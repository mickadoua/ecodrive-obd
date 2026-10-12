package tech.dm.conso.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Segment(
    val key: String,
    val tripId: Long,
    val startedAt: Long,
    val points: List<GeoPoint>,
    val distanceKm: Double,
    val fuelL: Double,
    val expectedFuelL: Double,
    val avgGrade: Double,
    val avgSpeedKmh: Double,
) {
    val l100: Double get() = fuelL / distanceKm * 100
    val expectedL100: Double get() = expectedFuelL / distanceKm * 100
    val surplusL100: Double get() = l100 - expectedL100
    val excessRatio: Double get() = l100 / expectedL100.coerceAtLeast(MIN_EXPECTED_L100)

    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("trip", tripId).put("t", startedAt)
        .put("d", distanceKm).put("f", fuelL).put("e", expectedFuelL)
        .put("g", avgGrade).put("v", avgSpeedKmh)
        .put("p", JSONArray().apply { points.forEach { put(it.lat); put(it.lon) } })

    companion object {
        const val MIN_EXPECTED_L100 = 4.0

        fun fromJson(o: JSONObject): Segment {
            val p = o.getJSONArray("p")
            return Segment(
                o.getString("key"), o.getLong("trip"), o.getLong("t"),
                (0 until p.length() step 2).map { GeoPoint(p.getDouble(it), p.getDouble(it + 1)) },
                o.getDouble("d"), o.getDouble("f"), o.getDouble("e"), o.getDouble("g"), o.getDouble("v"),
            )
        }
    }
}

class SegmentBuilder(private val tripId: Long, private val onSegment: (Segment) -> Unit) {
    private var cell: String? = null
    private val points = mutableListOf<GeoPoint>()
    private var startedAt = 0L
    private var lastWall = 0L
    private var distanceKm = 0.0
    private var fuelL = 0.0
    private var expectedL = 0.0
    private var gradeWeighted = 0.0

    fun add(wallMs: Long, position: GeoPoint?, stepKm: Double, fuelStepL: Double, expectedStepL: Double, grade: Double) {
        if (position == null) return
        val c = Geo.geohash(position, CELL_PRECISION)
        if (c != cell) {
            if (points.isNotEmpty()) points += position
            flush()
            cell = c
            startedAt = wallMs
        }
        lastWall = wallMs
        if (points.isEmpty() || Geo.distanceM(points.last(), position) > 10) points += position
        distanceKm += stepKm
        fuelL += fuelStepL
        expectedL += expectedStepL
        gradeWeighted += grade * stepKm
    }

    fun flush() {
        val c = cell
        val pathM = points.zipWithNext { a, b -> Geo.distanceM(a, b) }.sum()
        if (c != null && points.size >= 2 && pathM >= MIN_SEGMENT_M && distanceKm > 0.03) {
            val heading = ((Geo.bearingDeg(points.first(), points.last()) + 22.5) / 45).toInt() % 8
            onSegment(
                Segment(
                    key = "$c-$heading",
                    tripId = tripId,
                    startedAt = startedAt,
                    points = points.toList(),
                    distanceKm = distanceKm,
                    fuelL = fuelL,
                    expectedFuelL = expectedL,
                    avgGrade = gradeWeighted / distanceKm,
                    avgSpeedKmh = distanceKm / ((lastWall - startedAt).coerceAtLeast(1_000) / 3_600_000.0),
                )
            )
        }
        points.clear()
        distanceKm = 0.0; fuelL = 0.0; expectedL = 0.0; gradeWeighted = 0.0
        cell = null
    }

    companion object {
        const val CELL_PRECISION = 7
        const val MIN_SEGMENT_M = 40.0
    }
}

class SegmentStore(private val file: File) {
    private val segments = mutableListOf<Segment>()

    init {
        runCatching {
            file.forEachLine { line -> if (line.isNotBlank()) segments += Segment.fromJson(JSONObject(line)) }
        }
    }

    @Synchronized
    fun append(segment: Segment) {
        segments += segment
        file.appendText(segment.toJson().toString() + "\n")
    }

    @Synchronized
    fun all(): List<Segment> = segments.toList()
}
