package com.iptvup.app.data.parser

import com.iptvup.app.data.model.ParsedProgram
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class XmltvFallbackParserTest {

    @Test
    fun matchesChannelByDisplayNameAndUsesTargetEpgId() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="la1.es">
                <display-name>La 1 HD</display-name>
              </channel>
              <programme channel="la1.es" start="20261103180000 +0100" stop="20261103190000 +0100">
                <title>Telediario</title>
                <desc>Informativo</desc>
              </programme>
            </tv>
        """.trimIndent()
        val targets = mapOf(EpgFallback.normalize("La 1") to listOf("provider-la1"))
        val programs = mutableListOf<ParsedProgram>()

        val count = XmltvFallbackParser().parse(
            input = ByteArrayInputStream(xml.toByteArray()),
            fromMs = 0L,
            toMs = Long.MAX_VALUE,
            targets = targets
        ) { programs += it }

        assertEquals(1, count)
        assertEquals(1, programs.size)
        assertEquals("provider-la1", programs.first().epgChannelId)
        assertEquals("Telediario", programs.first().title)
    }

    @Test
    fun normalizesQualityAndCountrySuffixes() {
        assertEquals(
            EpgFallback.normalize("La 1"),
            EpgFallback.normalize("La 1 HD")
        )
        assertEquals(
            EpgFallback.normalize("Movistar Plus"),
            EpgFallback.normalize("Movistar Plus+ FHD")
        )
    }
}
