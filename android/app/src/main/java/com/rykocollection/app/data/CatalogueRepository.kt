package com.rykocollection.app.data

import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CatalogueRepository {
    suspend fun getTitles(query: String = "", limit: Int = 30, offset: Int = 0): Result<List<CatalogueTitle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val endpoint = "https://mori-data.akiraa142007.workers.dev/api/titles?limit=$limit&offset=$offset" +
                    if (query.isBlank()) "" else "&q=$encodedQuery"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    check(responseCode in 200..299) { "Catalogue request failed ($responseCode)" }
                    val data = JSONObject(body).optJSONArray("data")
                        ?: throw IllegalStateException("Catalogue response is missing data")
                    buildList {
                        for (index in 0 until data.length()) {
                            val item = data.getJSONObject(index)
                            add(
                                CatalogueTitle(
                                    id = item.optString("id"),
                                    name = item.optString("name", "Untitled"),
                                    mediaType = item.optString("media_type", "Manga"),
                                    synopsis = item.optString("synopsis"),
                                    coverUrl = item.optString("cover_url"),
                                    bannerUrl = item.optString("banner_url"),
                                    status = item.optString("status", "Ongoing"),
                                    year = item.optInt("release_year").takeIf { item.has("release_year") && !item.isNull("release_year") },
                                    rating = item.optDouble("rating").takeIf { item.has("rating") && !item.isNull("rating") },
                                    genres = item.optJSONArray("genres")?.let { array ->
                                        buildList { for (i in 0 until array.length()) add(array.optString(i)) }
                                    } ?: emptyList(),
                                    trending = item.optBoolean("is_trending"),
                                    featured = item.optBoolean("is_featured")
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
