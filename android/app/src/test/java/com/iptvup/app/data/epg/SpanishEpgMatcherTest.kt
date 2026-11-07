package com.iptvup.app.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpanishEpgMatcherTest {

    private val xml = """
        <tv>
          <channel id="Acci&#243;n.por.M+.es"><display-name>Acción por M+</display-name></channel>
          <channel id="Antena.3.es"><display-name>Antena 3</display-name></channel>
          <channel id="Canal.24.horas.es"><display-name>Canal 24 horas</display-name></channel>
          <channel id="Canal.24.horas.Catalu&#241;a.es"><display-name>Canal 24 horas Cataluña</display-name></channel>
        </tv>
    """.trimIndent()

    private val index = SpanishEpgMatcher.readIndex(xml.byteInputStream())

    @Test
    fun matchesProviderNameWithPrefixAndQuality() {
        assertEquals("Acción.por.M+.es", SpanishEpgMatcher.match("ES MOVISTAR ACCION", index))
    }

    @Test
    fun matchesIgnoringQualityAndCountry() {
        assertEquals("Antena.3.es", SpanishEpgMatcher.match("ES: Antena 3 HD", index))
    }

    @Test
    fun prefersExactTokenSetOverSuperset() {
        assertEquals("Canal.24.horas.es", SpanishEpgMatcher.match("Canal 24 horas", index))
    }

    @Test
    fun returnsNullForUnknownChannel() {
        assertNull(SpanishEpgMatcher.match("Canal Desconocido XYZ", index))
    }
}
