package com.iptvup.app.data.parser

import com.iptvup.app.data.identity.ChannelIdentity
import com.iptvup.app.data.model.ParsedChannel
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Lector de listas W3U (formato de WiseIPTV / WisePlay). Son JSON: un índice de categorías que
 * apuntan a otras listas (también JSON, a veces anidadas) hasta llegar a "stations" con la URL
 * del stream. Como en WisePlay, la categoría de primer nivel es el grupo; las sub-secciones
 * (eventos, películas...) se muestran como prefijo del nombre del canal.
 * Las entradas con "embed": "true" son páginas web de guía, no streams, y se omiten.
 */
class W3uParser @Inject constructor() {

    data class W3uResult(val count: Int, val lists: Int, val failures: Int)

    /** Indica si el contenido inicial corresponde a un W3U (JSON) y no a una M3U. */
    fun isW3u(head: String): Boolean =
        head.trimStart('\uFEFF', ' ', '\t', '\r', '\n').startsWith("{")

    suspend fun parse(
        rootUrl: String,
        rootText: String,
        fetchText: (String) -> String?,
        onStatus: (String) -> Unit,
        onChannel: suspend (ParsedChannel) -> Unit
    ): W3uResult {
        val walk = Walk(fetchText, onStatus, onChannel)
        walk.visited += rootUrl
        walkDocument(walk, rootText, depth = 0, top = null)
        return W3uResult(walk.count, walk.lists, walk.failures)
    }

    private class Walk(
        val fetchText: (String) -> String?,
        val onStatus: (String) -> Unit,
        val onChannel: suspend (ParsedChannel) -> Unit
    ) {
        val visited = HashSet<String>()
        val usedKeys = HashSet<String>()
        var count = 0
        var lists = 0
        var failures = 0
        var fetches = 0
    }

    private suspend fun walkDocument(walk: Walk, text: String, depth: Int, top: String?) {
        val root = try {
            JSONObject(clean(text))
        } catch (_: Exception) {
            walk.failures++
            return
        }
        walk.lists++
        walkNode(walk, root, depth, top)
    }

    /** [top] es la categoría de primer nivel a la que pertenece el nodo (null en la raíz). */
    private suspend fun walkNode(walk: Walk, node: JSONObject, depth: Int, top: String?) {
        val container = node.optString("name").trim()
        node.optJSONArray("stations")?.forEachObject { station ->
            addStation(walk, station, container, top)
        }
        node.optJSONArray("groups")?.forEachObject { child ->
            if (depth >= MAX_DEPTH) return@forEachObject
            val childTop = top ?: child.optString("name").trim().ifBlank { null }
            if (child.has("stations") || child.has("groups")) {
                walkNode(walk, child, depth + 1, childTop)
            } else {
                val childUrl = child.optString("url").trim()
                if (childUrl.startsWith("http", ignoreCase = true) &&
                    walk.fetches < MAX_FETCHES &&
                    walk.visited.add(childUrl)
                ) {
                    walk.fetches++
                    if (walk.fetches % 20 == 0) walk.onStatus("Leyendo listas W3U: ${walk.fetches}")
                    val text = walk.fetchText(childUrl)
                    if (text == null) {
                        walk.failures++
                    } else {
                        walkDocument(walk, text, depth + 1, childTop)
                    }
                }
            }
        }
    }

    private suspend fun addStation(walk: Walk, station: JSONObject, container: String, top: String?) {
        val url = station.optString("url").trim()
        if (!URL_REGEX.matches(url)) return
        if (station.optString("embed").equals("true", ignoreCase = true)) return

        val group = (top ?: container).ifBlank { "Sin grupo" }
        val section = container.takeIf { top != null && it.isNotBlank() && it != top }
        val baseName = station.optString("name").trim().ifBlank { "Canal" }
        val info = station.optString("info").trim()
        val titled = if (section != null) "$section · $baseName" else baseName
        val name = if (info.isNotBlank() && !titled.contains(info)) "$titled $info" else titled

        val dedupeKey = ChannelIdentity.dedupeKey(name, group, null)
        val legacyKey = ChannelIdentity.channelKey(dedupeKey)
        val channelKey = if (walk.usedKeys.add(legacyKey)) {
            legacyKey
        } else {
            ChannelIdentity.uniqueChannelKey(name, group, url)
        }
        val logo = station.optString("image").trim().takeIf { it.startsWith("http", ignoreCase = true) }
        walk.onChannel(
            ParsedChannel(
                channelKey = channelKey,
                dedupeKey = dedupeKey,
                name = name,
                logoUrl = logo,
                groupName = group,
                streamUrl = url,
                epgChannelId = null,
                sortOrder = walk.count
            )
        )
        walk.count++
    }

    /** Quita BOM y saltos de línea: las listas traen cadenas partidas en varias líneas. */
    private fun clean(text: String): String =
        text.removePrefix("\uFEFF").replace("\r", "").replace("\n", "")

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(action)
        }
    }

    private companion object {
        const val MAX_DEPTH = 6
        const val MAX_FETCHES = 500

        /** Cualquier URL con esquema: http(s), acestream://, sop://... */
        val URL_REGEX = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
    }
}
