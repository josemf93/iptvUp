package com.iptvup.app.data.repository

import android.content.Context
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.EpgProgramEntity
import com.iptvup.app.data.local.ProfileDao
import com.iptvup.app.data.local.toEntity
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.epg.SpanishEpgMatcher
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.model.ProfileCredentials
import com.iptvup.app.data.model.ProfileKind
import com.iptvup.app.data.parser.M3uParser
import com.iptvup.app.data.parser.XmltvParser
import com.iptvup.app.data.remote.XtreamClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val FALLBACK_PREFIX = "es:"

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val profileDao: ProfileDao,
    private val m3uParser: M3uParser,
    private val xmltvParser: XmltvParser,
    private val xtreamClient: XtreamClient,
    private val okHttp: OkHttpClient,
    private val settings: SettingsStore
) {

    /**
     * Actualiza canales. La EPG solo se descarga si [epgDue] es true
     * (una vez al día o si nunca se ha descargado).
     */
    suspend fun sync(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        epgDue: Boolean,
        onStatus: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onStatus("Actualizando todos los canales...")
            val discoveredEpg = when (profile.kind) {
                ProfileKind.M3U -> syncM3u(profile, credentials, onStatus)
                ProfileKind.XTREAM -> syncXtream(profile, credentials, onStatus)
            }
            if (epgDue) {
                syncEpgFor(profile, credentials, discoveredEpg, onStatus)
            } else {
                onStatus("EPG al día")
            }
            profileDao.updateLastSynced(profile.id, System.currentTimeMillis())
            onStatus("Listo")
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /** Descarga solo la EPG, sin tocar los canales. */
    suspend fun refreshEpg(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        onStatus: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            syncEpgFor(profile, credentials, null, onStatus)
            onStatus("EPG actualizada")
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun syncEpgFor(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        discoveredEpg: String?,
        onStatus: (String) -> Unit
    ) {
        val epgUrl = profile.epgUrl?.takeIf { it.isNotBlank() }
            ?: credentials.epgUrl?.takeIf { it.isNotBlank() }
            ?: discoveredEpg?.takeIf { it.isNotBlank() }
            ?: if (profile.kind == ProfileKind.XTREAM) {
                xtreamClient.epgUrl(credentials)
            } else {
                null
            }

        var epgOk = false
        if (epgUrl != null) {
            if (!discoveredEpg.isNullOrBlank()) {
                profileDao.updateEpgUrl(profile.id, discoveredEpg)
            }
            epgDao.deleteByProfile(profile.id)
            try {
                syncEpg(profile, epgUrl, onStatus)
                epgOk = true
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                onStatus("EPG no disponible: ${exception.message ?: "error"}")
            }
        } else {
            epgDao.deleteByProfile(profile.id)
            onStatus("Sin URL de EPG del proveedor")
        }

        try {
            syncSpanishFallbackEpg(profile, onStatus)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            onStatus("EPG española no disponible: ${exception.message ?: "error"}")
        }

        if (epgOk) {
            settings.setLastEpgSync(profile.id, System.currentTimeMillis())
        }
    }

    private suspend fun syncM3u(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        onStatus: (String) -> Unit
    ): String? {
        val url = credentials.m3uUrl?.takeIf { it.isNotBlank() }
            ?: error("Falta la URL de la lista M3U")
        onStatus("Descargando lista M3U...")
        val file = downloadToCache(url, "m3u_${profile.id}_")
        return try {
            if (file.length() == 0L) error("La lista M3U está vacía")
            val fallback = fallbackMap(profile.id)
            channelDao.deleteByProfile(profile.id)
            var count = 0
            val batch = ArrayList<ChannelEntity>(500)
            var epgUrl: String? = null
            maybeGzip(file.inputStream()).bufferedReader().use { reader ->
                val result = m3uParser.parse(reader) { parsed ->
                    if (ChannelQuality.isJunkChannel(parsed.name)) return@parse
                    batch += withFallbackEpg(parsed.toEntity(profile.id), fallback)
                    count++
                    if (batch.size >= 500) {
                        channelDao.upsertAll(batch)
                        batch.clear()
                        onStatus("Canales: $count")
                    }
                }
                epgUrl = result.epgUrl
            }
            if (batch.isNotEmpty()) channelDao.upsertAll(batch)
            onStatus("$count canales")
            epgUrl
        } finally {
            file.delete()
        }
    }

    private suspend fun syncXtream(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        onStatus: (String) -> Unit
    ): String? {
        if (credentials.xtreamHost.isNullOrBlank() ||
            credentials.xtreamUsername.isNullOrBlank() ||
            credentials.xtreamPassword.isNullOrBlank()
        ) {
            error("Faltan las credenciales de Xtream")
        }
        onStatus("Conectando con Xtream...")
        val categories = xtreamClient.liveCategories(credentials)
        onStatus("Descargando canales...")
        val channels = xtreamClient.liveStreams(credentials, categories)

        val fallback = fallbackMap(profile.id)
        channelDao.deleteByProfile(profile.id)
        channels.chunked(500).forEachIndexed { index, chunk ->
            channelDao.upsertAll(
                chunk.map { withFallbackEpg(it.toEntity(profile.id), fallback) }
            )
            onStatus("Canales: ${minOf((index + 1) * 500, channels.size)}")
        }
        onStatus("${channels.size} canales")
        return xtreamClient.epgUrl(credentials)
    }

    /** Guarda los emparejamientos con la EPG española para no perderlos al actualizar canales. */
    private suspend fun fallbackMap(profileId: String): Map<String, String> =
        channelDao.fallbackEpgRows(profileId).associate { it.id to it.epgChannelId }

    private fun withFallbackEpg(entity: ChannelEntity, fallback: Map<String, String>): ChannelEntity =
        if (entity.epgChannelId == null) {
            entity.copy(epgChannelId = fallback[entity.id])
        } else {
            entity
        }

    private suspend fun syncEpg(
        profile: IptvProfile,
        epgUrl: String,
        onStatus: (String) -> Unit
    ) {
        val epgIds = channelDao.visibleEpgChannelIds(profile.id).toSet()
        if (epgIds.isEmpty()) {
            onStatus("Los canales visibles no tienen id de EPG")
            return
        }
        onStatus("Descargando EPG...")
        val now = System.currentTimeMillis()
        val from = now - 6 * 60 * 60 * 1000L
        val to = now + 7 * 24 * 60 * 60 * 1000L
        val file = downloadToCache(epgUrl, "epg_${profile.id}_")
        try {
            if (file.length() == 0L) error("El EPG está vacío")
            var count = 0
            val batch = ArrayList<EpgProgramEntity>(500)
            maybeGzip(file.inputStream()).use { input ->
                xmltvParser.parse(input, from, to, epgIds) { program ->
                    batch += program.toEntity(profile.id)
                    count++
                    if (batch.size >= 500) {
                        runBlocking { epgDao.upsertAll(batch) }
                        batch.clear()
                        onStatus("EPG: $count programas")
                    }
                }
            }
            if (batch.isNotEmpty()) epgDao.upsertAll(batch)
            epgDao.deleteBefore(profile.id, now - 24 * 60 * 60 * 1000L)
            onStatus("EPG: $count programas")
        } finally {
            file.delete()
        }
    }

    private suspend fun syncSpanishFallbackEpg(
        profile: IptvProfile,
        onStatus: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val from = now - 6 * 60 * 60 * 1000L
        val to = now + 7 * 24 * 60 * 60 * 1000L
        val covered = epgDao.epgIdsWithPrograms(profile.id, from, to).toSet()
        val candidates = channelDao.getVisible(profile.id).filter { channel ->
            channel.epgChannelId.isNullOrBlank() || channel.epgChannelId !in covered
        }
        if (candidates.isEmpty()) return

        onStatus("Buscando EPG española de respaldo...")
        val file = downloadToCache(SpanishEpgMatcher.URL, "epg_es_${profile.id}_")
        try {
            if (file.length() == 0L) error("La EPG española está vacía")
            val index = maybeGzip(file.inputStream()).use { SpanishEpgMatcher.readIndex(it) }
            val matches = candidates.mapNotNull { channel ->
                SpanishEpgMatcher.match(channel.name, index)?.let { channel to it }
            }
            if (matches.isEmpty()) return

            val wantedIds = matches.map { it.second }.toSet()
            val batch = ArrayList<EpgProgramEntity>(500)
            maybeGzip(file.inputStream()).use { input ->
                xmltvParser.parse(input, from, to, wantedIds) { program ->
                    val prefixed = program.copy(epgChannelId = FALLBACK_PREFIX + program.epgChannelId)
                    batch += prefixed.toEntity(profile.id)
                    if (batch.size >= 500) {
                        runBlocking { epgDao.upsertAll(batch) }
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) epgDao.upsertAll(batch)
            matches.forEach { (channel, epgId) ->
                channelDao.updateEpgChannelId(channel.id, FALLBACK_PREFIX + epgId)
            }
            onStatus("EPG española: ${matches.size} canales emparejados")
        } finally {
            file.delete()
        }
    }

    private fun downloadToCache(url: String, prefix: String): File {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVUp/1.0")
            .build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} al descargar la lista")
            }
            val body = response.body ?: error("Respuesta vacía")
            val file = File.createTempFile(prefix, ".tmp", context.cacheDir)
            try {
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (exception: Exception) {
                file.delete()
                throw exception
            }
            return file
        }
    }

    private fun maybeGzip(raw: InputStream): InputStream {
        val buffered = BufferedInputStream(raw)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == 0x1f && second == 0x8b) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }
}
