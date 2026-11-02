package com.iptvup.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.iptvup.app.BuildConfig
import com.iptvup.app.data.model.ProfileCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val auth: FirebaseAuth
) {

    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    data class CreateProfileRequest(
        val name: String,
        val kind: String,
        val m3uUrl: String? = null,
        val xtreamHost: String? = null,
        val xtreamUsername: String? = null,
        val xtreamPassword: String? = null,
        val epgUrl: String? = null
    )

    suspend fun createProfile(request: CreateProfileRequest): String {
        val response = execute(
            url = "$baseUrl/api/profiles",
            method = "POST",
            body = request.toJson()
        )
        val id = JSONObject(response).optString("id", "")
        if (id.isBlank()) error("La API no devolvió el id del perfil")
        return id
    }

    suspend fun updateProfile(profileId: String, request: CreateProfileRequest) {
        execute(
            url = "$baseUrl/api/profiles/$profileId",
            method = "PUT",
            body = request.toJson()
        )
    }

    suspend fun getCredentials(profileId: String): ProfileCredentials {
        val response = execute(
            url = "$baseUrl/api/profiles/$profileId/credentials",
            method = "GET",
            body = null
        )
        val json = JSONObject(response)
        return ProfileCredentials(
            m3uUrl = json.optStringOrNull("m3uUrl"),
            xtreamHost = json.optStringOrNull("xtreamHost"),
            xtreamUsername = json.optStringOrNull("xtreamUsername"),
            xtreamPassword = json.optStringOrNull("xtreamPassword"),
            epgUrl = json.optStringOrNull("epgUrl")
        )
    }

    private suspend fun execute(
        url: String,
        method: String,
        body: JSONObject?
    ): String = withContext(Dispatchers.IO) {
        val token = auth.currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: throw ApiException(401, "La sesión ha caducado")
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .method(method, requestBody)
            .build()
        okHttp.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(text).optString("error", "")
                }.getOrNull().orEmpty().ifBlank {
                    "La API respondió HTTP ${response.code}"
                }
                throw ApiException(response.code, message)
            }
            text
        }
    }

    private fun CreateProfileRequest.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("kind", kind)
        m3uUrl?.let { put("m3uUrl", it) }
        xtreamHost?.let { put("xtreamHost", it) }
        xtreamUsername?.let { put("xtreamUsername", it) }
        xtreamPassword?.let { put("xtreamPassword", it) }
        epgUrl?.let { put("epgUrl", it) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() }
    }

    class ApiException(
        val statusCode: Int,
        override val message: String
    ) : Exception(message)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
