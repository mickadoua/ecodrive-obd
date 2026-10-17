package tech.dm.conso.data

data class GeoPoint(val lat: Double, val lon: Double)

interface PositionSource {
    val altitudeM: Double?
    val location: GeoPoint?
    val hasPreciseAltitude: Boolean
    fun start() = Unit
    fun stop() = Unit
}
