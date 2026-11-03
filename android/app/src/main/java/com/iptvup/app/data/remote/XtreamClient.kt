package com.iptvup.app.data.remote

import com.iptvup.app.data.identity.ChannelIdentity
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.model.ParsedChannel
import com.iptvup.app.data.model.ProfileCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamClient @Inject constructor(
    private val okHttp: OkHttpClient
) {

    suspend fun liveCategories(credentials: ProfileCredentials): Map<String, String> =
        withContext(Dispatchers.IO) {
            val host = normalizeHost(credentials.xtreamHost ?: error("Falta el host Xtream"))
            val user = credentials.xtreamUsername ?: error("Falta el usuario Xtream")
            val pass = credentials.xtreamPassword ?: error("Falta la contraseña Xtream")
            val url = "$host/player_api.php".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("username", user)
                ?.addQueryParameter("password", pass)
                ?.addQueryParameter("action", "get_live_categories")
                ?.build()
                ?: error("Host Xtream no válido")
            val json = getJson(url.toString())
            val array = JSONArray(json)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("category_id", "")
                    if (id.isNotBlank()) {
                        put(id, item.optString("category_name", "Sin grupo"))
                    }
                }
            }
        }

    suspend fun liveStreams(
        credentials: ProfileCredentials,
        categories: Map<String, String>,
        categoryIds: Set<String>? = null
    ): List<ParsedChannel> = withContext(Dispatchers.IO) {
        val host = normalizeHost(credentials.xtreamHost ?: error("Falta el host Xtream"))
        val user = credentials.xtreamUsername ?: error("Falta el usuario Xtream")
        val pass = credentials.xtreamPassword ?: error("Falta la contraseña Xtream")
        val url = "$host/player_api.php".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("username", user)
            ?.addQueryParameter("password", pass)
            ?.addQueryParameter("action", "get_live_streams")
            ?.build()
            ?: error("Host Xtream no válido")

        val result = ArrayList<ParsedChannel>()
        val usedKeys = HashSet<String>()
        if (categoryIds == null) {
            parseStreams(getJson(url.toString()), host, user, pass, categories, result, usedKeys)
        } else {
            var successfulCategories = 0
            categoryIds.forEach { categoryId ->
                try {
                    val categoryUrl = url.newBuilder()
                        .addQueryParameter("category_id", categoryId)
                        .build()
                    parseStreams(
                        getJson(categoryUrl.toString()),
                        host,
                        user,
                        pass,
                        categories,
                        result,
                        usedKeys
                    )
                    successfulCategories++
                } catch (_: Exception) {
                    // Si el proveedor no soporta category_id, se reintenta abajo.
                }
            }
            if (successfulCategories == 0 && categoryIds.isNotEmpty()) {
                result.clear()
                usedKeys.clear()
                parseStreams(getJson(url.toString()), host, user, pass, categories, result, usedKeys)
            }
        }
        result.distinctBy { it.channelKey }
    }

    private fun parseStreams(
        json: String,
        host: String,
        user: String,
        pass: String,
        categories: Map<String, String>,
        output: MutableList<ParsedChannel>,
        usedKeys: MutableSet<String>
    ) {
        val array = JSONArray(json)
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val streamId = item.opt("stream_id")?.toString()
                ?.takeIf { it != "null" && it.isNotBlank() }
                ?: continue
            val name = item.optString("name", "").ifBlank { "Canal $streamId" }
            if (ChannelQuality.isJunkChannel(name)) continue
            val categoryId = item.opt("category_id")?.toString().orEmpty()
            val group = categories[categoryId].orEmpty().ifBlank { "Sin grupo" }
            val epgId = item.opt("epg_channel_id")?.toString()
                ?.takeIf { it != "null" && it != "0" && it.isNotBlank() }
            val logo = item.optString("stream_icon", "").takeIf { it.isNotBlank() }
            val dedupeKey = ChannelIdentity.dedupeKey(name, group, epgId)
            val legacyKey = ChannelIdentity.channelKey(dedupeKey)
            val channelKey = if (usedKeys.add(legacyKey)) {
                legacyKey
            } else {
                ChannelIdentity.uniqueChannelKey(name, group, streamId)
            }
            val streamUrl = "$host/live/$user/$pass/$streamId.m3u8"
            output += ParsedChannel(
                channelKey = channelKey,
                dedupeKey = dedupeKey,
                name = name,
                logoUrl = logo,
                groupName = group,
                streamUrl = streamUrl,
                epgChannelId = epgId,
                streamId = streamId,
                sortOrder = output.size
            )
        }
    }

    fun epgUrl(credentials: ProfileCredentials): String? {
        val host = credentials.xtreamHost?.let(::normalizeHost) ?: return null
        val user = credentials.xtreamUsername ?: return null
        val pass = credentials.xtreamPassword ?: return null
        return "$host/xmltv.php".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("username", user)
            ?.addQueryParameter("password", pass)
            ?.build()
            ?.toString()
    }

    private fun getJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Xtream respondió HTTP ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: error("Xtream devolvió una respuesta vacía")
        }
    }

    private fun normalizeHost(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
