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
import org.json.JSONArray
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
    val status: String,
    val broadcasters: List<String>
)

private data class SportsSource(
    val path: String,
    val sport: String,
    val competition: String,
    val spanishOnly: Boolean = false,
    val individual: Boolean = false
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
            val events = root.optJSONArray("events") ?: return emptyList()
            val result = ArrayList<SportsEventRaw>()
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                val competitions = competitionsOf(event)
                for (competition in competitions) {
                    parseCompetition(event, competition, source)?.let { result += it }
                }
            }
            return result
        }
    }

    private fun competitionsOf(event: JSONObject): List<JSONObject> {
        event.optJSONArray("competitions")?.let { return it.objects() }
        val groupings = event.optJSONArray("groupings") ?: return emptyList()
        return groupings.objects().flatMap { grouping ->
            grouping.optJSONArray("competitions")?.objects().orEmpty()
        }
    }

    private fun parseCompetition(
        event: JSONObject,
        competition: JSONObject,
        source: SportsSource
    ): SportsEventRaw? {
        val state = competition.optJSONObject("status")
            ?.optJSONObject("type")
            ?.optString("state")
            .orEmpty()
        if (state == "post") return null

        val start = parseEspnDate(
            competition.optString("date").ifBlank { event.optString("date") }
        ) ?: return null

        val sides = if (source.individual) {
            individualSides(competition)
        } else {
            teamSides(competition)
        } ?: return null
        val (home, homeLogo, away, awayLogo) = sides

        if (source.spanishOnly && !isSpanishEvent(home, away)) return null

        val id = competition.optString("id")
            .ifBlank { event.optString("id") }
            .ifBlank { "$home-$away-$start" }
        val competitionName = if (source.individual) {
            event.optString("name").ifBlank { source.competition }
        } else {
            source.competition
        }
        return SportsEventRaw(
            id = "${source.path}:$id",
            sport = source.sport,
            competition = competitionName,
            homeTeam = home,
            awayTeam = away,
            homeLogo = homeLogo,
            awayLogo = awayLogo,
            startMs = start,
            status = state,
            broadcasters = broadcastersOf(competition)
        )
    }

    private data class Sides(
        val home: String,
        val homeLogo: String?,
        val away: String,
        val awayLogo: String?
    )

    private fun teamSides(competition: JSONObject): Sides? {
        val competitors = competition.optJSONArray("competitors") ?: return null
        var home: Pair<String, String?>? = null
        var away: Pair<String, String?>? = null
        for (competitor in competitors.objects()) {
            val team = competitor.optJSONObject("team") ?: continue
            val name = team.optString("displayName")
                .ifBlank { team.optString("shortDisplayName") }
            if (name.isBlank()) continue
            val logo = team.optString("logo").takeIf { it.isNotBlank() }
                ?: team.optJSONArray("logos")?.optJSONObject(0)
                    ?.optString("href")?.takeIf { it.isNotBlank() }
            when (competitor.optString("homeAway")) {
                "home" -> home = name to logo
                "away" -> away = name to logo
            }
        }
        val h = home ?: return null
        val a = away ?: return null
        return Sides(h.first, h.second, a.first, a.second)
    }

    private fun individualSides(competition: JSONObject): Sides? {
        val competitors = competition.optJSONArray("competitors") ?: return null
        val players = competitors.objects()
            .sortedBy { it.optInt("order", 0) }
            .mapNotNull { competitor ->
                val athlete = competitor.optJSONObject("athlete") ?: return@mapNotNull null
                val name = athlete.optString("displayName")
                    .ifBlank { athlete.optString("shortName") }
                if (name.isBlank()) return@mapNotNull null
                name to athlete.optJSONObject("flag")?.optString("href")?.takeIf { it.isNotBlank() }
            }
        if (players.size < 2) return null
        return Sides(players[0].first, players[0].second, players[1].first, players[1].second)
    }

    private fun broadcastersOf(competition: JSONObject): List<String> {
        val names = LinkedHashSet<String>()
        competition.optJSONArray("broadcasts")?.objects()?.forEach { broadcast ->
            broadcast.optJSONArray("names")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(names::add)
                }
            }
        }
        if (names.isEmpty()) {
            competition.optJSONArray("geoBroadcasts")?.objects()?.forEach { geo ->
                geo.optJSONObject("media")?.optString("shortName")
                    ?.takeIf { it.isNotBlank() }?.let(names::add)
            }
        }
        return names.toList()
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun isSpanishEvent(home: String, away: String): Boolean {
        val normalized = "${normalize(home)} ${normalize(away)}"
        return SPANISH_TOKENS.any { token -> normalized.contains(token) }
    }

    private fun normalize(value: String): String {
        val text = java.text.Normalizer.normalize(
            value.lowercase(Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replace(DIACRITICS, "")
        return text.replace(NON_ALPHANUMERIC, " ").trim()
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
        val DIACRITICS = Regex("\\p{M}+")
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

        val SOURCES = listOf(
            SportsSource("soccer/esp.1", "Fútbol", "LaLiga"),
            SportsSource("soccer/esp.copa_del_rey", "Fútbol", "Copa del Rey"),
            SportsSource("soccer/esp.supercopa", "Fútbol", "Supercopa"),
            SportsSource("soccer/uefa.champions", "Fútbol", "Champions League", spanishOnly = true),
            SportsSource("soccer/uefa.europa", "Fútbol", "Europa League", spanishOnly = true),
            SportsSource("soccer/uefa.europa.conf", "Fútbol", "Conference League", spanishOnly = true),
            SportsSource("basketball/nba", "Baloncesto", "NBA"),
            SportsSource("tennis/atp", "Tenis", "ATP", individual = true),
            SportsSource("tennis/wta", "Tenis", "WTA", individual = true)
        )

        val SPANISH_TOKENS = setOf(
            "madrid", "barcelona", "atletico", "sevilla", "betis", "valencia",
            "villarreal", "sociedad", "athletic", "osasuna", "celta", "girona",
            "mallorca", "rayo", "getafe", "alaves", "las palmas", "espanyol",
            "leganes", "elche", "levante", "zaragoza", "malaga", "racing",
            "sporting", "deportivo", "granada", "valladolid", "tenerife",
            "huesca", "eibar", "almeria", "cadiz", "cordoba", "oviedo",
            "numancia", "mirandes", "albacete", "burgos", "santander"
        )

        val DATE_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
    }
}
