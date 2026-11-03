package com.rykocollection.app.data

import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val CATALOGUE_SUPABASE_URL = "https://moicrqcddmftjgwjqkvz.supabase.co"
private const val CATALOGUE_SUPABASE_KEY = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"
private const val CATALOGUE_WORKER_URL = "https://mori-data.akiraa142007.workers.dev"

class CatalogueRepository {
    suspend fun getTitles(
        query: String = "",
        limit: Int = 30,
        offset: Int = 0
    ): Result<List<CatalogueTitle>> = withContext(Dispatchers.IO) {
        runCatching {
            val primary = runCatching { fetchFromSupabase(query, limit, offset) }
            primary.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { fetchFromWorker(query, limit, offset) }.getOrElse { workerError ->
                    primary.exceptionOrNull()?.let { supabaseError ->
                        throw IllegalStateException(
                            "Catalogue unavailable. Supabase: ${supabaseError.message}; catalogue cache: ${workerError.message}",
                            workerError
                        )
                    }
                    throw workerError
                }
        }
    }

    private fun fetchFromSupabase(query: String, limit: Int, offset: Int): List<CatalogueTitle> {
        val columns = "id,name,media_type,synopsis,cover_url,banner_url,status,release_year,rating,genres,is_trending,is_featured"
        val filters = if (query.isBlank()) "" else {
            val safeQuery = query.trim().take(80).replace("*", "").replace(",", " ")
            "&or=" + encode("(name.ilike.*$safeQuery*,synopsis.ilike.*$safeQuery*)")
        }
        val endpoint = "$CATALOGUE_SUPABASE_URL/rest/v1/titles" +
            "?select=$columns&order=created_at.desc,id.asc&limit=$limit&offset=$offset$filters"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("apikey", CATALOGUE_SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $CATALOGUE_SUPABASE_KEY")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(code in 200..299) { "Supabase catalogue request failed ($code): ${body.take(180)}" }
            parseRows(JSONArray(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFromWorker(query: String, limit: Int, offset: Int): List<CatalogueTitle> {
        val filters = if (query.isBlank()) "" else "&q=${encode(query.trim().take(80))}"
        val endpoint = "$CATALOGUE_WORKER_URL/api/titles?limit=$limit&offset=$offset$filters"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(code in 200..299) { "Catalogue cache request failed ($code): ${body.take(180)}" }
            val rows = JSONObject(body).optJSONArray("data")
                ?: throw IllegalStateException("Catalogue cache response is missing data")
            parseRows(rows)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRows(rows: JSONArray): List<CatalogueTitle> = buildList {
        for (index in 0 until rows.length()) {
            val item = rows.getJSONObject(index)
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

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
