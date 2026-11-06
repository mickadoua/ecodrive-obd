package tech.dm.conso.data

import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.dm.conso.fixed

class TerrainElevation(private val fetch: suspend (List<GeoPoint>) -> List<Double>? = ::openMeteo) : SynchronizedObject() {
    private val cache = HashMap<String, Double>()
    private val pending = LinkedHashMap<String, GeoPoint>()

    fun elevation(p: GeoPoint): Double? = synchronized(this) { cache[cell(p)] }

    fun request(p: GeoPoint) = synchronized(this) {
        val c = cell(p)
        if (c !in cache && c !in pending) pending[c] = p
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        var backoffMs = POLL_MS
        while (isActive) {
            delay(backoffMs)
            backoffMs = if (resolvePending()) POLL_MS else (backoffMs * 2).coerceAtMost(30_000)
        }
    }

    suspend fun resolvePending(): Boolean {
        val batch = synchronized(this) { pending.entries.take(MAX_BATCH).map { it.key to it.value } }
        if (batch.isEmpty()) return true
        val result = runCatching { fetch(batch.map { it.second }) }.getOrNull() ?: return false
        if (result.size != batch.size) return false
        synchronized(this) {
            batch.forEachIndexed { i, (c, _) ->
                cache[c] = result[i]
                pending.remove(c)
            }
        }
        return true
    }

    companion object {
        private const val POLL_MS = 2_000L
        private const val MAX_BATCH = 100

        fun cell(p: GeoPoint) = Geo.geohash(p, 8)

        suspend fun openMeteo(points: List<GeoPoint>): List<Double>? {
            val lat = points.joinToString(",") { it.lat.fixed(5) }
            val lon = points.joinToString(",") { it.lon.fixed(5) }
            val response = http.get("https://api.open-meteo.com/v1/elevation?latitude=$lat&longitude=$lon") {
                timeout { connectTimeoutMillis = 5_000; socketTimeoutMillis = 5_000 }
            }
            if (response.status != HttpStatusCode.OK) return null
            return Json.parseToJsonElement(response.bodyAsText()).jsonObject.array("elevation").map { it.jsonPrimitive.double }
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
