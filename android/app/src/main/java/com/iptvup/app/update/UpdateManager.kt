package com.iptvup.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.iptvup.app.BuildConfig
import com.iptvup.app.core.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val notes: String,
    val minSupportedVersionCode: Int
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(
        val info: UpdateInfo,
        val mandatory: Boolean
    ) : UpdateState
    data class Downloading(val progress: Int) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
    private val settings: SettingsStore
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var current: UpdateInfo? = null
    private var pendingApk: File? = null

    init {
        // Limpia APKs de actualizaciones anteriores; Android ya instaló la versión nueva.
        runCatching {
            context.cacheDir.listFiles { file ->
                file.isFile && file.name.matches(Regex("iptvup-\\d+\\.apk"))
            }?.forEach { it.delete() }
        }
    }

    suspend fun check(manual: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = settings.lastUpdateCheckMs.first()
        if (!manual && now - lastCheck < DAY_MS) return@withContext

        _state.value = UpdateState.Checking
        try {
            val manifest = fetchManifest()
            settings.setLastUpdateCheck(now)
            if (manifest == null || manifest.versionCode <= BuildConfig.VERSION_CODE) {
                _state.value = UpdateState.UpToDate
                return@withContext
            }
            val skipped = settings.skippedVersionCode.first()
            if (!manual && skipped == manifest.versionCode) {
                _state.value = UpdateState.Idle
                return@withContext
            }
            current = manifest
            _state.value = UpdateState.Available(
                info = manifest,
                mandatory = manifest.minSupportedVersionCode > BuildConfig.VERSION_CODE
            )
        } catch (exception: Exception) {
            _state.value = if (manual) {
                UpdateState.Error(exception.message ?: "No se pudo comprobar la actualización")
            } else {
                UpdateState.Idle
            }
        }
    }

    suspend fun skip() {
        val info = current ?: return
        settings.setSkippedVersionCode(info.versionCode)
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        when (_state.value) {
            is UpdateState.Available, is UpdateState.Error, UpdateState.Idle -> {
                _state.value = UpdateState.Idle
            }
            else -> Unit
        }
    }

    suspend fun downloadAndInstall() = withContext(Dispatchers.IO) {
        val info = current
        if (info == null) {
            check(manual = true)
            return@withContext
        }

        val existing = pendingApk
        if (existing != null && existing.exists()) {
            install(existing)
            return@withContext
        }

        _state.value = UpdateState.Downloading(0)
        val target = File(context.cacheDir, "iptvup-${info.versionCode}.apk")
        try {
            val request = Request.Builder().url(info.apkUrl).build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("La descarga respondió HTTP ${response.code}")
                }
                val body = response.body ?: error("La descarga está vacía")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val progress = ((written * 100) / total).toInt()
                                if (progress != (_state.value as? UpdateState.Downloading)?.progress) {
                                    _state.value = UpdateState.Downloading(progress)
                                }
                            }
                            read = input.read(buffer)
                        }
                    }
                }
                val hash = digest.digest().joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
                val expected = info.sha256?.trim()?.lowercase().orEmpty()
                if (expected.isNotBlank() && expected != hash) {
                    target.delete()
                    error("El archivo descargado no coincide con el SHA-256 esperado")
                }
            }

            if (!hasSameSignature(target)) {
                target.delete()
                error("El APK descargado no tiene la misma firma que IPTVUp")
            }

            pendingApk = target
            install(target)
        } catch (exception: Exception) {
            target.delete()
            _state.value = UpdateState.Error(
                exception.message ?: "No se pudo descargar la actualización"
            )
        }
    }

    private fun install(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            _state.value = UpdateState.Error(
                "Activa \"Instalar apps desconocidas\" para IPTVUp y vuelve a pulsar Actualizar"
            )
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _state.value = UpdateState.Installing
        } catch (exception: Exception) {
            _state.value = UpdateState.Error(
                exception.message ?: "No se pudo abrir el instalador"
            )
        }
    }

    private fun fetchManifest(): UpdateInfo? {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_MANIFEST_URL)
            .header("Cache-Control", "no-cache")
            .build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("No se pudo leer el manifiesto (HTTP ${response.code})")
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) return null
            val json = JSONObject(text)
            val versionCode = json.optInt("versionCode", 0)
            val apkUrl = json.optString("apkUrl", "")
            if (versionCode <= 0 || apkUrl.isBlank()) return null
            return UpdateInfo(
                versionCode = versionCode,
                versionName = json.optString("versionName", "?"),
                apkUrl = apkUrl,
                sha256 = json.optString("sha256", "").takeIf { it.isNotBlank() },
                notes = json.optString("notes", ""),
                minSupportedVersionCode = json.optInt("minSupportedVersionCode", 1)
            )
        }
    }

    private fun hasSameSignature(apk: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return false
        val installedSignatures = signatureHashes(installed)
        val archiveSignatures = signatureHashes(archive)
        return installedSignatures.isNotEmpty() && installedSignatures == archiveSignatures
    }

    private fun signatureHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
            digest.joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        }.toSet()
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
