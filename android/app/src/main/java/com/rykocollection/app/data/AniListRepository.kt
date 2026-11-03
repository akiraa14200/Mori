package com.rykocollection.app.data

import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * AniList GraphQL client modelled on the working Kairo/comic implementation:
 * direct POST requests, public non-adult catalogue, short in-memory cache,
 * bounded timeouts and retries for transient failures.
 */
class AniListRepository {
    private data class CachedPage(val expiresAt: Long, val titles: List<CatalogueTitle>)
    private val cache = ConcurrentHashMap<String, CachedPage>()

    suspend fun getTitles(query: String, limit: Int, offset: Int): List<CatalogueTitle> {
        require(limit in 1..50) { "Page size must be between 1 and 50." }
        require(offset >= 0) { "Page offset cannot be negative." }

        val page = offset / limit + 1
        val term = query.trim().take(80)
        val key = "$term|$limit|$page"
        cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.titles }

        val escapedTerm = JSONObject.quote(term)
        val searchArgument = if (term.isBlank()) "" else "search: $escapedTerm,"
        val sort = if (term.isBlank()) "POPULARITY_DESC" else "SEARCH_MATCH"
        val graphQl = """
            query($page: Int!, $perPage: Int!) {
              Page(page: $page, perPage: $perPage) {
                anime: media(type: ANIME, $searchArgument sort: $sort, isAdult: false) {
                  id title { romaji english native }
                  description(asHtml: false)
                  coverImage { extraLarge large } bannerImage
                  status seasonYear format genres averageScore popularity
                }
                manga: media(type: MANGA, $searchArgument sort: $sort, isAdult: false) {
                  id title { romaji english native }
                  description(asHtml: false)
                  coverImage { extraLarge large } bannerImage
                  status startDate { year } format genres averageScore popularity
                }
              }
            }
        """.trimIndent()

        var lastError = "AniList catalogue is temporarily unavailable."
        for (attempt in 0..2) {
            try {
                val body = JSONObject()
                    .put("query", graphQl)
                    .put("variables", JSONObject().put("page", page).put("perPage", limit))
                val response = postGraphQl(body.toString())
                val errors = response.optJSONArray("errors")
                if (errors != null && errors.length() > 0 && !response.has("data")) {
                    lastError = errors.optJSONObject(0)?.optString("message")
                        ?.takeIf(String::isNotBlank) ?: "AniList returned a GraphQL error."
                    if (attempt < 2 && (lastError.contains("rate", true) ||
                                lastError.contains("tempor", true) ||
                                lastError.contains("timeout", true))) {
                        delay(400L * (attempt + 1))
                        continue
                    }
                    throw IllegalStateException(lastError)
                }

                val pageData = response.optJSONObject("data")?.optJSONObject("Page")
                    ?: throw IllegalStateException(
                        errors?.optJSONObject(0)?.optString("message")
                            ?.takeIf(String::isNotBlank) ?: "AniList returned no catalogue data."
                    )
                val combined = mutableListOf<Pair<Int, CatalogueTitle>>()
                parseMedia(pageData.optJSONArray("anime"), "Anime", combined)
                parseMedia(pageData.optJSONArray("manga"), "Manga", combined)
                val result = combined
                    .sortedByDescending { it.first }
                    .map { it.second }
                    .distinctBy { it.id }
                    .take(limit * 2)

                cache[key] = CachedPage(System.currentTimeMillis() + 5 * 60 * 1000L, result)
                return result
            } catch (failure: Exception) {
                lastError = failure.message?.takeIf(String::isNotBlank)
                    ?: "Could not connect to AniList."
                if (attempt < 2 && (failure is java.io.IOException ||
                            lastError.contains("tempor", true) ||
                            lastError.contains("timeout", true) ||
                            lastError.contains("429"))) {
                    delay(450L * (attempt + 1))
                    continue
                }
                throw IllegalStateException(lastError, failure)
            }
        }
        throw IllegalStateException(lastError)
    }

    private fun postGraphQl(json: String): JSONObject {
        val connection = (URL("https://graphql.anilist.co").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RykoCollection-Android/1.6")
        }
        return try {
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(raw).optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                }.getOrNull().orEmpty()
                throw java.io.IOException(
                    if (message.isNotBlank()) "AniList HTTP $code: $message"
                    else if (code == 429) "AniList rate limit reached. Please retry shortly."
                    else "AniList request failed (HTTP $code)."
                )
            }
            JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMedia(
        rows: org.json.JSONArray?,
        type: String,
        output: MutableList<Pair<Int, CatalogueTitle>>
    ) {
        if (rows == null) return
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val id = item.optInt("id")
            val titleObject = item.optJSONObject("title")
            val name = titleObject?.optString("english")?.takeIf(String::isNotBlank)
                ?: titleObject?.optString("romaji")?.takeIf(String::isNotBlank)
                ?: titleObject?.optString("native")?.takeIf(String::isNotBlank)
                ?: continue
            val cover = item.optJSONObject("coverImage")
            val genresJson = item.optJSONArray("genres")
            val genres = buildList {
                if (genresJson != null) for (i in 0 until genresJson.length()) {
                    genresJson.optString(i).takeIf(String::isNotBlank)?.let(::add)
                }
            }
            val startDate = item.optJSONObject("startDate")
            val year = item.optInt("seasonYear").takeIf { it > 0 }
                ?: startDate?.optInt("year")?.takeIf { it > 0 }
            val synopsis = item.optString("description")
                .replace(Regex("<[^>]*>"), " ")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace(Regex("\\s+"), " ").trim()
            val popularity = item.optInt("popularity")
            val score = item.optInt("averageScore").takeIf { it > 0 }?.div(10.0)
            val status = item.optString("status").replace('_', ' ')
                .lowercase().replaceFirstChar { it.uppercase() }
            output += popularity to CatalogueTitle(
                id = "anilist-$id",
                name = name,
                mediaType = type,
                synopsis = synopsis,
                coverUrl = cover?.optString("extraLarge")?.takeIf(String::isNotBlank)
                    ?: cover?.optString("large").orEmpty(),
                bannerUrl = item.optString("bannerImage"),
                status = status.ifBlank { "Unknown" },
                year = year,
                rating = score,
                genres = genres,
                trending = popularity >= 100_000,
                featured = false
            )
        }
    }
}
