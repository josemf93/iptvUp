package com.iptvup.app.data.sports

import com.iptvup.app.data.epg.SpanishEpgMatcher
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.local.ChannelEntity

/** Partido con los canales de tu lista (de grupos visibles) que lo emiten. */
data class SportsEventMatch(
    val event: SportsEventRaw,
    val channels: List<ChannelEntity>
)

object BroadcasterMatcher {

    /**
     * Devuelve los canales visibles que coinciden con alguna cadena de emisión del partido.
     * Un canal coincide si contiene todas las palabras significativas de la cadena
     * (por ejemplo "DAZN LaLiga" encaja con "ES DAZN LALIGA FHD").
     * Ordenados de mejor a peor calidad.
     */
    fun matchingChannels(broadcasters: List<String>, channels: List<ChannelEntity>): List<ChannelEntity> {
        val wanted = broadcasters
            .map { SpanishEpgMatcher.tokens(it) }
            .filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return emptyList()
        return channels
            .filter { channel ->
                val channelTokens = SpanishEpgMatcher.tokens(channel.name)
                wanted.any { tokens -> tokens.all { it in channelTokens } }
            }
            .sortedByDescending { ChannelQuality.sportsRank(it.name) }
    }
}
