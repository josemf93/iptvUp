package com.iptvup.app.data.parser

import com.iptvup.app.data.model.ParsedProgram
import org.junit.Assert.assertEquals
import org.junit.Test

class XmltvParserTest {

    @Test
    fun parsesProgrammesAndFiltersByChannel() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="c1">
                <title lang="es">Noticias</title>
                <desc>Resumen del día</desc>
              </programme>
              <programme start="20261102130000 +0000" stop="20261102140000 +0000" channel="c2">
                <title>Otro canal</title>
              </programme>
            </tv>
        """.trimIndent()

        val from = XmltvParser.parseXmltvTime("20261102120000 +0000")!!
        val to = XmltvParser.parseXmltvTime("20261102140000 +0000")!!
        val programs = mutableListOf<ParsedProgram>()

        val count = XmltvParser().parse(
            input = xml.byteInputStream(),
            fromMs = from,
            toMs = to,
            allowedEpgIds = setOf("c1")
        ) { programs += it }

        assertEquals(1, count)
        assertEquals("Noticias", programs[0].title)
        assertEquals("Resumen del día", programs[0].description)
        assertEquals("c1", programs[0].epgChannelId)
    }

    @Test
    fun parsesXmltvDates() {
        assertEquals(
            1793620800000L,
            XmltvParser.parseXmltvTime("20261102120000 +0000")
        )
    }
}
