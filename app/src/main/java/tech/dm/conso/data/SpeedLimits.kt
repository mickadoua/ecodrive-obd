package tech.dm.conso.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SpeedLimits(private val fetch: (GeoPoint) -> RoadArea = ::overpass) {
    private var inFlight: Job? = null
    private var lastQueryAt = 0L

    @Volatile private var area: RoadArea? = null
    @Volatile private var last: Pair<GeoPoint, Int>? = null

    fun limitAt(p: GeoPoint?): Int? {
        if (p == null) return null
        area?.limitAt(p)?.let { last = p to it; return it }
        return last?.takeIf { Geo.distanceM(it.first, p) < CARRY_OVER_M }?.second
    }

    fun ahead(p: GeoPoint?, bearingDeg: Double?, currentLimit: Int?, horizonM: Double): List<Ahead> {
        if (p == null || bearingDeg == null) return emptyList()
        return area?.takeIf { Geo.distanceM(it.center, p) + horizonM < RADIUS_M }
            ?.ahead(p, bearingDeg, currentLimit, horizonM).orEmpty()
    }

    fun prefetch(scope: CoroutineScope, p: GeoPoint?, nowMs: Long) {
        if (p == null || inFlight?.isActive == true || nowMs - lastQueryAt < MIN_INTERVAL_MS) return
        if (area?.let { Geo.distanceM(it.center, p) < REFETCH_M } == true) return
        lastQueryAt = nowMs
        inFlight = scope.launch(Dispatchers.IO) {
            runCatching { fetch(p) }.onSuccess { area = it }
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 10_000L
        private const val CARRY_OVER_M = 400.0
        const val RADIUS_M = 1000.0
        private const val REFETCH_M = 300.0
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

        fun parseArea(center: GeoPoint, json: String): RoadArea {
            val elements = JSONObject(json).getJSONArray("elements")
            val ways = mutableListOf<LimitWay>()
            val features = mutableListOf<RoadFeature>()
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: continue
                if (e.getString("type") == "node") {
                    val kind = when (tags.optString("highway")) {
                        "stop" -> AheadKind.STOP
                        "traffic_signals" -> AheadKind.SIGNAL
                        "give_way" -> AheadKind.GIVE_WAY
                        else -> continue
                    }
                    features += RoadFeature(kind, listOf(GeoPoint(e.getDouble("lat"), e.getDouble("lon"))))
                    continue
                }
                val geometry = e.optJSONArray("geometry") ?: continue
                val points = List(geometry.length()) { geometry.getJSONObject(it).let { g -> GeoPoint(g.getDouble("lat"), g.getDouble("lon")) } }
                parseMaxspeed(tags.optString("maxspeed").ifBlank { null })?.let { ways += LimitWay(it, points) }
                if (tags.optString("junction") == "roundabout") features += RoadFeature(AheadKind.ROUNDABOUT, points)
            }
            return RoadArea(center, ways, features)
        }

        fun overpass(p: GeoPoint): RoadArea {
            val at = "${RADIUS_M.toInt()},${"%.6f".format(java.util.Locale.US, p.lat)},${"%.6f".format(java.util.Locale.US, p.lon)}"
            val query = "[out:json][timeout:15];(way(around:$at)[highway][maxspeed];way(around:$at)[junction=roundabout];" +
                "node(around:$at)[highway~\"^(stop|traffic_signals|give_way)$\"];);out geom;"
            for (endpoint in ENDPOINTS) {
                val conn = URL("$endpoint?data=${URLEncoder.encode(query, "UTF-8")}").openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", "EcoDriveOBD/0.1 (+https://github.com/mickadoua/ecodrive-obd)")
                try {
                    if (conn.responseCode != 200) continue
                    return parseArea(p, conn.inputStream.bufferedReader().readText())
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
