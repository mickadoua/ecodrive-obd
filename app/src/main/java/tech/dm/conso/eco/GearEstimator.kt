package tech.dm.conso.eco

import tech.dm.conso.obd.FuelType
import kotlin.math.abs

class GearEstimator(initialRatios: List<Double> = emptyList(), private val fuel: FuelType = FuelType.GASOLINE) {
    private data class Cluster(var ratio: Double, var hits: Int)

    private val clusters = initialRatios.map { Cluster(it, CONFIRMED_HITS) }.toMutableList()

    val learnedRatios: List<Double> get() = confirmed().map { it.ratio }

    fun update(speedKmh: Double, rpm: Double?, accelMs2: Double): GearAdvice? {
        if (rpm == null || rpm < 900 || speedKmh < 8) return null
        val ratio = speedKmh / rpm * 1000
        if (abs(accelMs2) < 1.0) learn(ratio)
        val gears = confirmed()
        val current = gears.indexOfFirst { abs(it.ratio - ratio) / it.ratio < TOLERANCE }
        if (current < 0) return null
        val gear = current + 1
        val upshiftRpm = if (fuel == FuelType.DIESEL) 2000.0 else 2500.0
        val minRpm = if (fuel == FuelType.DIESEL) 1150.0 else 1350.0
        val next = gears.getOrNull(current + 1)
        if (rpm > upshiftRpm && accelMs2 < 1.5 && next != null && speedKmh / next.ratio * 1000 >= minRpm) {
            var target = current + 1
            while (gears.getOrNull(target + 1)?.let { speedKmh / it.ratio * 1000 >= minRpm + 300 } == true) target++
            return GearAdvice(gear, target + 1)
        }
        if (rpm < minRpm - 200 && accelMs2 > 0.2 && current > 0) return GearAdvice(gear, gear - 1)
        return GearAdvice(gear, gear)
    }

    private fun learn(ratio: Double) {
        val match = clusters.minByOrNull { abs(it.ratio - ratio) / it.ratio }
        if (match != null && abs(match.ratio - ratio) / match.ratio < TOLERANCE) {
            match.ratio += (ratio - match.ratio) * 0.02
            match.hits++
        } else {
            clusters += Cluster(ratio, 1)
        }
        clusters.removeAll { it.hits < CONFIRMED_HITS && clusters.size > MAX_CLUSTERS }
    }

    private fun confirmed() = clusters.filter { it.hits >= CONFIRMED_HITS }.sortedBy { it.ratio }

    companion object {
        private const val TOLERANCE = 0.07
        private const val CONFIRMED_HITS = 40
        private const val MAX_CLUSTERS = 20
    }
}
