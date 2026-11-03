package com.iptvup.app.data.repository

import android.content.Context
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.ProfileDao
import com.iptvup.app.data.local.toEntity
import com.iptvup.app.data.model.IptvProfile
import com.iptvup.app.data.model.ProfileCredentials
import com.iptvup.app.data.model.ProfileKind
import com.iptvup.app.data.parser.M3uParser
import com.iptvup.app.data.parser.EpgFallback
import com.iptvup.app.data.parser.XmltvFallbackParser
import com.iptvup.app.data.parser.XmltvParser
import com.iptvup.app.data.remote.XtreamClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val profileDao: ProfileDao,
    private val m3uParser: M3uParser,
    private val xmltvParser: XmltvParser,
    private val xmltvFallbackParser: XmltvFallbackParser,
    private val xtreamClient: XtreamClient,
    private val okHttp: OkHttpClient
) {

    suspend fun sync(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        hiddenGroups: Set<String> = emptySet(),
        fullSync: Boolean = true,
        onStatus: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cachedChannels = channelDao.count(profile.id)
            val effectiveFullSync = fullSync ||
                (cachedChannels == 0 && hiddenGroups.isEmpty())
            val excludedGroups = if (effectiveFullSync) emptySet() else hiddenGroups
            onStatus(
                if (effectiveFullSync) {
                    "Actualizando todos los canales..."
                } else {
                    "Actualizando grupos visibles..."
                }
            )

            val discoveredEpg = when (profile.kind) {
                ProfileKind.M3U -> syncM3u(profile, credentials, excludedGroups, onStatus)
                ProfileKind.XTREAM -> syncXtream(profile, credentials, excludedGroups, onStatus)
            }

            val epgUrl = profile.epgUrl?.takeIf { it.isNotBlank() }
                ?: credentials.epgUrl?.takeIf { it.isNotBlank() }
                ?: discoveredEpg?.takeIf { it.isNotBlank() }
                ?: if (profile.kind == ProfileKind.XTREAM) {
                    xtreamClient.epgUrl(credentials)
                } else {
                    null
                }

            if (epgUrl != null) {
                if (!discoveredEpg.isNullOrBlank()) {
                    profileDao.updateEpgUrl(profile.id, discoveredEpg)
                }
                epgDao.deleteByProfile(profile.id)
                try {
                    syncEpg(profile, epgUrl, excludedGroups, onStatus)
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
                syncFallbackEpg(profile, excludedGroups, onStatus)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                onStatus("EPG de respaldo no disponible: ${exception.message ?: "error"}")
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

    private suspend fun syncM3u(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        excludedGroups: Set<String>,
        onStatus: (String) -> Unit
    ): String? {
        val url = credentials.m3uUrl?.takeIf { it.isNotBlank() }
            ?: error("Falta la URL de la lista M3U")
        onStatus("Descargando lista M3U...")
        val file = downloadToCache(url, "m3u_${profile.id}_")
        return try {
            if (file.length() == 0L) error("La lista M3U está vacía")
            val replaceAfterParse = excludedGroups.isNotEmpty()
            val collected = if (replaceAfterParse) {
                ArrayList<com.iptvup.app.data.local.ChannelEntity>(1024)
            } else {
                null
            }
            if (!replaceAfterParse) {
                channelDao.deleteByProfile(profile.id)
            }
            var count = 0
            val batch = ArrayList<com.iptvup.app.data.local.ChannelEntity>(500)
            var epgUrl: String? = null
            maybeGzip(file.inputStream()).bufferedReader().use { reader ->
                val result = m3uParser.parse(reader, excludedGroups) { parsed ->
                    val entity = parsed.toEntity(profile.id)
                    count++
                    if (collected != null) {
                        collected += entity
                        if (count % 500 == 0) onStatus("Canales visibles: $count")
                    } else {
                        batch += entity
                        if (batch.size >= 500) {
                            channelDao.upsertAll(batch)
                            batch.clear()
                            onStatus("Canales: $count")
                        }
                    }
                }
                epgUrl = result.epgUrl
            }
            if (collected != null) {
                channelDao.deleteByProfile(profile.id)
                collected.chunked(500).forEach { chunk ->
                    channelDao.upsertAll(chunk)
                }
            } else if (batch.isNotEmpty()) {
                channelDao.upsertAll(batch)
            }
            onStatus(
                if (excludedGroups.isEmpty()) "$count canales" else "$count canales visibles"
            )
            epgUrl
        } finally {
            file.delete()
        }
    }

    private suspend fun syncXtream(
        profile: IptvProfile,
        credentials: ProfileCredentials,
        excludedGroups: Set<String>,
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
        val selectedCategories = if (excludedGroups.isEmpty()) {
            categories
        } else {
            categories.filterValues { it !in excludedGroups }
        }
        if (selectedCategories.isEmpty()) {
            channelDao.deleteByProfile(profile.id)
            onStatus("Sin grupos visibles")
            return xtreamClient.epgUrl(credentials)
        }

        onStatus("Descargando canales...")
        val categoryIds = if (excludedGroups.isEmpty()) null else selectedCategories.keys
        val channels = xtreamClient
            .liveStreams(credentials, categories, categoryIds)
            .filter { it.groupName !in excludedGroups }

        channelDao.deleteByProfile(profile.id)
        channels.chunked(500).forEachIndexed { index, chunk ->
            channelDao.upsertAll(chunk.map { it.toEntity(profile.id) })
            onStatus("Canales: ${minOf((index + 1) * 500, channels.size)}")
        }
        onStatus(
            if (excludedGroups.isEmpty()) {
                "${channels.size} canales"
            } else {
                "${channels.size} canales visibles"
            }
        )
        return xtreamClient.epgUrl(credentials)
    }

    private suspend fun syncEpg(
        profile: IptvProfile,
        epgUrl: String,
        hiddenGroups: Set<String>,
        onStatus: (String) -> Unit
    ) {
        val epgIds = channelDao.observeAll(profile.id).first()
            .asSequence()
            .filter { it.groupName !in hiddenGroups }
            .mapNotNull { channel ->
                channel.epgChannelId?.takeIf { it.isNotBlank() }
            }
            .toSet()
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
            val batch = ArrayList<com.iptvup.app.data.local.EpgProgramEntity>(500)
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

    private suspend fun syncFallbackEpg(
        profile: IptvProfile,
        hiddenGroups: Set<String>,
        onStatus: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val from = now - 6 * 60 * 60 * 1000L
        val to = now + 7 * 24 * 60 * 60 * 1000L
        val covered = epgDao.epgIdsWithPrograms(profile.id, from, to).toSet()
        val channels = channelDao.observeAll(profile.id).first()
            .filter { it.groupName !in hiddenGroups }

        val targets = LinkedHashMap<String, MutableList<String>>()
        val syntheticChannels = LinkedHashMap<String, String>()
        channels.forEach { channel ->
            val normalized = EpgFallback.normalize(channel.name)
            if (normalized.isBlank()) return@forEach
            val epgId = channel.epgChannelId?.takeIf { it.isNotBlank() }
                ?: EpgFallback.syntheticId(channel.name)
            if (epgId in covered) return@forEach
            if (channel.epgChannelId.isNullOrBlank()) {
                syntheticChannels[channel.id] = epgId
            }
            targets.getOrPut(normalized) { mutableListOf() }
                .apply { if (epgId !in this) add(epgId) }
        }
        if (targets.isEmpty()) return

        onStatus("Descargando EPG de respaldo (España)...")
        val file = downloadToCache(EpgFallback.URL, "epg_es_${profile.id}_")
        val matched = HashSet<String>()
        try {
            if (file.length() == 0L) error("La EPG de respaldo está vacía")
            var count = 0
            val batch = ArrayList<com.iptvup.app.data.local.EpgProgramEntity>(500)
            maybeGzip(file.inputStream()).use { input ->
                xmltvFallbackParser.parse(input, from, to, targets) { program ->
                    matched += program.epgChannelId
                    batch += program.toEntity(profile.id)
                    count++
                    if (batch.size >= 500) {
                        runBlocking { epgDao.upsertAll(batch) }
                        batch.clear()
                        onStatus("EPG respaldo: $count programas")
                    }
                }
            }
            if (batch.isNotEmpty()) epgDao.upsertAll(batch)
            syntheticChannels.forEach { (channelId, epgId) ->
                if (epgId in matched) {
                    channelDao.updateEpgChannelId(channelId, epgId)
                }
            }
            onStatus("EPG de respaldo: $count programas")
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
