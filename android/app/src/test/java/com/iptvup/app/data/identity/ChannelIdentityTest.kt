package com.iptvup.app.data.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelIdentityTest {

    @Test
    fun sameChannelAndQualityProducesSameIdentity() {
        val first = ChannelIdentity.dedupeKey("Canal Uno HD", "Noticias", "c1")
        val second = ChannelIdentity.dedupeKey("Canal Uno HD", "Noticias", "c1")

        assertEquals(first, second)
        assertEquals(
            ChannelIdentity.channelKey(first),
            ChannelIdentity.channelKey(second)
        )
    }

    @Test
    fun differentQualitiesStaySeparate() {
        val hd = ChannelIdentity.dedupeKey("Canal Uno HD", "Noticias", "c1")
        val fhd = ChannelIdentity.dedupeKey("Canal Uno FHD", "Noticias", "c1")
        val uhd = ChannelIdentity.dedupeKey("Canal Uno 4K", "Noticias", "c1")
        val sd = ChannelIdentity.dedupeKey("Canal Uno SD", "Noticias", "c1")

        assertNotEquals(hd, fhd)
        assertNotEquals(hd, uhd)
        assertNotEquals(fhd, uhd)
        assertNotEquals(hd, sd)
    }

    @Test
    fun sameQualityWithDifferentNotationDeduplicates() {
        val hd = ChannelIdentity.dedupeKey("Canal Dos HD", "Deportes", null)
        val hd720 = ChannelIdentity.dedupeKey("Canal Dos 720p", "Deportes", null)

        assertEquals(hd, hd720)
    }

    @Test
    fun sameNameInDifferentGroupsStaysSeparate() {
        val news = ChannelIdentity.dedupeKey("Canal 1 HD", "Noticias", null)
        val sports = ChannelIdentity.dedupeKey("Canal 1 HD", "Deportes", null)

        assertNotEquals(news, sports)
    }

    @Test
    fun qualityScorePrefersHdAndEpg() {
        val hd = com.iptvup.app.data.model.ParsedChannel(
            channelKey = "ch:hd",
            dedupeKey = "name:canal 1|deportes|q:HD",
            name = "Canal 1 HD",
            logoUrl = null,
            groupName = "Deportes",
            streamUrl = "https://example.com/1.m3u8",
            epgChannelId = "c1"
        )
        val sd = hd.copy(
            channelKey = "ch:sd",
            dedupeKey = "name:canal 1|deportes|q:SD",
            name = "Canal 1 SD",
            streamUrl = "http://example.com/1.ts",
            epgChannelId = null
        )

        assertTrue(ChannelIdentity.qualityScore(hd) > ChannelIdentity.qualityScore(sd))
    }
}
