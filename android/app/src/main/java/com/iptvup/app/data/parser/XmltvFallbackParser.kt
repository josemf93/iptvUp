package com.iptvup.app.data.parser

import com.iptvup.app.data.model.ParsedProgram
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.inject.Inject
import javax.xml.parsers.SAXParserFactory

/**
 * Parser para una fuente XMLTV externa (epgshare). Empareja los canales por
 * nombre normalizado y emite los programas con el id de EPG de nuestro canal.
 */
class XmltvFallbackParser @Inject constructor() {

    fun parse(
        input: InputStream,
        fromMs: Long,
        toMs: Long,
        targets: Map<String, List<String>>,
        idTargets: Map<String, List<String>> = emptyMap(),
        onProgram: (ParsedProgram) -> Unit
    ): Int {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val handler = Handler(fromMs, toMs, targets, idTargets, onProgram)
        factory.newSAXParser().parse(input, handler)
        return handler.count
    }

    private class Handler(
        private val fromMs: Long,
        private val toMs: Long,
        private val targets: Map<String, List<String>>,
        private val idTargets: Map<String, List<String>>,
        private val onProgram: (ParsedProgram) -> Unit
    ) : DefaultHandler() {

        var count: Int = 0
            private set

        private val sourceNames = HashMap<String, String>()
        private var currentChannel: String? = null
        private var displayName: StringBuilder? = null
        private var active = false
        private var currentTargets: List<String> = emptyList()
        private var currentStart: Long = 0
        private var currentStop: Long = 0
        private var inTitle = false
        private var inDescription = false
        private val title = StringBuilder()
        private val description = StringBuilder()

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes?
        ) {
            val name = (localName?.ifBlank { qName } ?: qName ?: "").lowercase()
            when (name) {
                "channel" -> {
                    currentChannel = attributes?.getValue("id")?.trim()
                }

                "display-name" -> {
                    if (currentChannel != null) {
                        displayName = StringBuilder()
                    }
                }

                "programme" -> {
                    val sourceChannel = attributes?.getValue("channel")?.trim().orEmpty()
                    val sourceName = sourceNames[sourceChannel]
                    val targetIds = LinkedHashSet<String>()
                    idTargets[sourceChannel]?.let(targetIds::addAll)
                    sourceName?.let { name ->
                        targets[EpgFallback.normalize(name)]?.let(targetIds::addAll)
                    }
                    val start = XmltvParser.parseXmltvTime(attributes?.getValue("start"))
                    val stop = XmltvParser.parseXmltvTime(attributes?.getValue("stop"))
                    val inWindow = start != null && stop != null &&
                        stop > fromMs && start <= toMs
                    active = targetIds.isNotEmpty() && inWindow
                    if (active) {
                        currentTargets = targetIds.toList()
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
            displayName?.append(ch, start, length)
            if (inTitle) title.append(ch, start, length)
            if (inDescription) description.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = (localName?.ifBlank { qName } ?: qName ?: "").lowercase()
            when (name) {
                "display-name" -> {
                    val channel = currentChannel
                    val value = displayName?.toString()?.trim().orEmpty()
                    if (channel != null && value.isNotBlank() && channel !in sourceNames) {
                        sourceNames[channel] = value
                    }
                    displayName = null
                }

                "channel" -> currentChannel = null

                "title" -> inTitle = false
                "desc" -> inDescription = false
                "programme" -> {
                    if (active) {
                        val cleanTitle = title.toString().trim().replace(WHITESPACE, " ")
                        val cleanDescription = description.toString().trim()
                            .replace(WHITESPACE, " ")
                            .ifBlank { null }
                        if (cleanTitle.isNotEmpty()) {
                            currentTargets.forEach { target ->
                                onProgram(
                                    ParsedProgram(
                                        epgChannelId = target,
                                        startMs = currentStart,
                                        endMs = currentStop,
                                        title = cleanTitle,
                                        description = cleanDescription
                                    )
                                )
                            }
                            count++
                        }
                    }
                    active = false
                    currentTargets = emptyList()
                    inTitle = false
                    inDescription = false
                }
            }
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
