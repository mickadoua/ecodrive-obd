package tech.dm.conso.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class RoadName(val name: String, val locality: String?) {
    val label get() = listOfNotNull(name, locality).joinToString(" · ")
}

data class RoadStats(val road: RoadName, val segments: List<Segment>) {
    val distanceKm = segments.sumOf { it.distanceKm }
    val fuelL = segments.sumOf { it.fuelL }
    val expectedL = segments.sumOf { it.expectedFuelL }
    val l100 get() = fuelL / distanceKm * 100
    val expectedL100 get() = expectedL / distanceKm * 100
    val surplusL100 get() = l100 - expectedL100
    val wastedL get() = fuelL - expectedL
    val excessRatio get() = l100 / expectedL100.coerceAtLeast(Segment.MIN_EXPECTED_L100)
    val avgSpeedKmh get() = segments.sumOf { it.avgSpeedKmh * it.distanceKm } / distanceKm
}

fun Segment.cell() = key.substringBefore('-')

fun fallbackRoad(s: Segment) = RoadName("Secteur ${s.cell().take(5).uppercase()}", "nom de route indisponible")

fun groupByRoad(
    segments: List<Segment>,
    names: Map<String, RoadName>,
    unresolvable: Set<String> = emptySet(),
    minKm: Double = 0.3,
): List<RoadStats> =
    segments.mapNotNull { s ->
        (names[s.cell()] ?: if (s.cell() in unresolvable) fallbackRoad(s) else null)?.let { it to s }
    }
        .groupBy({ it.first }, { it.second })
        .map { (road, segs) -> RoadStats(road, segs) }
        .filter { it.distanceKm >= minKm }
        .sortedByDescending { it.wastedL }

class RoadNames(private val context: Context, private val file: File) {
    private val _names = MutableStateFlow(load())
    val names: StateFlow<Map<String, RoadName>> = _names.asStateFlow()
    private val _unresolvable = MutableStateFlow(emptySet<String>())
    val unresolvable: StateFlow<Set<String>> = _unresolvable.asStateFlow()

    suspend fun resolve(segments: List<Segment>) {
        val missing = segments.distinctBy { it.cell() }
            .filter { it.cell() !in _names.value && it.cell() !in _unresolvable.value }
        if (!Geocoder.isPresent()) {
            _unresolvable.value = _unresolvable.value + missing.map { it.cell() }
            return
        }
        val geocoder = Geocoder(context, Locale.FRANCE)
        for ((i, s) in missing.withIndex()) {
            if (i > 0 && i % SAVE_EVERY == 0) save()
            val mid = s.points[s.points.size / 2]
            val address = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { runCatching { lookup(geocoder, mid) }.getOrNull() }
            if (address == null) {
                _unresolvable.value = _unresolvable.value + s.cell()
                continue
            }
            val name = address.thoroughfare ?: address.featureName?.takeIf { it.any(Char::isLetter) } ?: "Route sans nom"
            _names.value = _names.value + (s.cell() to RoadName(name, address.locality ?: address.subAdminArea))
        }
        if (missing.isNotEmpty()) save()
    }

    suspend fun localityAt(p: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            runCatching { lookup(Geocoder(context, Locale.FRANCE), p) }.getOrNull()
        } ?: return null
        return address.locality ?: address.subAdminArea
    }

    private suspend fun lookup(geocoder: Geocoder, p: GeoPoint): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(p.lat, p.lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION") geocoder.getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
            }
        }

    private fun load(): Map<String, RoadName> = runCatching {
        val o = JSONObject(file.readText())
        o.keys().asSequence().associateWith { k ->
            val v = o.getJSONObject(k)
            RoadName(v.getString("n"), v.optString("l").ifBlank { null })
        }
    }.getOrDefault(emptyMap())

    private fun save() = withLock {
        val o = JSONObject()
        _names.value.forEach { (k, v) -> o.put(k, JSONObject().put("n", v.name).put("l", v.locality ?: "")) }
        file.writeText(o.toString())
    }

    private fun withLock(block: () -> Unit) = synchronized(this) { block() }

    companion object {
        private const val SAVE_EVERY = 20
        private const val LOOKUP_TIMEOUT_MS = 5_000L
    }
}
