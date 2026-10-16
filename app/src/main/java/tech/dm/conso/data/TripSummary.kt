package tech.dm.conso.data

import org.json.JSONObject
import tech.dm.conso.eco.EcoHint
import java.io.File

class TripStats(
    var movingS: Double = 0.0,
    var idleS: Double = 0.0,
    var distanceKm: Double = 0.0,
    var fuelL: Double = 0.0,
    var expectedL: Double = 0.0,
    var idleFuelL: Double = 0.0,
    var scoreWeighted: Double = 0.0,
    var scoredKm: Double = 0.0,
    val wastedL: MutableMap<EcoHint, Double> = mutableMapOf(),
    val hintS: MutableMap<EcoHint, Double> = mutableMapOf(),
    var start: GeoPoint? = null,
    var end: GeoPoint? = null,
) {
    fun add(dt: Double, speedKmh: Double, stepKm: Double, fuelStepL: Double, expectedStepL: Double, hint: EcoHint, score: Double, position: GeoPoint?, lostL: Double = 0.0) {
        if (position != null) {
            if (start == null) start = position
            end = position
        }
        fuelL += fuelStepL
        if (lostL > 0) wastedL.merge(EcoHint.WASTED_ACCEL, lostL, Double::plus)
        distanceKm += stepKm
        if (speedKmh < 3) {
            idleS += dt
            idleFuelL += fuelStepL
            return
        }
        movingS += dt
        expectedL += expectedStepL
        scoreWeighted += score * stepKm
        scoredKm += stepKm
        hintS.merge(hint, dt, Double::plus)
        val surplus = fuelStepL - expectedStepL
        if (surplus > 0) wastedL.merge(hint, surplus, Double::plus)
    }

    val score: Double? get() = if (scoredKm > MIN_SCORED_KM) scoreWeighted / scoredKm else null

    fun toJson(): JSONObject = JSONObject()
        .put("moving", movingS).put("idle", idleS).put("km", distanceKm).put("fuel", fuelL)
        .put("expected", expectedL).put("idleFuel", idleFuelL).put("scoreKm", scoreWeighted).put("scoredKm", scoredKm)
        .put("wasted", JSONObject(wastedL.mapKeys { it.key.name }))
        .put("hintS", JSONObject(hintS.mapKeys { it.key.name }))
        .put("start", start?.let { JSONObject().put("lat", it.lat).put("lon", it.lon) } ?: JSONObject.NULL)
        .put("end", end?.let { JSONObject().put("lat", it.lat).put("lon", it.lon) } ?: JSONObject.NULL)

    companion object {
        const val MIN_SCORED_KM = 0.3

        fun fromJson(o: JSONObject): TripStats {
            fun point(key: String) = o.optJSONObject(key)?.let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }
            fun hints(key: String) = o.optJSONObject(key)?.let { m ->
                m.keys().asSequence().mapNotNull { k ->
                    runCatching { EcoHint.valueOf(k) }.getOrNull()?.let { it to m.getDouble(k) }
                }.toMap().toMutableMap()
            } ?: mutableMapOf()
            return TripStats(
                o.optDouble("moving", 0.0), o.optDouble("idle", 0.0), o.optDouble("km", 0.0), o.optDouble("fuel", 0.0),
                o.optDouble("expected", 0.0), o.optDouble("idleFuel", 0.0), o.optDouble("scoreKm", 0.0), o.optDouble("scoredKm", 0.0),
                hints("wasted"), hints("hintS"), point("start"), point("end"),
            )
        }
    }
}

data class TripSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val stats: TripStats,
    val startCity: String?,
    val endCity: String?,
) {
    val l100: Double? get() = if (stats.distanceKm > 0.3) stats.fuelL / stats.distanceKm * 100 else null
    val expectedL100: Double? get() = if (stats.distanceKm > 0.3) (stats.expectedL + stats.idleFuelL) / stats.distanceKm * 100 else null
    val route: String? get() = if (startCity != null && endCity != null) "$startCity → $endCity" else null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("start", startedAt).put("end", endedAt)
        .put("stats", stats.toJson())
        .put("from", startCity ?: JSONObject.NULL).put("to", endCity ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = TripSummary(
            o.getLong("id"), o.getLong("start"), o.getLong("end"),
            TripStats.fromJson(o.getJSONObject("stats")),
            o.optString("from").takeIf { !o.isNull("from") && it.isNotBlank() },
            o.optString("to").takeIf { !o.isNull("to") && it.isNotBlank() },
        )
    }
}

data class Tip(val title: String, val text: String, val wastedL: Double)

fun tipsFor(s: TripSummary): List<Tip> {
    val st = s.stats
    val tips = mutableListOf<Tip>()
    fun sec(h: EcoHint) = st.hintS[h] ?: 0.0
    fun lost(h: EcoHint) = st.wastedL[h] ?: 0.0
    if (sec(EcoHint.HARSH_ACCEL) > 5) tips += Tip(
        "Accélérations trop vives",
        "%.0f s d'accélérations franches. Montez en vitesse plus progressivement, en passant les rapports tôt.".format(sec(EcoHint.HARSH_ACCEL)),
        lost(EcoHint.HARSH_ACCEL),
    )
    if (sec(EcoHint.HIGH_RPM) > 20) tips += Tip(
        "Régime trop haut",
        "%.0f s au-dessus du régime économique. Passez le rapport supérieur vers 2 000–2 500 tr/min.".format(sec(EcoHint.HIGH_RPM)),
        lost(EcoHint.HIGH_RPM),
    )
    if (sec(EcoHint.DOWNHILL_COAST) > 10) tips += Tip(
        "Descentes",
        "%.0f s avec le pied sur l'accélérateur en descente. Levez complètement le pied : l'injection se coupe.".format(sec(EcoHint.DOWNHILL_COAST)),
        lost(EcoHint.DOWNHILL_COAST),
    )
    if (sec(EcoHint.OVER_SPEED) > 20) tips += Tip(
        "Au-dessus des limitations",
        "%.0f s au-delà de la vitesse autorisée. La résistance de l'air croît avec le carré de la vitesse.".format(sec(EcoHint.OVER_SPEED)),
        lost(EcoHint.OVER_SPEED),
    )
    if (sec(EcoHint.OVER_CONSUMPTION) > 30) tips += Tip(
        "Allure irrégulière",
        "%.0f s au-dessus de la consommation attendue. Gardez une vitesse stable et anticipez le trafic.".format(sec(EcoHint.OVER_CONSUMPTION)),
        lost(EcoHint.OVER_CONSUMPTION),
    )
    if (sec(EcoHint.ANTICIPATE) > 5) tips += Tip(
        "Accélérations avant un ralentissement",
        "%.0f s à accélérer alors qu'une limitation plus basse, un stop ou un rond-point approchait. Levez le pied plus tôt.".format(sec(EcoHint.ANTICIPATE)),
        lost(EcoHint.ANTICIPATE),
    )
    if (lost(EcoHint.WASTED_ACCEL) > 0) tips += Tip(
        "Accélérations perdues",
        "Des accélérations suivies d'un freinage quelques secondes plus tard : l'énergie gagnée part dans les freins.",
        lost(EcoHint.WASTED_ACCEL),
    )
    if (sec(EcoHint.HARSH_BRAKE) > 3) tips += Tip(
        "Freinages brusques",
        "Chaque freinage fort gaspille l'élan acquis. Regardez plus loin et relâchez plus tôt.",
        lost(EcoHint.HARSH_BRAKE),
    )
    if (st.idleS > 120) tips += Tip(
        "Ralenti",
        "%.0f min moteur tournant à l'arrêt. Au-delà d'une minute d'arrêt, couper le moteur est plus économique.".format(st.idleS / 60),
        st.idleFuelL,
    )
    return tips.sortedByDescending { it.wastedL }.ifEmpty {
        listOf(Tip("Trajet exemplaire", "Rien à redire : conduite souple, adaptée au relief et aux limitations.", 0.0))
    }
}

data class RouteBoard(val route: String, val trips: List<TripSummary>) {
    val best get() = trips.first()
}

fun routeBoards(summaries: List<TripSummary>): List<RouteBoard> =
    summaries.filter { it.route != null && it.l100 != null }
        .groupBy { it.route!! }
        .map { (route, trips) -> RouteBoard(route, trips.sortedBy { it.l100 }) }
        .sortedByDescending { it.trips.size }

class TripSummaryStore(private val file: File) {
    private val items = mutableListOf<TripSummary>()

    init {
        runCatching { file.forEachLine { if (it.isNotBlank()) items += TripSummary.fromJson(JSONObject(it)) } }
    }

    @Synchronized
    fun add(s: TripSummary) {
        items.removeAll { it.id == s.id }
        items += s
        file.writeText(items.joinToString("\n") { it.toJson().toString() } + "\n")
    }

    @Synchronized
    fun all(): List<TripSummary> = items.sortedBy { it.startedAt }
}
