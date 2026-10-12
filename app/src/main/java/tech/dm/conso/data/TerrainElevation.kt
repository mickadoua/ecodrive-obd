package tech.dm.conso.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class TerrainElevation(private val fetch: (List<GeoPoint>) -> List<Double>? = ::openMeteo) {
    private val cache = ConcurrentHashMap<String, Double>()
    private val pending = ConcurrentHashMap<String, GeoPoint>()

    fun elevation(p: GeoPoint): Double? = cache[cell(p)]

    fun request(p: GeoPoint) {
        val c = cell(p)
        if (!cache.containsKey(c)) pending.putIfAbsent(c, p)
    }

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        var backoffMs = POLL_MS
        while (isActive) {
            delay(backoffMs)
            backoffMs = if (resolvePending()) POLL_MS else (backoffMs * 2).coerceAtMost(30_000)
        }
    }

    fun resolvePending(): Boolean {
        val batch = pending.entries.take(MAX_BATCH)
        if (batch.isEmpty()) return true
        val result = runCatching { fetch(batch.map { it.value }) }.getOrNull() ?: return false
        if (result.size != batch.size) return false
        batch.forEachIndexed { i, (c, _) ->
            cache[c] = result[i]
            pending.remove(c)
        }
        return true
    }

    companion object {
        private const val POLL_MS = 2_000L
        private const val MAX_BATCH = 100

        fun cell(p: GeoPoint) = Geo.geohash(p, 8)

        fun openMeteo(points: List<GeoPoint>): List<Double>? {
            val lat = points.joinToString(",") { "%.5f".format(java.util.Locale.US, it.lat) }
            val lon = points.joinToString(",") { "%.5f".format(java.util.Locale.US, it.lon) }
            val conn = URL("https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lon").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            return try {
                if (conn.responseCode != 200) return null
                val arr = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("elevation")
                List(arr.length()) { arr.getDouble(it) }
            } finally {
                conn.disconnect()
            }
        }
    }
}

class TerrainGrade(private val terrain: TerrainElevation, private val windowM: Double = 100.0) {
    private val track = ArrayDeque<Pair<Double, GeoPoint>>()

    fun update(distanceM: Double, position: GeoPoint?): Pair<Double?, Double> {
        if (position != null && (track.isEmpty() || distanceM - track.last().first >= 5.0)) {
            track.addLast(distanceM to position)
            terrain.request(position)
        }
        while (track.size > 2 && distanceM - track.first().first > windowM * 4) track.removeFirst()

        val latestIdx = track.indices.lastOrNull { terrain.elevation(track[it].second) != null } ?: return null to 0.0
        val (dLatest, pLatest) = track[latestIdx]
        val eLatest = terrain.elevation(pLatest)!!
        val oldIdx = (0 until latestIdx).lastOrNull { i ->
            dLatest - track[i].first >= windowM && terrain.elevation(track[i].second) != null
        } ?: return eLatest to 0.0
        val (dOld, pOld) = track[oldIdx]
        val grade = (eLatest - terrain.elevation(pOld)!!) / (dLatest - dOld)
        return eLatest to grade.coerceIn(-0.2, 0.2)
    }
}
