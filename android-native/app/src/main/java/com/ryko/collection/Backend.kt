package com.ryko.collection

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RykoRelatedVideo(
    val id: String, val titleId: String, val videoUrl: String, val platform: String,
    val videoTitle: String, val creatorName: String, val creatorAvatarUrl: String,
    val actionType: String, val actionUrl: String, val sortOrder: Int
)

data class RykoTitle(
    val id: String, val name: String, val type: String, val synopsis: String,
    val cover: String, val genres: List<String>, val status: String,
    val year: Int?, val rating: String?, val popularity: Int,
    val alternativeNames: List<String> = emptyList(), val themes: List<String> = emptyList(),
    val chapters: String = "", val episodes: String = "", val author: String = "",
    val artist: String = "", val studio: String = "", val country: String = "",
    val trailerUrl: String = "", val banner: String = "", val season: String = "",
    val sourceUrl: String = "", val featured: Boolean = false, val trending: Boolean = false, val anilistId: Int? = null
)
data class RykoCharacter(val id: String, val titleId: String, val name: String, val role: String, val description: String, val image: String)

class Backend(context: Context) {
    companion object {
        private const val BASE = "https://moicrqcddmftjgwjqkvz.supabase.co"
        private const val KEY = "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val prefs = EncryptedSharedPreferences.create(
        context, "ryko_secure_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    val userId: String? get() = prefs.getString("user_id", null)
    val email: String? get() = prefs.getString("email", null)
    val signedIn: Boolean get() = !prefs.getString("access_token", null).isNullOrBlank()
    private val token: String? get() = prefs.getString("access_token", null)

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null,
                                auth: Boolean = true, extraHeaders: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url("$BASE$path")
                .addHeader("apikey", KEY).addHeader("Accept", "application/json")
            val bearer = if (auth) token else null
            builder.addHeader("Authorization", "Bearer ${bearer ?: KEY}")
            extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
            when (method) {
                "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(JSON))
                "PATCH" -> builder.patch((body ?: JSONObject()).toString().toRequestBody(JSON))
                "DELETE" -> builder.delete()
                else -> builder.get()
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("Server ${response.code}: $text".take(320))
                text
            }
        }

    suspend fun loadTitles(search: String = ""): List<RykoTitle> {
        val filter = if (search.isBlank()) "" else "&name=ilike.*${enc(search)}*"
        val raw = request("/rest/v1/titles?select=id,name,anilist_id,alternative_names,media_type,synopsis,cover_url,banner_url,genres,themes,status,release_year,season,rating,popularity,chapters,episodes,author,artist,studio,country,trailer_url,source_url,is_featured,is_trending&order=popularity.desc&limit=60$filter", auth = false)
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getJSONObject(it).toTitle() }
    }

    suspend fun loadRelatedVideos(): List<RykoRelatedVideo> {
        val raw = request("/rest/v1/related_videos?select=id,title_id,video_url,platform,video_title,creator_name,creator_avatar_url,action_type,action_url,sort_order&order=sort_order.asc,created_at.desc&limit=300", auth = false)
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val v = arr.getJSONObject(i)
            RykoRelatedVideo(
                id = v.optString("id"), titleId = v.optString("title_id"),
                videoUrl = v.optString("video_url"), platform = v.optString("platform"),
                videoTitle = v.optString("video_title"), creatorName = v.optString("creator_name"),
                creatorAvatarUrl = v.optString("creator_avatar_url"), actionType = v.optString("action_type"),
                actionUrl = v.optString("action_url"), sortOrder = v.optInt("sort_order")
            )
        }
    }

    suspend fun loadAniListTrailer(anilistId: Int): String? = withContext(Dispatchers.IO) {
        val query = """query (${'
        val payload = JSONObject().put("query", query).put("variables", JSONObject().put("id", anilistId))
        val request = Request.Builder().url("https://graphql.anilist.co")
            .post(payload.toString().toRequestBody(JSON))
            .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val trailer = JSONObject(response.body?.string().orEmpty()).optJSONObject("data")
                ?.optJSONObject("Media")?.optJSONObject("trailer") ?: return@withContext null
            if (!trailer.optString("site").equals("youtube", true)) return@withContext null
            val id = trailer.optString("id")
            if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return@withContext null
            "https://www.youtube.com/watch?v=$id"
        }
    }

    suspend fun loadCharacters(titleId: String): List<RykoCharacter> {
        val raw = request("/rest/v1/characters?select=id,title_id,name,role,description,image_url&title_id=eq.$titleId&order=name.asc", auth = false)
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { c ->
            RykoCharacter(c.optString("id"), c.optString("title_id"), c.optString("name"),
                c.optString("role"), c.optString("description"), c.optString("image_url"))
        } }
    }

    suspend fun signIn(mail: String, password: String) {
        val result = JSONObject(request("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", mail.trim()).put("password", password), auth = false))
        saveSession(result, mail)
    }
    suspend fun signUp(mail: String, password: String) {
        val result = JSONObject(request("/auth/v1/signup", "POST",
            JSONObject().put("email", mail.trim()).put("password", password), auth = false))
        if (result.has("access_token")) saveSession(result, mail)
    }
    private fun saveSession(result: JSONObject, mail: String) {
        val user = result.optJSONObject("user") ?: JSONObject()
        prefs.edit().putString("access_token", result.optString("access_token"))
            .putString("refresh_token", result.optString("refresh_token"))
            .putString("user_id", user.optString("id"))
            .putString("email", user.optString("email", mail)).apply()
    }
    fun signOut() { prefs.edit().clear().apply() }

    suspend fun loadLibrary(): List<RykoTitle> {
        val uid = userId ?: return emptyList()
        val raw = request("/rest/v1/user_library?select=title_id&user_id=eq.$uid&limit=300")
        val ids = JSONArray(raw)
        if (ids.length() == 0) return emptyList()
        val joined = (0 until ids.length()).map { ids.getJSONObject(it).getString("title_id") }
            .joinToString(",") { "\"$it\"" }
        val titles = request("/rest/v1/titles?select=id,name,anilist_id,alternative_names,media_type,synopsis,cover_url,banner_url,genres,themes,status,release_year,season,rating,popularity,chapters,episodes,author,artist,studio,country,trailer_url,source_url,is_featured,is_trending&id=in.${enc("($joined)")}&order=popularity.desc")
        val arr = JSONArray(titles)
        return (0 until arr.length()).map { arr.getJSONObject(it).toTitle() }
    }
    suspend fun saveToLibrary(titleId: String) {
        val uid = userId ?: error("Sign in to save titles")
        request("/rest/v1/user_library", "POST", JSONObject().put("user_id", uid).put("title_id", titleId),
            extraHeaders = mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"))
    }
    suspend fun removeFromLibrary(titleId: String) {
        val uid = userId ?: error("Sign in to edit your library")
        request("/rest/v1/user_library?user_id=eq.$uid&title_id=eq.$titleId", "DELETE")
    }
    suspend fun profile(): JSONObject {
        val uid = userId ?: error("Sign in first")
        val result = JSONArray(request("/rest/v1/profiles?select=username,display_name,avatar_url,bio&id=eq.$uid&limit=1"))
        return if (result.length() > 0) result.getJSONObject(0) else JSONObject()
    }
    suspend fun comments(titleId: String): JSONArray =
        JSONArray(request("/rest/v1/community_comments?select=id,user_id,body,created_at&title_id=eq.$titleId&order=created_at.desc&limit=30", auth = false))
    suspend fun postComment(titleId: String, body: String) {
        val uid = userId ?: error("Sign in to comment")
        request("/rest/v1/community_comments", "POST",
            JSONObject().put("title_id", titleId).put("user_id", uid).put("body", body.trim()))
    }
    suspend fun isAdmin(): Boolean {
        val uid = userId ?: return false
        return JSONArray(request("/rest/v1/admin_users?select=user_id&user_id=eq.$uid&limit=1")).length() > 0
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun JSONObject.toTitle() = RykoTitle(
        id = optString("id"), name = optString("name"), type = optString("media_type"),
        synopsis = optString("synopsis"), cover = optString("cover_url"),
        genres = optJSONArray("genres").toStringList(), status = optString("status"),
        year = if (isNull("release_year")) null else optInt("release_year"),
        rating = if (isNull("rating")) null else optString("rating"), popularity = optInt("popularity"),
        alternativeNames = optJSONArray("alternative_names").toStringList(), themes = optJSONArray("themes").toStringList(),
        chapters = optString("chapters"), episodes = optString("episodes"), author = optString("author"),
        artist = optString("artist"), studio = optString("studio"), country = optString("country"),
        trailerUrl = optString("trailer_url"), banner = optString("banner_url"), season = optString("season"),
        sourceUrl = optString("source_url"), featured = optBoolean("is_featured"), trending = optBoolean("is_trending"), anilistId = if (isNull("anilist_id")) null else optInt("anilist_id")
    )
    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    }
}
}id:Int){Media(id:${'
        val payload = JSONObject().put("query", query).put("variables", JSONObject().put("id", anilistId))
        val request = Request.Builder().url("https://graphql.anilist.co")
            .post(payload.toString().toRequestBody(JSON))
            .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val trailer = JSONObject(response.body?.string().orEmpty()).optJSONObject("data")
                ?.optJSONObject("Media")?.optJSONObject("trailer") ?: return@withContext null
            if (!trailer.optString("site").equals("youtube", true)) return@withContext null
            val id = trailer.optString("id")
            if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return@withContext null
            "https://www.youtube.com/watch?v=$id"
        }
    }

    suspend fun loadCharacters(titleId: String): List<RykoCharacter> {
        val raw = request("/rest/v1/characters?select=id,title_id,name,role,description,image_url&title_id=eq.$titleId&order=name.asc", auth = false)
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { c ->
            RykoCharacter(c.optString("id"), c.optString("title_id"), c.optString("name"),
                c.optString("role"), c.optString("description"), c.optString("image_url"))
        } }
    }

    suspend fun signIn(mail: String, password: String) {
        val result = JSONObject(request("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", mail.trim()).put("password", password), auth = false))
        saveSession(result, mail)
    }
    suspend fun signUp(mail: String, password: String) {
        val result = JSONObject(request("/auth/v1/signup", "POST",
            JSONObject().put("email", mail.trim()).put("password", password), auth = false))
        if (result.has("access_token")) saveSession(result, mail)
    }
    private fun saveSession(result: JSONObject, mail: String) {
        val user = result.optJSONObject("user") ?: JSONObject()
        prefs.edit().putString("access_token", result.optString("access_token"))
            .putString("refresh_token", result.optString("refresh_token"))
            .putString("user_id", user.optString("id"))
            .putString("email", user.optString("email", mail)).apply()
    }
    fun signOut() { prefs.edit().clear().apply() }

    suspend fun loadLibrary(): List<RykoTitle> {
        val uid = userId ?: return emptyList()
        val raw = request("/rest/v1/user_library?select=title_id&user_id=eq.$uid&limit=300")
        val ids = JSONArray(raw)
        if (ids.length() == 0) return emptyList()
        val joined = (0 until ids.length()).map { ids.getJSONObject(it).getString("title_id") }
            .joinToString(",") { "\"$it\"" }
        val titles = request("/rest/v1/titles?select=id,name,anilist_id,alternative_names,media_type,synopsis,cover_url,banner_url,genres,themes,status,release_year,season,rating,popularity,chapters,episodes,author,artist,studio,country,trailer_url,source_url,is_featured,is_trending&id=in.${enc("($joined)")}&order=popularity.desc")
        val arr = JSONArray(titles)
        return (0 until arr.length()).map { arr.getJSONObject(it).toTitle() }
    }
    suspend fun saveToLibrary(titleId: String) {
        val uid = userId ?: error("Sign in to save titles")
        request("/rest/v1/user_library", "POST", JSONObject().put("user_id", uid).put("title_id", titleId),
            extraHeaders = mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"))
    }
    suspend fun removeFromLibrary(titleId: String) {
        val uid = userId ?: error("Sign in to edit your library")
        request("/rest/v1/user_library?user_id=eq.$uid&title_id=eq.$titleId", "DELETE")
    }
    suspend fun profile(): JSONObject {
        val uid = userId ?: error("Sign in first")
        val result = JSONArray(request("/rest/v1/profiles?select=username,display_name,avatar_url,bio&id=eq.$uid&limit=1"))
        return if (result.length() > 0) result.getJSONObject(0) else JSONObject()
    }
    suspend fun comments(titleId: String): JSONArray =
        JSONArray(request("/rest/v1/community_comments?select=id,user_id,body,created_at&title_id=eq.$titleId&order=created_at.desc&limit=30", auth = false))
    suspend fun postComment(titleId: String, body: String) {
        val uid = userId ?: error("Sign in to comment")
        request("/rest/v1/community_comments", "POST",
            JSONObject().put("title_id", titleId).put("user_id", uid).put("body", body.trim()))
    }
    suspend fun isAdmin(): Boolean {
        val uid = userId ?: return false
        return JSONArray(request("/rest/v1/admin_users?select=user_id&user_id=eq.$uid&limit=1")).length() > 0
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun JSONObject.toTitle() = RykoTitle(
        id = optString("id"), name = optString("name"), type = optString("media_type"),
        synopsis = optString("synopsis"), cover = optString("cover_url"),
        genres = optJSONArray("genres").toStringList(), status = optString("status"),
        year = if (isNull("release_year")) null else optInt("release_year"),
        rating = if (isNull("rating")) null else optString("rating"), popularity = optInt("popularity"),
        alternativeNames = optJSONArray("alternative_names").toStringList(), themes = optJSONArray("themes").toStringList(),
        chapters = optString("chapters"), episodes = optString("episodes"), author = optString("author"),
        artist = optString("artist"), studio = optString("studio"), country = optString("country"),
        trailerUrl = optString("trailer_url"), banner = optString("banner_url"), season = optString("season"),
        sourceUrl = optString("source_url"), featured = optBoolean("is_featured"), trending = optBoolean("is_trending"), anilistId = if (isNull("anilist_id")) null else optInt("anilist_id")
    )
    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    }
}
}id,type:ANIME){trailer{id site}}}"""
        val payload = JSONObject().put("query", query).put("variables", JSONObject().put("id", anilistId))
        val request = Request.Builder().url("https://graphql.anilist.co")
            .post(payload.toString().toRequestBody(JSON))
            .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val trailer = JSONObject(response.body?.string().orEmpty()).optJSONObject("data")
                ?.optJSONObject("Media")?.optJSONObject("trailer") ?: return@withContext null
            if (!trailer.optString("site").equals("youtube", true)) return@withContext null
            val id = trailer.optString("id")
            if (!id.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return@withContext null
            "https://www.youtube.com/watch?v=$id"
        }
    }

    suspend fun loadCharacters(titleId: String): List<RykoCharacter> {
        val raw = request("/rest/v1/characters?select=id,title_id,name,role,description,image_url&title_id=eq.$titleId&order=name.asc", auth = false)
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { c ->
            RykoCharacter(c.optString("id"), c.optString("title_id"), c.optString("name"),
                c.optString("role"), c.optString("description"), c.optString("image_url"))
        } }
    }

    suspend fun signIn(mail: String, password: String) {
        val result = JSONObject(request("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", mail.trim()).put("password", password), auth = false))
        saveSession(result, mail)
    }
    suspend fun signUp(mail: String, password: String) {
        val result = JSONObject(request("/auth/v1/signup", "POST",
            JSONObject().put("email", mail.trim()).put("password", password), auth = false))
        if (result.has("access_token")) saveSession(result, mail)
    }
    private fun saveSession(result: JSONObject, mail: String) {
        val user = result.optJSONObject("user") ?: JSONObject()
        prefs.edit().putString("access_token", result.optString("access_token"))
            .putString("refresh_token", result.optString("refresh_token"))
            .putString("user_id", user.optString("id"))
            .putString("email", user.optString("email", mail)).apply()
    }
    fun signOut() { prefs.edit().clear().apply() }

    suspend fun loadLibrary(): List<RykoTitle> {
        val uid = userId ?: return emptyList()
        val raw = request("/rest/v1/user_library?select=title_id&user_id=eq.$uid&limit=300")
        val ids = JSONArray(raw)
        if (ids.length() == 0) return emptyList()
        val joined = (0 until ids.length()).map { ids.getJSONObject(it).getString("title_id") }
            .joinToString(",") { "\"$it\"" }
        val titles = request("/rest/v1/titles?select=id,name,anilist_id,alternative_names,media_type,synopsis,cover_url,banner_url,genres,themes,status,release_year,season,rating,popularity,chapters,episodes,author,artist,studio,country,trailer_url,source_url,is_featured,is_trending&id=in.${enc("($joined)")}&order=popularity.desc")
        val arr = JSONArray(titles)
        return (0 until arr.length()).map { arr.getJSONObject(it).toTitle() }
    }
    suspend fun saveToLibrary(titleId: String) {
        val uid = userId ?: error("Sign in to save titles")
        request("/rest/v1/user_library", "POST", JSONObject().put("user_id", uid).put("title_id", titleId),
            extraHeaders = mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal"))
    }
    suspend fun removeFromLibrary(titleId: String) {
        val uid = userId ?: error("Sign in to edit your library")
        request("/rest/v1/user_library?user_id=eq.$uid&title_id=eq.$titleId", "DELETE")
    }
    suspend fun profile(): JSONObject {
        val uid = userId ?: error("Sign in first")
        val result = JSONArray(request("/rest/v1/profiles?select=username,display_name,avatar_url,bio&id=eq.$uid&limit=1"))
        return if (result.length() > 0) result.getJSONObject(0) else JSONObject()
    }
    suspend fun comments(titleId: String): JSONArray =
        JSONArray(request("/rest/v1/community_comments?select=id,user_id,body,created_at&title_id=eq.$titleId&order=created_at.desc&limit=30", auth = false))
    suspend fun postComment(titleId: String, body: String) {
        val uid = userId ?: error("Sign in to comment")
        request("/rest/v1/community_comments", "POST",
            JSONObject().put("title_id", titleId).put("user_id", uid).put("body", body.trim()))
    }
    suspend fun isAdmin(): Boolean {
        val uid = userId ?: return false
        return JSONArray(request("/rest/v1/admin_users?select=user_id&user_id=eq.$uid&limit=1")).length() > 0
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun JSONObject.toTitle() = RykoTitle(
        id = optString("id"), name = optString("name"), type = optString("media_type"),
        synopsis = optString("synopsis"), cover = optString("cover_url"),
        genres = optJSONArray("genres").toStringList(), status = optString("status"),
        year = if (isNull("release_year")) null else optInt("release_year"),
        rating = if (isNull("rating")) null else optString("rating"), popularity = optInt("popularity"),
        alternativeNames = optJSONArray("alternative_names").toStringList(), themes = optJSONArray("themes").toStringList(),
        chapters = optString("chapters"), episodes = optString("episodes"), author = optString("author"),
        artist = optString("artist"), studio = optString("studio"), country = optString("country"),
        trailerUrl = optString("trailer_url"), banner = optString("banner_url"), season = optString("season"),
        sourceUrl = optString("source_url"), featured = optBoolean("is_featured"), trending = optBoolean("is_trending"), anilistId = if (isNull("anilist_id")) null else optInt("anilist_id")
    )
    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    }
}
