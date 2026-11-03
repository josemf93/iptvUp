package com.iptvup.app.data.parser

import com.iptvup.app.data.model.ParsedProgram
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.xml.parsers.SAXParserFactory

class XmltvParser @Inject constructor() {

    fun parse(
        input: InputStream,
        fromMs: Long,
        toMs: Long,
        allowedEpgIds: Set<String>? = null,
        onProgram: (ParsedProgram) -> Unit
    ): Int {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val handler = Handler(fromMs, toMs, allowedEpgIds, onProgram)
        factory.newSAXParser().parse(input, handler)
        return handler.count
    }

    private class Handler(
        private val fromMs: Long,
        private val toMs: Long,
        private val allowedEpgIds: Set<String>?,
        private val onProgram: (ParsedProgram) -> Unit
    ) : DefaultHandler() {

        var count: Int = 0
            private set

        private var active = false
        private var currentChannel: String? = null
        private var currentStart: Long = 0
        private var currentStop: Long = 0
        private var inTitle = false
        private var inDescription = false
        private val title = StringBuilder()
        private val description = StringBuilder()

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val name = (localName?.ifBlank { qName } ?: qName ?: "").lowercase()
            when (name) {
                "programme" -> {
                    val channel = attributes?.getValue("channel")?.trim().orEmpty()
                    val allowed = channel.isNotBlank() &&
                        (allowedEpgIds == null || channel in allowedEpgIds)
                    val start = if (allowed) parseXmltvTime(attributes?.getValue("start")) else null
                    val stop = if (allowed) parseXmltvTime(attributes?.getValue("stop")) else null
                    val inWindow = start != null && stop != null && stop > fromMs && start <= toMs
                    active = allowed && start != null && stop != null && inWindow
                    if (active) {
                        currentChannel = channel
                        currentStart = start!!
                        currentStop = stop!!
                        title.clear()
                        description.clear()
                    }
                }

                "title" -> if (active) inTitle = true
                "desc" -> if (active) inDescription = true
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inTitle) title.append(ch, start, length)
            if (inDescription) description.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = (localName?.ifBlank { qName } ?: qName ?: "").lowercase()
            when (name) {
                "title" -> inTitle = false
                "desc" -> inDescription = false
                "programme" -> {
                    if (active) {
                        val cleanTitle = title.toString().trim().replace(WHITESPACE, " ")
                        if (cleanTitle.isNotEmpty()) {
                            onProgram(
                                ParsedProgram(
                                    epgChannelId = currentChannel.orEmpty(),
                                    startMs = currentStart,
                                    endMs = currentStop,
                                    title = cleanTitle,
                                    description = description.toString()
                                        .trim()
                                        .replace(WHITESPACE, " ")
                                        .ifBlank { null }
                                )
                            )
                            count++
                        }
                    }
                    active = false
                    currentChannel = null
                    inTitle = false
                    inDescription = false
                }
            }
        }
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val DATE_FORMATS = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmmssZ",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmmZ",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm"
        )

        private val FORMATS: ThreadLocal<List<SimpleDateFormat>> = ThreadLocal.withInitial {
            DATE_FORMATS.map { pattern ->
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            }
        }

        fun parseXmltvTime(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val value = raw.trim()
            for (format in FORMATS.get()!!) {
                try {
                    return format.parse(value)?.time
                } catch (_: Exception) {
                    // Try the next format.
                }
            }
            return null
        }
    }
}
