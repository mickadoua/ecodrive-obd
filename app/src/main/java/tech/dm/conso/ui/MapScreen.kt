package tech.dm.conso.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import tech.dm.conso.data.GeoPoint
import tech.dm.conso.data.RoadName
import tech.dm.conso.data.RoadStats
import tech.dm.conso.data.Segment
import tech.dm.conso.data.cell
import tech.dm.conso.data.groupByRoad
import tech.dm.conso.data.fallbackRoad
import androidx.compose.runtime.LaunchedEffect
import java.time.Instant
import java.time.LocalDate
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.osmdroid.util.GeoPoint as OsmPoint

private enum class MapPeriod(val label: String) {
    TODAY("Aujourd'hui"), LAST("Dernier trajet"), WEEK("7 jours"), ALL("Tout")
}

private val DARK_TILES = ColorMatrixColorFilter(
    ColorMatrix().apply { setSaturation(0f) }.apply {
        postConcat(ColorMatrix(floatArrayOf(-0.85f, 0f, 0f, 0f, 235f, 0f, -0.85f, 0f, 0f, 240f, 0f, 0f, -0.85f, 0f, 250f, 0f, 0f, 0f, 1f, 0f)))
    }
)

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

fun excessColor(ratio: Double): Color = when {
    ratio <= 1.1 -> Palette.Green
    ratio <= 1.4 -> Palette.Yellow
    else -> Palette.Red
}

@Composable
fun MapScreen(
    segments: List<Segment>,
    roadNames: Map<String, RoadName>,
    unresolvable: Set<String>,
    current: GeoPoint?,
    focusKey: String?,
    resolveRoads: suspend (List<Segment>) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(MapPeriod.TODAY) }
    var focus by remember(focusKey) {
        mutableStateOf(segments.filter { it.key == focusKey }.takeIf { it.isNotEmpty() }?.let { RoadStats(RoadName("Portion du classement", null), it) })
    }

    val shown = remember(segments, period) {
        when (period) {
            MapPeriod.LAST -> segments.lastOrNull()?.tripId?.let { trip -> segments.filter { it.tripId == trip } }.orEmpty()
            MapPeriod.TODAY -> {
                val since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                segments.filter { it.startedAt >= since }
            }
            MapPeriod.WEEK -> segments.filter { it.startedAt >= System.currentTimeMillis() - 7 * 86_400_000L }
            MapPeriod.ALL -> segments
        }
    }
    LaunchedEffect(shown) { resolveRoads(shown) }
    val roads = remember(shown, roadNames, unresolvable) { groupByRoad(shown, roadNames, unresolvable) }
    val pending = remember(shown, roadNames, unresolvable) { shown.count { it.cell() !in roadNames && it.cell() !in unresolvable } }

    Column(Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Surconsommation", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text("Consommation réelle comparée à l'attendu selon vitesse et dénivelé", color = Palette.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapPeriod.entries.forEach { p -> Chip(p.label, p == period) { period = p; focus = null } }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            OsmMap(shown, current, focus?.segments.orEmpty()) { s ->
                val road = roadNames[s.cell()] ?: fallbackRoad(s).takeIf { s.cell() in unresolvable }
                focus = if (road != null) roads.firstOrNull { it.road == road } ?: RoadStats(road, listOf(s)) else RoadStats(RoadName("Portion", null), listOf(s))
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Surface.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LegendDot(Palette.Green, "conforme")
                LegendDot(Palette.Yellow, "un peu au-dessus")
                LegendDot(Palette.Red, "nettement au-dessus")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Par route", color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                if (pending > 0) "recherche des noms de route…" else "moyenne sur chaque route, pire en premier",
                color = Palette.Muted, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (roads.isEmpty() && pending == 0) {
                item { Text("Aucune route d'au moins 300 m sur la période.", color = Palette.Muted, fontSize = 13.sp) }
            }
            items(roads) { r -> RoadRow(r, selected = focus?.road == r.road) { focus = if (focus?.road == r.road) null else r } }
        }
    }
}

@Composable
internal fun RoadRow(r: RoadStats, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(76.dp)) {
            Text("%+.1f".format(r.surplusL100), color = excessColor(r.excessRatio), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("L/100 en +", color = Palette.Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(r.road.name, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                listOfNotNull(
                    r.road.locality,
                    "%.1f km".format(r.distanceKm),
                    "%.1f réel / %.1f attendu".format(r.l100, r.expectedL100),
                ).joinToString(" · "),
                color = Palette.Muted, fontSize = 12.sp,
            )
            if (r.wastedL > 0.01) {
                Text("%.2f L de trop · %.0f km/h moyen".format(r.wastedL, r.avgSpeedKmh), color = Palette.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Palette.Green else Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (active) Palette.Background else Palette.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Palette.Text, fontSize = 11.sp)
    }
}

@Composable
internal fun OsmMap(segments: List<Segment>, current: GeoPoint?, focus: List<Segment>, onSelect: (Segment) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlayManager.tilesOverlay.setColorFilter(DARK_TILES)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(14.0)
        }
    }
    val fitted = remember { mutableStateOf<Any?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    AndroidView({ map }, Modifier.fillMaxSize()) { m ->
        m.overlays.clear()
        segments.forEach { s ->
            m.overlays += Polyline(m).apply {
                setPoints(s.points.map { OsmPoint(it.lat, it.lon) })
                outlinePaint.color = excessColor(s.excessRatio).toArgb()
                outlinePaint.strokeWidth = if (s in focus) 22f else 12f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                setOnClickListener { _, _, _ -> onSelect(s); true }
            }
        }
        current?.let { p ->
            m.overlays += Polyline(m).apply {
                setPoints(listOf(OsmPoint(p.lat, p.lon), OsmPoint(p.lat + 0.00001, p.lon)))
                outlinePaint.color = AndroidColor.WHITE
                outlinePaint.strokeWidth = 26f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            }
        }
        when {
            focus.isNotEmpty() && fitted.value != focus -> {
                fitted.value = focus
                val pts = focus.flatMap { it.points }.map { OsmPoint(it.lat, it.lon) }
                m.post { m.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.4f), true) }
            }
            fitted.value != segments.firstOrNull() && segments.isNotEmpty() -> {
                fitted.value = segments.firstOrNull()
                val pts = segments.flatMap { it.points }
                m.post {
                    m.zoomToBoundingBox(
                        BoundingBox.fromGeoPointsSafe(pts.map { OsmPoint(it.lat, it.lon) }).increaseByScale(1.2f), false
                    )
                }
            }
            segments.isEmpty() && current != null -> m.controller.setCenter(OsmPoint(current.lat, current.lon))
        }
        m.invalidate()
    }
}
