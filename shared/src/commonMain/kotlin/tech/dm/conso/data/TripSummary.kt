package tech.dm.conso.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import tech.dm.conso.eco.EcoHint
import tech.dm.conso.fixed
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

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
        if (lostL > 0) wastedL.accumulate(EcoHint.WASTED_ACCEL, lostL)
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
        hintS.accumulate(hint, dt)
        val surplus = fuelStepL - expectedStepL
        if (surplus > 0) wastedL.accumulate(hint, surplus)
    }

    private fun MutableMap<EcoHint, Double>.accumulate(hint: EcoHint, value: Double) {
        this[hint] = (this[hint] ?: 0.0) + value
    }

    val score: Double? get() = if (scoredKm > MIN_SCORED_KM) scoreWeighted / scoredKm else null

    fun toJson(): JsonObject = buildJsonObject {
        put("moving", movingS); put("idle", idleS); put("km", distanceKm); put("fuel", fuelL)
        put("expected", expectedL); put("idleFuel", idleFuelL); put("scoreKm", scoreWeighted); put("scoredKm", scoredKm)
        put("wasted", JsonObject(wastedL.entries.associate { it.key.name to JsonPrimitive(it.value) }))
        put("hintS", JsonObject(hintS.entries.associate { it.key.name to JsonPrimitive(it.value) }))
        put("start", start.json())
        put("end", end.json())
    }

    companion object {
        const val MIN_SCORED_KM = 0.3

        private fun GeoPoint?.json() = this?.let { buildJsonObject { put("lat", it.lat); put("lon", it.lon) } } ?: JsonNull

        fun fromJson(o: JsonObject): TripStats {
            fun point(key: String) = o.optObject(key)?.let { GeoPoint(it.double("lat"), it.double("lon")) }
            fun hints(key: String) = o.optObject(key)?.let { m ->
                m.mapNotNull { (k, v) ->
                    val hint = EcoHint.entries.firstOrNull { it.name == k }
                    val value = (v as? JsonPrimitive)?.doubleOrNull
                    if (hint != null && value != null) hint to value else null
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

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id); put("start", startedAt); put("end", endedAt)
        put("stats", stats.toJson())
        put("from", startCity.json()); put("to", endCity.json())
    }

    companion object {
        fun fromJson(o: JsonObject) = TripSummary(
            o.long("id"), o.long("start"), o.long("end"),
            TripStats.fromJson(o.getValue("stats").jsonObject),
            o.optString("from")?.takeIf { it.isNotBlank() },
            o.optString("to")?.takeIf { it.isNotBlank() },
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
        "${sec(EcoHint.HARSH_ACCEL).fixed(0)} s d'accélérations franches. Montez en vitesse plus progressivement, en passant les rapports tôt.",
        lost(EcoHint.HARSH_ACCEL),
    )
    if (sec(EcoHint.HIGH_RPM) > 20) tips += Tip(
        "Régime trop haut",
        "${sec(EcoHint.HIGH_RPM).fixed(0)} s au-dessus du régime économique. Passez le rapport supérieur vers 2 000–2 500 tr/min.",
        lost(EcoHint.HIGH_RPM),
    )
    if (sec(EcoHint.DOWNHILL_COAST) > 10) tips += Tip(
        "Descentes",
        "${sec(EcoHint.DOWNHILL_COAST).fixed(0)} s avec le pied sur l'accélérateur en descente. Levez complètement le pied : l'injection se coupe.",
        lost(EcoHint.DOWNHILL_COAST),
    )
    if (sec(EcoHint.OVER_SPEED) > 20) tips += Tip(
        "Au-dessus des limitations",
        "${sec(EcoHint.OVER_SPEED).fixed(0)} s au-delà de la vitesse autorisée. La résistance de l'air croît avec le carré de la vitesse.",
        lost(EcoHint.OVER_SPEED),
    )
    if (sec(EcoHint.OVER_CONSUMPTION) > 30) tips += Tip(
        "Allure irrégulière",
        "${sec(EcoHint.OVER_CONSUMPTION).fixed(0)} s au-dessus de la consommation attendue. Gardez une vitesse stable et anticipez le trafic.",
        lost(EcoHint.OVER_CONSUMPTION),
    )
    if (sec(EcoHint.ANTICIPATE) > 5) tips += Tip(
        "Accélérations avant un ralentissement",
        "${sec(EcoHint.ANTICIPATE).fixed(0)} s à accélérer alors qu'une limitation plus basse, un stop ou un rond-point approchait. Levez le pied plus tôt.",
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
        "${(st.idleS / 60).fixed(0)} min moteur tournant à l'arrêt. Au-delà d'une minute d'arrêt, couper le moteur est plus économique.",
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

class TripSummaryStore(private val file: TextFile) : SynchronizedObject() {
    private val items = mutableListOf<TripSummary>()

    init {
        runCatching {
            file.readText().lineSequence().filter { it.isNotBlank() }
                .forEach { items += TripSummary.fromJson(Json.parseToJsonElement(it).jsonObject) }
        }
    }

    fun add(s: TripSummary) = synchronized(this) {
        items.removeAll { it.id == s.id }
        items += s
        file.writeText(items.joinToString("\n") { it.toJson().toString() } + "\n")
    }

    fun all(): List<TripSummary> = synchronized(this) { items.sortedBy { it.startedAt } }
}
