package tech.dm.conso.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class AheadKind(val label: String, val targetKmh: Int?) {
    LIMIT("limitation", null),
    STOP("stop", 0),
    SIGNAL("feu", 30),
    GIVE_WAY("cédez-le-passage", 20),
    ROUNDABOUT("rond-point", 30),
}

data class Ahead(val kind: AheadKind, val kmh: Int, val distanceM: Double)

data class LimitWay(val kmh: Int, val points: List<GeoPoint>)

data class RoadFeature(val kind: AheadKind, val points: List<GeoPoint>)

class RoadArea(val center: GeoPoint, val ways: List<LimitWay>, val features: List<RoadFeature>) {
    fun limitAt(p: GeoPoint): Int? =
        ways.map { it to distanceToWay(p, it.points) }
            .filter { it.second < MATCH_M }
            .minByOrNull { it.second }?.first?.kmh

    fun ahead(p: GeoPoint, bearingDeg: Double, currentLimit: Int?, horizonM: Double): List<Ahead> {
        val events = mutableListOf<Ahead>()
        for (f in features) {
            val d = f.points.map { forwardLateral(p, it, bearingDeg) }
                .filter { (fwd, lat) -> fwd > MIN_AHEAD_M && fwd <= horizonM && abs(lat) < CORRIDOR_M }
                .minOfOrNull { it.first } ?: continue
            events += Ahead(f.kind, f.kind.targetKmh ?: continue, d)
        }
        val previous = currentLimit
        var candidate: Int? = null
        var d = STEP_M
        while (d <= horizonM) {
            val kmh = limitAt(Geo.offset(p, d, bearingDeg))
            if (kmh != null && kmh != previous) {
                if (kmh == candidate) {
                    events += Ahead(AheadKind.LIMIT, kmh, d - STEP_M)
                    break
                }
                candidate = kmh
            } else candidate = null
            d += STEP_M
        }
        return events.sortedBy { it.distanceM }
    }

    companion object {
        private const val MATCH_M = 20.0
        private const val CORRIDOR_M = 30.0
        private const val MIN_AHEAD_M = 5.0
        private const val STEP_M = 20.0

        fun forwardLateral(origin: GeoPoint, q: GeoPoint, bearingDeg: Double): Pair<Double, Double> {
            val (x, y) = local(origin, q)
            val b = Math.toRadians(bearingDeg)
            return (x * sin(b) + y * cos(b)) to (x * cos(b) - y * sin(b))
        }

        private fun local(origin: GeoPoint, q: GeoPoint): Pair<Double, Double> =
            (q.lon - origin.lon) * 111_320.0 * cos(Math.toRadians(origin.lat)) to (q.lat - origin.lat) * 111_320.0

        fun distanceToWay(p: GeoPoint, points: List<GeoPoint>): Double {
            if (points.size == 1) return Geo.distanceM(p, points[0])
            return points.zipWithNext().minOf { (a, b) ->
                val (ax, ay) = local(p, a)
                val (bx, by) = local(p, b)
                val dx = bx - ax
                val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val cx = ax + t * dx
                val cy = ay + t * dy
                sqrt(cx * cx + cy * cy)
            }
        }
    }
}
