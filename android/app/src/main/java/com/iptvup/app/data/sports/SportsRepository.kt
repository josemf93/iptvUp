package com.iptvup.app.data.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class SportsEventRaw(
    val id: String,
    val sport: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String?,
    val awayLogo: String?,
    val startMs: Long,
    val status: String
)

private data class SportsSource(
    val path: String,
    val sport: String,
    val competition: String,
    val spanishOnly: Boolean
)

@Singleton
class SportsRepository @Inject constructor(
    private val okHttp: OkHttpClient
) {
    private val mutex = Mutex()
    private var cacheDate: String? = null
    private var cacheAt: Long = 0L
    private var cacheEvents: List<SportsEventRaw> = emptyList()

    suspend fun eventsForToday(): List<SportsEventRaw> = mutex.withLock {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        if (cacheDate == date && now - cacheAt < CACHE_MS) return cacheEvents
        val events = withContext(Dispatchers.IO) { fetchAll(date) }
        if (events != null) {
            cacheDate = date
            cacheAt = now
            cacheEvents = events
        }
        cacheEvents
    }

    private suspend fun fetchAll(date: String): List<SportsEventRaw>? = coroutineScope {
        val results = SOURCES.map { source ->
            async(Dispatchers.IO) {
                runCatching { fetchSource(source, date) }.getOrNull()
            }
        }.awaitAll()
        if (results.all { it == null }) return@coroutineScope null
        results.filterNotNull()
            .flatten()
            .distinctBy { it.id }
            .sortedBy { it.startMs }
    }

    private fun fetchSource(source: SportsSource, date: String): List<SportsEventRaw> {
        val url = "https://site.api.espn.com/apis/site/v2/sports/" +
            "${source.path}/scoreboard?dates=$date&limit=100"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVUp/1.0")
            .header("Accept", "application/json")
            .build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("ESPN HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val root = JSONObject(body)
            val array = root.optJSONArray("events") ?: return emptyList()
            val result = ArrayList<SportsEventRaw>()
            for (index in 0 until array.length()) {
                val event = array.optJSONObject(index) ?: continue
                val competition = event.optJSONArray("competitions")?.optJSONObject(0)
                    ?: continue
                val competitors = competition.optJSONArray("competitors") ?: continue
                var homeName: String? = null
                var awayName: String? = null
                var homeLogo: String? = null
                var awayLogo: String? = null
                for (c in 0 until competitors.length()) {
                    val competitor = competitors.optJSONObject(c) ?: continue
                    val side = competitor.optString("homeAway")
                    val team = competitor.optJSONObject("team") ?: continue
                    val name = team.optString("displayName")
                        .ifBlank { team.optString("shortDisplayName") }
                    if (name.isBlank()) continue
                    val logo = team.optString("logo").takeIf { it.isNotBlank() }
                        ?: team.optJSONArray("logos")?.optJSONObject(0)
                            ?.optString("href")?.takeIf { it.isNotBlank() }
                    if (side == "home") {
                        homeName = name
                        homeLogo = logo
                    } else if (side == "away") {
                        awayName = name
                        awayLogo = logo
                    }
                }
                val home = homeName ?: continue
                val away = awayName ?: continue
                if (source.spanishOnly && !isSpanishEvent(home, away)) continue

                val start = parseEspnDate(
                    event.optString("date").ifBlank { competition.optString("date") }
                ) ?: continue
                val state = competition.optJSONObject("status")
                    ?.optJSONObject("type")
                    ?.optString("state")
                    .orEmpty()
                if (state == "post") continue
                val id = event.optString("id")
                    .ifBlank { "$home-$away-$start" }
                result += SportsEventRaw(
                    id = id,
                    sport = source.sport,
                    competition = source.competition,
                    homeTeam = home,
                    awayTeam = away,
                    homeLogo = homeLogo,
                    awayLogo = awayLogo,
                    startMs = start,
                    status = state
                )
            }
            return result
        }
    }

    private fun isSpanishEvent(home: String, away: String): Boolean {
        val normalized = "${normalize(home)} ${normalize(away)}"
        return SPANISH_TOKENS.any { token -> normalized.contains(token) }
    }

    private fun normalize(value: String): String {
        val text = java.text.Normalizer.normalize(
            value.lowercase(Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
        return text.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun parseEspnDate(value: String): Long? {
        if (value.isBlank()) return null
        for (pattern in DATE_PATTERNS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                return format.parse(value)?.time
            } catch (_: Exception) {
                // Try next format.
            }
        }
        return null
    }

    private companion object {
        const val CACHE_MS = 15 * 60 * 1000L

        val SOURCES = listOf(
            SportsSource("soccer/esp.1", "Fútbol", "LaLiga", spanishOnly = false),
            SportsSource("soccer/esp.copa_del_rey", "Fútbol", "Copa del Rey", spanishOnly = false),
            SportsSource("soccer/esp.supercopa", "Fútbol", "Supercopa", spanishOnly = false),
            SportsSource("soccer/uefa.champions", "Fútbol", "Champions League", spanishOnly = true),
            SportsSource("soccer/uefa.europa", "Fútbol", "Europa League", spanishOnly = true),
            SportsSource("soccer/uefa.europa.conf", "Fútbol", "Conference League", spanishOnly = true),
            SportsSource("basketball/euroleague", "Baloncesto", "EuroLeague", spanishOnly = false),
            SportsSource("basketball/nba", "Baloncesto", "NBA", spanishOnly = false)
        )

        val SPANISH_TOKENS = setOf(
            "madrid", "barcelona", "atletico", "sevilla", "betis", "valencia",
            "villarreal", "sociedad", "athletic", "osasuna", "celta", "girona",
            "mallorca", "rayo", "getafe", "alaves", "las palmas", "espanyol",
            "leganes", "elche", "levante", "zaragoza", "malaga", "racing",
            "sporting", "deportivo", "granada", "valladolid", "tenerife",
            "huesca", "eibar", "almeria", "cadiz", "cordoba", "oviedo",
            "numancia", "mirandes", "albacete", "burgos", "santander",
            "baskonia", "unicaja", "joventut", "bilbao", "murcia", "andorra",
            "breogan", "obradoiro", "fuenlabrada", "estudiantes", "penya",
            "canaria"
        )

        val DATE_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
    }
}
