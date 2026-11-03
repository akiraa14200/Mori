package com.rykocollection.app.data

import com.rykocollection.app.model.CatalogueTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.selects.select
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
        try {
            require(limit in 1..100) { "Page size must be between 1 and 100." }
            require(offset >= 0) { "Page offset cannot be negative." }

            withTimeout(15_000) {
                coroutineScope {
                    // Race the public D1 cache and Supabase. Return the first non-empty
                    // successful page rather than waiting for a slow secondary source.
                    val worker = async { capture { fetchFromWorker(query, limit, offset) } }
                    val database = async { capture { fetchFromSupabase(query, limit, offset) } }
                    try {
                        val first = select<Pair<Boolean, Result<List<CatalogueTitle>>>> {
                            worker.onAwait { true to it }
                            database.onAwait { false to it }
                        }
                        first.second.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@coroutineScope it }

                        val second = if (first.first) database.await() else worker.await()
                        second.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@coroutineScope it }

                        when {
                            first.second.isFailure && second.isFailure -> throw IllegalStateException(
                                "Both catalogue sources failed. D1: ${first.second.exceptionOrNull()?.message}; " +
                                    "Supabase: ${second.exceptionOrNull()?.message}",
                                first.second.exceptionOrNull()
                            )
                            first.second.isFailure -> throw IllegalStateException(
                                "Catalogue source failed: ${first.second.exceptionOrNull()?.message}"
                            )
                            second.isFailure -> throw IllegalStateException(
                                "Catalogue fallback failed: ${second.exceptionOrNull()?.message}"
                            )
                            else -> emptyList()
                        }
                    } finally {
                        worker.cancel()
                        database.cancel()
                    }
                }
            }.let { Result.success(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }

    private suspend fun capture(block: () -> List<CatalogueTitle>): Result<List<CatalogueTitle>> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Result.failure(failure)
        }

    private fun fetchFromWorker(query: String, limit: Int, offset: Int): List<CatalogueTitle> {
        val filters = if (query.isBlank()) "" else "&q=${encode(query.trim().take(100))}"
        val endpoint = "$CATALOGUE_WORKER_URL/api/titles?limit=$limit&offset=$offset$filters"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 7000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            val code = connection.responseCode
            val body = readResponse(connection, code)
            check(code in 200..299) { "D1 returned HTTP $code: ${body.take(220)}" }
            val json = JSONObject(body)
            val rows = json.optJSONArray("data")
                ?: json.optJSONArray("results")
                ?: json.optJSONArray("items")
                ?: throw IllegalStateException("D1 response did not contain a data/results/items array.")
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
            connectTimeout = 5000
            readTimeout = 7000
            setRequestProperty("apikey", CATALOGUE_SUPABASE_KEY)
            // A publishable key is an API key, not a JWT. Do not send it as Bearer auth.
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val body = readResponse(connection, code)
            check(code in 200..299) { "Supabase returned HTTP $code: ${body.take(220)}" }
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
