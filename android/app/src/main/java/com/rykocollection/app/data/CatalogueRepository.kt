package com.rykocollection.app.data

import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"
private const val PAGE_SIZE_PER_TYPE = 15

/** Direct AniList GraphQL catalogue, following the working web app in akiraa14200/comic. */
class CatalogueRepository {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<CatalogueTitle>>>()

    suspend fun getTitles(query: String = "", limit: Int = 30, offset: Int = 0): Result<List<CatalogueTitle>> =
        withContext(Dispatchers.IO) {
            try {
                require(limit in 1..100) { "Page size must be between 1 and 100." }
                require(offset >= 0) { "Page offset cannot be negative." }
                val term = query.trim().take(80)
                val page = (offset / limit) + 1
                val key = "$term|$page|$limit"
                cache[key]?.takeIf { it.first > System.currentTimeMillis() }?.let {
                    return@withContext Result.success(it.second)
                }
                val perType = (limit / 2).coerceAtLeast(1)
                val anime = requestMedia("ANIME", term, page, perType)
                val manga = requestMedia("MANGA", term, page, perType)
                val combined = (anime + manga).distinctBy { it.id }
                    .sortedWith(compareByDescending<CatalogueTitle> { it.trending }
                        .thenByDescending { it.rating ?: 0.0 }
                        .thenBy { it.name.lowercase() })
                    .take(limit)
                cache[key] = (System.currentTimeMillis() + 300_000L) to combined
                Result.success(combined)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }

    private fun requestMedia(type: String, search: String, page: Int, perPage: Int): List<CatalogueTitle> {
        val sort = if (search.isBlank()) "TRENDING_DESC" else "SEARCH_MATCH"
        val graphQl = """
            query CataloguePage($page: Int!, $perPage: Int!, $type: MediaType!, $sort: [MediaSort]!, $search: String) {
              Page(page: $page, perPage: $perPage) {
                media(type: $type, sort: $sort, search: $search, isAdult: false) {
                  id title { romaji english native } description(asHtml: false)
                  coverImage { extraLarge large } bannerImage status seasonYear
                  averageScore genres format isAdult
                }
              }
            }
        """.trimIndent()
        val payload = JSONObject()
            .put("query", graphQl)
            .put("variables", JSONObject()
                .put("page", page)
                .put("perPage", perPage)
                .put("type", type)
                .put("sort", JSONArray().put(sort))
                .put("search", search.takeIf { it.isNotBlank() } ?: JSONObject.NULL))

        var lastError = "AniList catalogue is temporarily unavailable."
        for (attempt in 0..2) {
            val connection = (URL(ANILIST_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RykoCollection-Android/1.6")
            }
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val bodyText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(bodyText) }.getOrNull()
                if (code in 200..299 && json != null) {
                    val errors = json.optJSONArray("errors")
                    if (errors != null && errors.length() > 0) {
                        lastError = (0 until errors.length()).mapNotNull {
                            errors.optJSONObject(it)?.optString("message")?.takeIf(String::isNotBlank)
                        }.joinToString(" · ").ifBlank { "AniList rejected the catalogue query." }
                        if (attempt < 2 && lastError.contains("rate", true)) {
                            Thread.sleep(400L * (attempt + 1))
                            continue
                        }
                        throw IllegalStateException(lastError)
                    }
                    val media = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")
                        ?: throw IllegalStateException("AniList returned no catalogue results.")
                    return parseMedia(media, type)
                }
                val apiMessage = json?.optJSONArray("errors")?.let { errors ->
                    (0 until errors.length()).mapNotNull { errors.optJSONObject(it)?.optString("message") }
                        .filter(String::isNotBlank).joinToString(" · ")
                }.orEmpty()
                lastError = when {
                    code == 429 -> "AniList is receiving too many requests. Please try again shortly."
                    apiMessage.isNotBlank() -> "AniList rejected the catalogue query: $apiMessage"
                    else -> "AniList catalogue request failed (HTTP $code)."
                }
                if (attempt < 2 && (code == 429 || code >= 500)) {
                    Thread.sleep(400L * (attempt + 1))
                } else {
                    throw IllegalStateException("$lastError ${bodyText.take(180)}".trim())
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException(lastError)
    }

    private fun parseMedia(rows: JSONArray, type: String): List<CatalogueTitle> = buildList {
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            if (item.optBoolean("isAdult")) continue
            val titles = item.optJSONObject("title")
            val name = titles?.optString("english").orEmpty()
                .ifBlank { titles?.optString("romaji").orEmpty() }
                .ifBlank { titles?.optString("native").orEmpty() }
            val id = item.optInt("id")
            if (id <= 0 || name.isBlank()) continue
            val cover = item.optJSONObject("coverImage")
            val genreRows = item.optJSONArray("genres")
            val genres = buildList {
                if (genreRows != null) for (i in 0 until genreRows.length()) {
                    genreRows.optString(i).takeIf(String::isNotBlank)?.let(::add)
                }
            }
            val score = item.optInt("averageScore").takeIf { it > 0 }?.div(10.0)
            val status = item.optString("status").takeIf(String::isNotBlank)
                ?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Unknown"
            add(CatalogueTitle(
                id = id.toString(),
                name = name,
                mediaType = item.optString("format").takeIf(String::isNotBlank)
                    ?.replace('_', ' ') ?: if (type == "ANIME") "Anime" else "Manga",
                synopsis = item.optString("description"),
                coverUrl = cover?.optString("extraLarge").orEmpty().ifBlank { cover?.optString("large").orEmpty() },
                bannerUrl = item.optString("bannerImage"),
                status = status,
                year = item.optInt("seasonYear").takeIf { it > 0 },
                rating = score,
                genres = genres,
                trending = index < 8,
                featured = index == 0
            ))
        }
    }
}
