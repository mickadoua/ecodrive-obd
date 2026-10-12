package tech.dm.conso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.DayStats
import tech.dm.conso.data.RoadName
import tech.dm.conso.data.RoadStats
import tech.dm.conso.data.Segment
import tech.dm.conso.data.cell
import tech.dm.conso.data.fallbackRoad
import tech.dm.conso.data.groupByRoad
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val hourFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private data class TripSummary(val startedAt: Long, val endedAt: Long, val segments: List<Segment>) {
    val distanceKm = segments.sumOf { it.distanceKm }
    val l100 get() = segments.sumOf { it.fuelL } / distanceKm * 100
    val surplusL100 get() = l100 - segments.sumOf { it.expectedFuelL } / distanceKm * 100
}

@Composable
fun DayDetailScreen(
    day: DayStats,
    allSegments: List<Segment>,
    roadNames: Map<String, RoadName>,
    unresolvable: Set<String>,
    resolveRoads: suspend (List<Segment>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val zone = ZoneId.systemDefault()
    val segments = remember(day, allSegments) {
        allSegments.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == day.date }
    }
    LaunchedEffect(segments) { resolveRoads(segments) }
    val roads = remember(segments, roadNames, unresolvable) { groupByRoad(segments, roadNames, unresolvable) }
    val trips = remember(segments) {
        segments.groupBy { it.tripId }.map { (_, segs) -> TripSummary(segs.first().startedAt, segs.last().startedAt, segs) }
            .filter { it.distanceKm > 0.2 }
    }
    var focus by remember { mutableStateOf<RoadStats?>(null) }
    val drivingMin = segments.sumOf { s -> if (s.avgSpeedKmh > 1) s.distanceKm / s.avgSpeedKmh * 60 else 0.0 }

    LazyColumn(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(Palette.Surface).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Palette.Text) }
                Spacer(Modifier.width(12.dp))
                Text(
                    day.date.format(dayTitle).replaceFirstChar(Char::uppercase),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Text,
                )
            }
        }
        item {
            Card {
                Row(Modifier.fillMaxWidth()) {
                    DayStat("Moyenne", day.avgL100?.let { "%.1f".format(it) } ?: "–", "L/100", Modifier.weight(1f))
                    DayStat("Distance", "%.1f".format(day.distanceKm), "km", Modifier.weight(1f))
                    DayStat("Carburant", "%.2f".format(day.fuelL), "L", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    DayStat(
                        "Éco-score", day.ecoScore?.let { "%.0f".format(it) } ?: "–", "/100", Modifier.weight(1f),
                        color = day.ecoScore?.let(Palette::forScore) ?: Palette.Text,
                    )
                    DayStat("Trajets", "${trips.size}", "", Modifier.weight(1f))
                    DayStat("Conduite", if (drivingMin >= 1) "%.0f".format(drivingMin) else "–", "min", Modifier.weight(1f))
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))) {
                if (segments.isEmpty()) {
                    Text("Pas de trace GPS ce jour-là.", color = Palette.Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    OsmMap(segments, null, focus?.segments.orEmpty()) { s ->
                        val road = roadNames[s.cell()] ?: fallbackRoad(s).takeIf { s.cell() in unresolvable }
                        focus = roads.firstOrNull { it.road == road }
                    }
                }
            }
        }
        if (trips.isNotEmpty()) {
            item { SectionTitle("Trajets", "") }
            items(trips) { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${hourFmt.format(Instant.ofEpochMilli(t.startedAt))} – ${hourFmt.format(Instant.ofEpochMilli(t.endedAt))}",
                        color = Palette.Text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    )
                    Text("%.1f km".format(t.distanceKm), color = Palette.Muted, fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("%.1f L/100".format(t.l100), color = excessColor(1 + t.surplusL100 / 10), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (roads.isNotEmpty()) {
            item { SectionTitle("Par route", "pire en premier") }
            items(roads) { r -> RoadRow(r, selected = focus?.road == r.road) { focus = if (focus?.road == r.road) null else r } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DayStat(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier,
    color: androidx.compose.ui.graphics.Color = Palette.Text,
) {
    Column(modifier) {
        Text(label, color = Palette.Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = Palette.Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
