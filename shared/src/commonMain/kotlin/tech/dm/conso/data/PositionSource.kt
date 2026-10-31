package tech.dm.conso.data

data class GeoPoint(val lat: Double, val lon: Double)

interface PositionSource {
    val altitudeM: Double?
    val location: GeoPoint?
    val hasPreciseAltitude: Boolean
    fun start() = Unit
    fun stop() = Unit
}

class GradeEstimator(private val windowM: Double = 60.0) {
    private val points = ArrayDeque<Pair<Double, Double>>()

    fun update(distanceM: Double, altitudeM: Double?): Double {
        if (altitudeM == null) return 0.0
        points.addLast(distanceM to altitudeM)
        while (points.size > 2 && distanceM - points[1].first >= windowM) points.removeFirst()
        val (d0, a0) = points.first()
        val span = distanceM - d0
        return if (span < windowM / 2) 0.0 else ((altitudeM - a0) / span).coerceIn(-0.2, 0.2)
    }
}
