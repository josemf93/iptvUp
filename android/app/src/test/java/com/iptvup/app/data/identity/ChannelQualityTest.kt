package com.iptvup.app.data.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelQualityTest {

    @Test
    fun detectsCommonQualityTags() {
        assertEquals("4K", ChannelQuality.label("Canal 1 4K"))
        assertEquals("4K", ChannelQuality.label("Canal 1 UHD"))
        assertEquals("4K", ChannelQuality.label("Canal 1 2160p"))
        assertEquals("FHD", ChannelQuality.label("Canal 1 FHD"))
        assertEquals("FHD", ChannelQuality.label("Canal 1 1080p"))
        assertEquals("HD", ChannelQuality.label("Canal 1 HD"))
        assertEquals("HD", ChannelQuality.label("Canal 1 720p"))
        assertEquals("SD", ChannelQuality.label("Canal 1 SD"))
        assertNull(ChannelQuality.label("Canal 1"))
    }

    @Test
    fun detectsQualityGluedToNameAndInvisibleChars() {
        assertEquals("HD", ChannelQuality.label("ES: MOVISTAR COMEDIA hd"))
        assertEquals("HD", ChannelQuality.label("ES: MOVISTAR COMEDIAHD"))
        assertEquals("HD", ChannelQuality.label("ES: MOVISTAR COMEDIA\u200BHD"))
        assertEquals("FHD", ChannelQuality.label("ES: MOVISTAR COMEDIAFHD"))
        assertEquals("HD", ChannelQuality.label("ES: MOVISTAR COMEDIA ＨＤ"))
    }

    @Test
    fun treatsRawFeedAsSd() {
        assertEquals("SD", ChannelQuality.label("ES: MOVISTAR LALIGA RAW"))
    }

    @Test
    fun removesQualityFromBaseName() {
        assertEquals("Canal 1", ChannelQuality.baseName("Canal 1 HD"))
        assertEquals("Canal 1", ChannelQuality.baseName("Canal 1 4K UHD"))
        assertEquals("Canal 1", ChannelQuality.baseName("Canal 1 1080p"))
        assertEquals("Canal 1", ChannelQuality.baseName("Canal 1 FHD HEVC"))
    }
}
