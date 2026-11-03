package com.rykocollection.app.data

import com.rykocollection.app.model.RelatedVideo
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
            runCatching {
                val endpoint = "$SUPABASE_URL/rest/v1/related_videos" +
                    "?select=id,title_id,video_url,platform,video_title,creator_name,creator_avatar_url,action_type,action_url,sort_order" +
                    "&order=sort_order.asc,created_at.desc&limit=$limit&offset=$offset"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    check(responseCode in 200..299) { "Videos request failed ($responseCode)" }
                    val rows = JSONArray(body)
                    buildList {
                        for (index in 0 until rows.length()) {
                            val item = rows.getJSONObject(index)
                            add(
                                RelatedVideo(
                                    id = item.optString("id"),
                                    titleId = item.optString("title_id"),
                                    videoUrl = item.optString("video_url"),
                                    platform = item.optString("platform", "youtube"),
                                    videoTitle = item.optString("video_title").ifBlank { "Untitled video" },
                                    creatorName = item.optString("creator_name"),
                                    creatorAvatarUrl = item.optString("creator_avatar_url"),
                                    actionType = item.optString("action_type", "watch"),
                                    actionUrl = item.optString("action_url").ifBlank { item.optString("video_url") },
                                    sortOrder = item.optInt("sort_order")
                                )
                            )
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
}
