package com.rykocollection.app.data

import com.rykocollection.app.model.RelatedVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL = "https://moicrqcddmftjgwjqkvz.supabase.co"
private const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"

class VideoRepository {
    suspend fun getVideos(limit: Int = 20, offset: Int = 0): Result<List<RelatedVideo>> =
        withContext(Dispatchers.IO) {
            try {
                require(limit in 1..100) { "Page size must be between 1 and 100." }
                require(offset >= 0) { "Page offset cannot be negative." }
                val endpoint = "$SUPABASE_URL/rest/v1/related_videos" +
                    "?select=id,title_id,video_url,platform,video_title,creator_name,creator_avatar_url,action_type,action_url,sort_order,title:titles(name)" +
                    "&order=sort_order.asc,created_at.desc&limit=$limit&offset=$offset"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 10000
                    setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
                    // Publishable keys must not be sent as JWT Bearer tokens.
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    check(responseCode in 200..299) {
                        "Videos request failed (HTTP $responseCode): ${body.take(180)}"
                    }
                    val rows = JSONArray(body)
                    Result.success(buildList {
                        for (index in 0 until rows.length()) {
                            val item = rows.optJSONObject(index) ?: continue
                            val platform = item.optString("platform").ifBlank { "video" }
                            val relatedTitle = item.optJSONObject("title")?.optString("name").orEmpty()
                            val videoTitle = item.optString("video_title")
                                .ifBlank { relatedTitle }
                                .ifBlank { if (platform.equals("youtube", true)) "YouTube video" else "${platform.replaceFirstChar(Char::uppercase)} video" }
                            val videoUrl = item.optString("video_url")
                            val actionUrl = item.optString("action_url").ifBlank { videoUrl }
                            if (videoUrl.isBlank() && actionUrl.isBlank()) continue
                            add(
                                RelatedVideo(
                                    id = item.optString("id"),
                                    titleId = item.optString("title_id"),
                                    videoUrl = videoUrl.ifBlank { actionUrl },
                                    platform = platform,
                                    videoTitle = videoTitle,
                                    creatorName = item.optString("creator_name"),
                                    creatorAvatarUrl = item.optString("creator_avatar_url"),
                                    actionType = item.optString("action_type").ifBlank { "watch" },
                                    actionUrl = actionUrl,
                                    sortOrder = item.optInt("sort_order")
                                )
                            )
                        }
                    })
                } finally {
                    connection.disconnect()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }
}
