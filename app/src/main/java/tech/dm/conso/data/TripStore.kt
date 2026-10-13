package tech.dm.conso.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

data class TripSnapshot(
    val tripId: Long,
    val engineStartMs: Long?,
    val lastUpdateMs: Long,
    val tripKm: Double,
    val tripLiters: Double,
    val samples: List<Sample>,
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

class TripStore(private val file: File) {
    fun load(): TripSnapshot? = runCatching {
        val o = JSONObject(file.readText())
        val arr = o.getJSONArray("s")
        TripSnapshot(
            tripId = o.getLong("id"),
            engineStartMs = if (o.isNull("engine")) null else o.getLong("engine"),
            lastUpdateMs = o.getLong("t"),
            tripKm = o.getDouble("km"),
            tripLiters = o.getDouble("l"),
            samples = List(arr.length()) { i ->
                val a = arr.getJSONArray(i)
                Sample(
                    a.getLong(0), a.getDouble(1), a.optNullable(2), a.optNullable(3), a.getDouble(4), a.optNullable(5),
                )
            },
        )
    }.getOrNull()

    @Synchronized
    fun save(t: TripSnapshot) {
        val arr = JSONArray()
        t.samples.forEach { s ->
            arr.put(
                JSONArray().put(s.wallMs).put(s.speedKmh).put(s.litersPerHour ?: JSONObject.NULL)
                    .put(s.altitudeM ?: JSONObject.NULL).put(s.grade).put(s.ecoScore ?: JSONObject.NULL)
            )
        }
        val o = JSONObject()
            .put("id", t.tripId)
            .put("engine", t.engineStartMs ?: JSONObject.NULL)
            .put("t", t.lastUpdateMs)
            .put("km", t.tripKm)
            .put("l", t.tripLiters)
            .put("s", arr)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    private fun JSONArray.optNullable(i: Int): Double? = if (isNull(i)) null else getDouble(i)
}
