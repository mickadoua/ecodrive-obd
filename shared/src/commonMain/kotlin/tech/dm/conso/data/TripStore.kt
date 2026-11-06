package tech.dm.conso.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.double
import kotlinx.serialization.json.put
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.math.abs

data class TripSnapshot(
    val tripId: Long,
    val engineStartMs: Long?,
    val lastUpdateMs: Long,
    val tripKm: Double,
    val tripLiters: Double,
    val samples: List<Sample>,
    val startedAt: Long = tripId,
    val stats: TripStats = TripStats(),
    val finalized: Boolean = false,
) {
    fun isSameTrip(engineStart: Long?, now: Long): Boolean = when {
        engineStart != null && engineStartMs != null -> abs(engineStart - engineStartMs) < ENGINE_START_TOLERANCE_MS
        else -> now - lastUpdateMs < RESUME_WINDOW_MS
    }

    companion object {
        const val ENGINE_START_TOLERANCE_MS = 120_000L
        const val RESUME_WINDOW_MS = 5 * 60_000L
    }
}

class TripStore(private val file: TextFile) : SynchronizedObject() {
    fun load(): TripSnapshot? = runCatching {
        val o = Json.parseToJsonElement(file.readText()).jsonObject
        TripSnapshot(
            tripId = o.long("id"),
            engineStartMs = o.longOrNull("engine"),
            lastUpdateMs = o.long("t"),
            tripKm = o.double("km"),
            tripLiters = o.double("l"),
            samples = o.array("s").map { e ->
                val a = e.jsonArray
                Sample(
                    a[0].jsonPrimitive.long, a[1].jsonPrimitive.double, a.doubleOrNull(2), a.doubleOrNull(3),
                    a[4].jsonPrimitive.double, a.doubleOrNull(5),
                )
            },
            startedAt = o.longOrNull("startedAt") ?: o.long("id"),
            stats = o.optObject("stats")?.let(TripStats::fromJson) ?: TripStats(),
            finalized = o.optBoolean("finalized", false),
        )
    }.getOrNull()

    fun save(t: TripSnapshot) = synchronized(this) {
        val samples = JsonArray(t.samples.map { s ->
            JsonArray(listOf(s.wallMs.json(), s.speedKmh.json(), s.litersPerHour.json(), s.altitudeM.json(), s.grade.json(), s.ecoScore.json()))
        })
        val o = buildJsonObject {
            put("id", t.tripId)
            put("engine", t.engineStartMs.json())
            put("t", t.lastUpdateMs)
            put("km", t.tripKm)
            put("l", t.tripLiters)
            put("s", samples)
            put("startedAt", t.startedAt)
            put("stats", t.stats.toJson())
            put("finalized", t.finalized)
        }
        file.replaceText(o.toString())
    }
}
