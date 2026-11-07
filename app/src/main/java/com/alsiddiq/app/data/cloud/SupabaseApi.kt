package com.alsiddiq.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String)

/** No network: the operation is kept and retried later. */
class OfflineException : IOException("لا يوجد اتصال بالإنترنت")
class ApiException(val code: Int, message: String) : Exception(message)

/** Minimal client for Supabase Auth, PostgREST and Edge Functions over HTTPS. Only the PUBLIC key is used. */
class SupabaseApi(private val baseUrl: String, private val publicKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private suspend fun exec(
        method: String, path: String, token: String?, body: JsonElement? = null, headers: Map<String, String> = emptyMap()
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("apikey", publicKey)
            .header("Authorization", "Bearer " + (token ?: publicKey))
        headers.forEach { (k, v) -> b.header(k, v) }
        val rb = body?.toString()?.toRequestBody(jsonType)
        when (method) {
            "GET" -> b.get()
            "DELETE" -> if (rb != null) b.delete(rb) else b.delete()
            else -> b.method(method, rb ?: "{}".toRequestBody(jsonType))
        }
        try {
            http.newCall(b.build()).execute().use { r -> r.code to (r.body?.string() ?: "") }
        } catch (e: IOException) {
            throw OfflineException()
        }
    }

    private fun errorText(code: Int, text: String): String {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val raw = listOf("error", "msg", "message", "error_description").firstNotNullOfOrNull { k ->
            obj?.get(k)?.let { (it as? JsonPrimitive)?.contentOrNull }
        } ?: ""
        return when {
            raw.contains("Invalid login credentials", true) -> "كلمة السر غير صحيحة"
            raw.contains("row-level security", true) || code == 403 -> if (raw.any { it in 'ء'..'ي' }) raw else "ليست لديك صلاحية لهذه العملية"
            raw.contains("duplicate key", true) -> "هذا السجل موجود مسبقاً"
            raw.isNotBlank() && raw.any { it in 'ء'..'ي' } -> raw
            code == 401 -> "انتهت الجلسة، سجّل الدخول مجدداً"
            code >= 500 -> "الخادم غير متاح حالياً، حاول بعد قليل"
            else -> "تعذّر تنفيذ العملية ($code)"
        }
    }

    private suspend fun ok(method: String, path: String, token: String?, body: JsonElement? = null, headers: Map<String, String> = emptyMap()): String {
        val (code, text) = exec(method, path, token, body, headers)
        if (code !in 200..299) throw ApiException(code, errorText(code, text))
        return text
    }

    private fun sessionFrom(text: String): Session = sessionOf(json.parseToJsonElement(text).jsonObject)

    fun sessionOf(o: JsonObject): Session {
        val expiresIn = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        return Session(
            accessToken = o["access_token"]!!.jsonPrimitive.content,
            refreshToken = o["refresh_token"]!!.jsonPrimitive.content,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000,
            userId = o["user"]!!.jsonObject["id"]!!.jsonPrimitive.content
        )
    }

    suspend fun signIn(email: String, password: String): Session =
        sessionFrom(ok("POST", "/auth/v1/token?grant_type=password", null, buildJsonObject { put("email", email); put("password", password) }))

    suspend fun refresh(refreshToken: String): Session =
        sessionFrom(ok("POST", "/auth/v1/token?grant_type=refresh_token", null, buildJsonObject { put("refresh_token", refreshToken) }))

    suspend fun signOut(token: String) { runCatching { exec("POST", "/auth/v1/logout", token) } }

    suspend fun updatePassword(token: String, password: String) {
        ok("PUT", "/auth/v1/user", token, buildJsonObject { put("password", password) })
    }

    /** Reads every row the user may see (RLS), page by page. */
    suspend fun selectAll(token: String, table: String, query: String = "select=*"): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        val page = 1000
        var offset = 0
        while (true) {
            val text = ok("GET", "/rest/v1/$table?$query&limit=$page&offset=$offset", token)
            val arr = json.parseToJsonElement(text).jsonArray.map { it.jsonObject }
            out += arr
            if (arr.size < page) break
            offset += page
        }
        return out
    }

    suspend fun upsert(token: String, table: String, rows: JsonElement, onConflict: String, ignore: Boolean = false) {
        ok("POST", "/rest/v1/$table?on_conflict=$onConflict", token, rows,
            mapOf("Prefer" to (if (ignore) "resolution=ignore-duplicates" else "resolution=merge-duplicates") + ",return=minimal"))
    }

    suspend fun patch(token: String, table: String, filter: String, body: JsonElement) {
        ok("PATCH", "/rest/v1/$table?$filter", token, body, mapOf("Prefer" to "return=minimal"))
    }

    suspend fun delete(token: String, table: String, filter: String) {
        ok("DELETE", "/rest/v1/$table?$filter", token, null, mapOf("Prefer" to "return=minimal"))
    }

    /** Debug self-test: a raw write that bypasses the app's own permission checks, so the SERVER's answer is what shows. */
    suspend fun rawInsert(token: String, table: String, row: JsonObject): Pair<Int, String> =
        exec("POST", "/rest/v1/$table", token, row, mapOf("Prefer" to "return=minimal"))
    suspend fun rawPatch(token: String, table: String, filter: String, row: JsonObject): Pair<Int, String> =
        exec("PATCH", "/rest/v1/$table?$filter", token, row, mapOf("Prefer" to "return=representation"))

    suspend fun function(token: String?, name: String, body: JsonObject): JsonObject {
        val text = ok("POST", "/functions/v1/$name", token, body)
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }
}
