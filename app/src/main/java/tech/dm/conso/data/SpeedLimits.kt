package tech.dm.conso.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class SpeedLimits(private val fetch: (GeoPoint) -> Int? = ::overpass) {
    private val cache = ConcurrentHashMap<String, Int>()
    private val unknown = ConcurrentHashMap.newKeySet<String>()
    private var inFlight: Job? = null
    private var lastQueryAt = 0L

    @Volatile private var last: Pair<GeoPoint, Int>? = null

    fun limitAt(p: GeoPoint?): Int? {
        if (p == null) return null
        cache[Geo.geohash(p, 7)]?.let { last = p to it; return it }
        return last?.takeIf { Geo.distanceM(it.first, p) < CARRY_OVER_M }?.second
    }

    fun prefetch(scope: CoroutineScope, p: GeoPoint?, nowMs: Long) {
        if (p == null || inFlight?.isActive == true || nowMs - lastQueryAt < MIN_INTERVAL_MS) return
        val cell = Geo.geohash(p, 7)
        if (cache.containsKey(cell) || cell in unknown) return
        lastQueryAt = nowMs
        inFlight = scope.launch(Dispatchers.IO) {
            val result = runCatching { fetch(p) }
            if (result.isFailure) return@launch
            val limit = result.getOrNull()
            if (limit != null) cache[cell] = limit else unknown += cell
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 10_000L
        private const val CARRY_OVER_M = 400.0
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
        )

        fun parseMaxspeed(raw: String?): Int? {
            val v = raw?.trim()?.lowercase() ?: return null
            v.substringBefore(' ').toIntOrNull()?.let { return if ("mph" in v) (it * 1.609).toInt() else it }
            return when {
                v.endsWith(":urban") -> 50
                v.endsWith(":rural") -> if (v.startsWith("fr")) 80 else 90
                v.endsWith(":motorway") -> if (v.startsWith("es")) 120 else 130
                v.endsWith(":trunk") || v.endsWith(":expressway") -> 110
                v.endsWith(":living_street") -> 20
                v.endsWith(":zone30") || v.endsWith(":zone:30") -> 30
                else -> null
            }
        }

        fun overpass(p: GeoPoint): Int? {
            val query = "[out:json][timeout:8];way(around:25,${"%.6f".format(java.util.Locale.US, p.lat)},${"%.6f".format(java.util.Locale.US, p.lon)})[highway][maxspeed];out tags 1;"
            for (endpoint in ENDPOINTS) {
                val conn = URL("$endpoint?data=${URLEncoder.encode(query, "UTF-8")}").openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "EcoDriveOBD/0.1 (+https://github.com/mickadoua/ecodrive-obd)")
                try {
                    if (conn.responseCode != 200) continue
                    val elements = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("elements")
                    if (elements.length() == 0) return null
                    return parseMaxspeed(elements.getJSONObject(0).getJSONObject("tags").optString("maxspeed"))
                } catch (e: Exception) {
                    continue
                } finally {
                    conn.disconnect()
                }
            }
            throw java.io.IOException("Overpass indisponible")
        }
    }
}
