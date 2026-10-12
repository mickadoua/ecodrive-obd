package tech.dm.conso.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class DayStats(
    val date: LocalDate,
    val fuelL: Double = 0.0,
    val distanceKm: Double = 0.0,
    val ecoSum: Double = 0.0,
    val ecoCount: Int = 0,
) {
    val avgL100: Double? get() = if (distanceKm > 0.5) fuelL / distanceKm * 100.0 else null
    val ecoScore: Double? get() = if (ecoCount > 0) ecoSum / ecoCount else null
}

class DailyHistory(private val file: File) {
    private val days = mutableMapOf<LocalDate, DayStats>()

    init {
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val d = DayStats(
                    LocalDate.parse(o.getString("date")),
                    o.getDouble("fuelL"), o.getDouble("distanceKm"),
                    o.getDouble("ecoSum"), o.getInt("ecoCount"),
                )
                days[d.date] = d
            }
        }
    }

    @Synchronized
    fun add(date: LocalDate, fuelL: Double, distanceKm: Double, eco: Double?) {
        val d = days[date] ?: DayStats(date)
        days[date] = d.copy(
            fuelL = d.fuelL + fuelL,
            distanceKm = d.distanceKm + distanceKm,
            ecoSum = d.ecoSum + (eco ?: 0.0),
            ecoCount = d.ecoCount + if (eco != null) 1 else 0,
        )
    }

    @Synchronized
    fun lastDays(count: Int, today: LocalDate = LocalDate.now()): List<DayStats> =
        (count - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { days[it] ?: DayStats(it) }

    @Synchronized
    fun save() {
        val arr = JSONArray()
        days.values.sortedBy { it.date }.takeLast(90).forEach {
            arr.put(
                JSONObject()
                    .put("date", it.date.toString())
                    .put("fuelL", it.fuelL)
                    .put("distanceKm", it.distanceKm)
                    .put("ecoSum", it.ecoSum)
                    .put("ecoCount", it.ecoCount)
            )
        }
        file.writeText(arr.toString())
    }
}
