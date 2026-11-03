package com.rykocollection.app.data

import com.rykocollection.app.model.RelatedVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

private const val ANILIST_VIDEO_ENDPOINT = "https://graphql.anilist.co"

/** Builds the trailer feed from AniList metadata, as the comic web app does. */
class VideoRepository {
    private val cache = ConcurrentHashMap<Int, Pair<Long, List<RelatedVideo>>>()

    suspend fun getVideos(limit: Int = 20, offset: Int = 0): Result<List<RelatedVideo>> =
        withContext(Dispatchers.IO) {
            try {
                require(limit in 1..100) { "Page size must be between 1 and 100." }
                require(offset >= 0) { "Page offset cannot be negative." }
                val page = (offset / limit) + 1
                val cached = cache[page]
                val all = if (cached != null && cached.first > System.currentTimeMillis()) {
                    cached.second
                } else {
                    val trending = requestTrailers("TRENDING_DESC", page)
                    val popular = requestTrailers("POPULARITY_DESC", page)
                    val calendar = Calendar.getInstance()
                    val season = when (calendar.get(Calendar.MONTH)) {
                        in 0..2 -> "WINTER"
                        in 3..5 -> "SPRING"
                        in 6..8 -> "SUMMER"
                        else -> "FALL"
                    }
                    val seasonal = requestSeasonalTrailers(season, calendar.get(Calendar.YEAR), page)
                    (trending + popular + seasonal).distinctBy { it.id }
                        .filter { it.videoUrl.isNotBlank() }
                        .also { cache[page] = (System.currentTimeMillis() + 300_000L) to it }
                }
                Result.success(all.drop(offset % limit).take(limit))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }

    private fun requestTrailers(sort: String, page: Int): List<RelatedVideo> {
        val query = """
            query TrailerFeed(${'$'}page: Int!, ${'$'}sort: [MediaSort]!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(type: ANIME, sort: ${'$'}sort, isAdult: false) {
                  id title { english romaji } trailer { id site }
                }
              }
            }
        """.trimIndent()
        return execute(query, JSONObject().put("page", page).put("sort", JSONArray().put(sort)))
    }

    private fun requestSeasonalTrailers(season: String, year: Int, page: Int): List<RelatedVideo> {
        val query = """
            query SeasonalTrailerFeed(${'$'}page: Int!, ${'$'}season: MediaSeason!, ${'$'}year: Int!) {
              Page(page: ${'$'}page, perPage: 30) {
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, sort: POPULARITY_DESC, isAdult: false) {
                  id title { english romaji } trailer { id site }
                }
              }
            }
        """.trimIndent()
        return execute(query, JSONObject().put("page", page).put("season", season).put("year", year))
    }

    private fun execute(query: String, variables: JSONObject): List<RelatedVideo> {
        val connection = (URL(ANILIST_VIDEO_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RykoCollection-Android/1.6")
        }
        try {
            val body = JSONObject().put("query", query).put("variables", variables)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "AniList trailer request failed (HTTP $code): ${responseBody.take(160)}" }
            val json = JSONObject(responseBody)
            val errors = json.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val message = (0 until errors.length()).mapNotNull {
                    errors.optJSONObject(it)?.optString("message")?.takeIf(String::isNotBlank)
                }.joinToString(" · ")
                throw IllegalStateException(message.ifBlank { "AniList rejected the trailer query." })
            }
            val rows = json.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
            return parseTrailers(rows)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTrailers(rows: JSONArray): List<RelatedVideo> = buildList {
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val trailer = item.optJSONObject("trailer") ?: continue
            val trailerId = trailer.optString("id")
            if (!trailer.optString("site").equals("youtube", ignoreCase = true)) continue
            if (!trailerId.matches(Regex("^[A-Za-z0-9_-]{11}$"))) continue
            val title = item.optJSONObject("title")
            val animeTitle = title?.optString("english").orEmpty()
                .ifBlank { title?.optString("romaji").orEmpty() }
                .ifBlank { "Anime trailer" }
            val url = "https://www.youtube.com/watch?v=$trailerId"
            add(RelatedVideo(
                id = item.optInt("id").toString(),
                titleId = item.optInt("id").toString(),
                videoUrl = url,
                platform = "youtube",
                videoTitle = "$animeTitle — Trailer",
                creatorName = "AniList · YouTube",
                creatorAvatarUrl = "",
                actionType = "watch",
                actionUrl = url,
                sortOrder = index
            ))
        }
    }
}
