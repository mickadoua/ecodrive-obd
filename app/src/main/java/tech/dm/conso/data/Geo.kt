package tech.dm.conso.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun geohash(p: GeoPoint, precision: Int = 7): String {
        var latLo = -90.0; var latHi = 90.0
        var lonLo = -180.0; var lonHi = 180.0
        val out = StringBuilder()
        var even = true
        var bit = 0
        var ch = 0
        while (out.length < precision) {
            if (even) {
                val mid = (lonLo + lonHi) / 2
                if (p.lon >= mid) { ch = ch or (16 shr bit); lonLo = mid } else lonHi = mid
            } else {
                val mid = (latLo + latHi) / 2
                if (p.lat >= mid) { ch = ch or (16 shr bit); latLo = mid } else latHi = mid
            }
            even = !even
            if (bit < 4) bit++ else { out.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return out.toString()
    }

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val la = Math.toRadians(a.lat)
        val lb = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lb)
        val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun offset(p: GeoPoint, distanceM: Double, bearingDeg: Double): GeoPoint {
        val b = Math.toRadians(bearingDeg)
        return GeoPoint(
            p.lat + distanceM * cos(b) / 111_320.0,
            p.lon + distanceM * sin(b) / (111_320.0 * cos(Math.toRadians(p.lat))),
        )
    }
}
