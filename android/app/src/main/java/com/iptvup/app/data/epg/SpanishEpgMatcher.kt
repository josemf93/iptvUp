package com.iptvup.app.data.epg

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

/**
 * Empareja canales de nuestra lista con los canales españoles de epgshare
 * por nombre, aunque no sean idénticos ("ES MOVISTAR ACCION" ~ "Acción por M+").
 */
object SpanishEpgMatcher {

    const val URL = "https://epgshare01.online/epgshare01/epg_ripper_ES1.xml.gz"

    private val STOPWORDS = setOf(
        "es", "esp", "espana", "spain", "hd", "fhd", "uhd", "4k", "sd", "720p", "1080p",
        "movistar", "m", "plus", "por", "tv", "canal"
    )
    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    fun tokens(value: String): Set<String> {
        val text = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
        return text.split(NON_ALPHANUMERIC)
            .filter { it.isNotBlank() && it !in STOPWORDS }
            .toSet()
    }

    /** Lee los canales de la guía y devuelve (id de epgshare, tokens de sus nombres). */
    fun readIndex(input: InputStream): List<Pair<String, Set<String>>> {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        val result = ArrayList<Pair<String, Set<String>>>()
        factory.newSAXParser().parse(input, object : DefaultHandler() {
            private var channelId: String? = null
            private var inName = false
            private val name = StringBuilder()
            private val names = ArrayList<String>()

            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes?
            ) {
                when (qName) {
                    "channel" -> {
                        channelId = attributes?.getValue("id")?.trim()
                        names.clear()
                    }
                    "display-name" -> {
                        inName = true
                        name.clear()
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inName) name.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "display-name" -> {
                        inName = false
                        names += name.toString().trim()
                    }
                    "channel" -> {
                        val id = channelId ?: return
                        val fromNames = names.flatMap { tokens(it) }.toSet()
                        val tokens = fromNames.ifEmpty { tokens(id.removeSuffix(".es").replace('.', ' ')) }
                        if (tokens.isNotEmpty()) result += id to tokens
                        channelId = null
                    }
                }
            }
        })
        return result
    }

    /** Devuelve el id de epgshare que mejor encaja con el nombre del canal, o null. */
    fun match(channelName: String, index: List<Pair<String, Set<String>>>): String? {
        val wanted = tokens(channelName)
        if (wanted.isEmpty()) return null
        return index
            .filter { (_, candidate) ->
                candidate.isNotEmpty() && wanted.intersect(candidate).isNotEmpty() &&
                    (wanted.containsAll(candidate) || candidate.containsAll(wanted))
            }
            .minByOrNull { (_, candidate) ->
                wanted.size + candidate.size - 2 * wanted.intersect(candidate).size
            }
            ?.first
    }
}
