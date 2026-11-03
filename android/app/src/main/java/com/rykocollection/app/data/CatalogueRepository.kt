package com.rykocollection.app.data

import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val CATALOGUE_WORKER_URL = "https://mori-data.akiraa142007.workers.dev"
private const val CATALOGUE_SUPABASE_URL = "https://moicrqcddmftjgwjqkvz.supabase.co"
private const val CATALOGUE_SUPABASE_KEY = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"

class CatalogueRepository {
    suspend fun getTitles(
        query: String = "",
        limit: Int = 30,
        offset: Int = 0
    ): Result<List<CatalogueTitle>> = withContext(Dispatchers.IO) {
        runCatching {
            require(limit in 1..100) { "Page size must be between 1 and 100." }
            require(offset >= 0) { "Page offset cannot be negative." }

            // Start both sources together. A slow/unavailable worker must not prevent
            // the database fallback from making progress.
            coroutineScope {
                val worker = async { runCatching { fetchFromWorker(query, limit, offset) } }
                val database = async { runCatching { fetchFromSupabase(query, limit, offset) } }
                val results = awaitAll(worker, database)
                val workerResult = results[0]
                val databaseResult = results[1]

                workerResult.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: databaseResult.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: when {
                        workerResult.isFailure && databaseResult.isFailure -> {
                            throw IllegalStateException(
                                "Catalogue sources failed. Cache: ${workerResult.exceptionOrNull()?.message}; " +
                                    "database: ${databaseResult.exceptionOrNull()?.message}",
                                workerResult.exceptionOrNull()
                            )
                        }
                        workerResult.isFailure -> {
                            throw IllegalStateException(
                                "Catalogue cache unavailable: ${workerResult.exceptionOrNull()?.message}"
                            )
                        }
                        databaseResult.isFailure -> {
                            throw IllegalStateException(
                                "Catalogue database unavailable: ${databaseResult.exceptionOrNull()?.message}"
                            )
                        }
                        else -> emptyList()
                    }
            }
        }
    }

    private fun fetchFromWorker(query: String, limit: Int, offset: Int): List<CatalogueTitle> {
        val filters = if (query.isBlank()) "" else "&q=${encode(query.trim().take(100))}"
        val endpoint = "$CATALOGUE_WORKER_URL/api/titles?limit=$limit&offset=$offset$filters"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4500
            readTimeout = 6500
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            val code = connection.responseCode
            val body = readResponse(connection, code)
            check(code in 200..299) { "Catalogue cache returned HTTP $code: ${body.take(180)}" }
            val json = JSONObject(body)
            val rows = json.optJSONArray("data")
                ?: json.optJSONArray("results")
                ?: json.optJSONArray("items")
                ?: throw IllegalStateException("Catalogue cache response did not contain a data array.")
            parseRows(rows)
        } finally {
            connection.disconnect()
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
            connectTimeout = 4500
            readTimeout = 6500
            setRequestProperty("apikey", CATALOGUE_SUPABASE_KEY)
            // Publishable keys belong in apikey; Authorization is reserved for a user JWT.
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val body = readResponse(connection, code)
            check(code in 200..299) { "Supabase returned HTTP $code: ${body.take(180)}" }
            parseRows(JSONArray(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun parseRows(rows: JSONArray): List<CatalogueTitle> = buildList {
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val genresValue = item.opt("genres")
            val genres = when (genresValue) {
                is JSONArray -> buildList {
                    for (i in 0 until genresValue.length()) {
                        genresValue.optString(i).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                is String -> runCatching { JSONArray(genresValue) }.getOrNull()?.let { values ->
                    buildList {
                        for (i in 0 until values.length()) {
                            values.optString(i).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                } ?: emptyList()
                else -> emptyList()
            }
            val id = item.optString("id")
            val name = item.optString("name").ifBlank { item.optString("title") }
            if (id.isBlank() || name.isBlank()) continue

            add(
                CatalogueTitle(
                    id = id,
                    name = name,
                    mediaType = item.optString("media_type").ifBlank { "Manga" },
                    synopsis = item.optString("synopsis"),
                    coverUrl = item.optString("cover_url"),
                    bannerUrl = item.optString("banner_url"),
                    status = item.optString("status").ifBlank { "Ongoing" },
                    year = item.optInt("release_year").takeIf { item.has("release_year") && !item.isNull("release_year") },
                    rating = item.optDouble("rating").takeIf { item.has("rating") && !item.isNull("rating") },
                    genres = genres,
                    trending = item.optBoolean("is_trending"),
                    featured = item.optBoolean("is_featured")
                )
            )
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
