package com.iptvup.app.data.parser

import com.iptvup.app.data.model.ParsedChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesChannelsAttributesAndEpgUrl() = runBlocking {
        val playlist = """
            #EXTM3U url-tvg="https://example.com/epg.xml"
            #EXTINF:-1 tvg-id="c1" tvg-name="Uno" tvg-logo="https://example.com/1.png" group-title="Noticias",Canal Uno
            https://example.com/stream/1.m3u8
            #EXTGRP:Deportes
            #EXTINF:-1 tvg-id="c2",Canal Dos, HD
            https://example.com/stream/2.m3u8
        """.trimIndent()

        val channels = mutableListOf<ParsedChannel>()
        val result = M3uParser().parse(playlist.reader().buffered()) { channels += it }

        assertEquals(2, result.count)
        assertEquals("https://example.com/epg.xml", result.epgUrl)
        assertEquals("Canal Uno", channels[0].name)
        assertEquals("Noticias", channels[0].groupName)
        assertEquals("c1", channels[0].epgChannelId)
        assertEquals("Deportes", channels[1].groupName)
        assertEquals("Canal Dos, HD", channels[1].name)
        assertTrue(channels[0].channelKey.startsWith("ch:"))
        assertTrue(channels[0].channelKey != channels[1].channelKey)
    }
}
