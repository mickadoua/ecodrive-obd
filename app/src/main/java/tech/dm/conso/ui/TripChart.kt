package tech.dm.conso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.Sample
import tech.dm.conso.data.bucketFor
import tech.dm.conso.data.downsample
import tech.dm.conso.obd.FuelCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil

private enum class ChartWindow(val label: String, val ms: Long?) {
    ONE("1 min", 60_000), FIVE("5 min", 300_000), FIFTEEN("15 min", 900_000), TRIP("Trajet", null)
}

private val clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val clockShort = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun Sample.l100() = litersPerHour?.let { FuelCalculator.per100km(it, speedKmh) }

@Composable
fun TripChart(samples: List<Sample>, modifier: Modifier = Modifier) {
    var window by remember { mutableStateOf(ChartWindow.FIVE) }
    var panMs by remember { mutableLongStateOf(0L) }
    var selectedWall by remember { mutableStateOf<Long?>(null) }
    val measurer = rememberTextMeasurer()

    val first = samples.firstOrNull()?.wallMs ?: 0L
    val last = samples.lastOrNull()?.wallMs ?: 0L
    val total = (last - first).coerceAtLeast(1L)
    val available = ChartWindow.entries.filter { it.ms == null || total > it.ms }
    val effective = if (window in available) window else ChartWindow.TRIP
    val spanMs = effective.ms?.coerceAtMost(total) ?: total
    val endMs = last - panMs.coerceIn(0L, (total - spanMs).coerceAtLeast(0L))
    val startMs = endMs - spanMs
    val bucketMs = bucketFor(spanMs)
    val visible = remember(samples.size, startMs / bucketMs, bucketMs) {
        downsample(samples.filter { it.wallMs in startMs..endMs }, bucketMs)
    }
    val latestSpan by rememberUpdatedState(spanMs)
    val latestStart by rememberUpdatedState(startMs)
    val latestTotal by rememberUpdatedState(total)

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            available.forEach { w ->
                val active = w == effective
                Text(
                    w.label,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Palette.Green else Palette.SurfaceHigh)
                        .clickable { window = w; panMs = 0; selectedWall = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (active) Palette.Background else Palette.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            if (panMs > 0) {
                Text(
                    "En direct ›",
                    Modifier.clickable { panMs = 0; selectedWall = null }.padding(vertical = 6.dp),
                    color = Palette.Green, fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        val msPerPx = latestSpan / size.width.toFloat()
                        panMs = (panMs + (dx * msPerPx).toLong()).coerceIn(0L, (latestTotal - latestSpan).coerceAtLeast(0L))
                        selectedWall = null
                        change.consume()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val t = timeAt(pos.x, size.width, latestStart, latestSpan)
                        selectedWall = if (selectedWall == null) t else null
                    }
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            selectedWall = timeAt(pos.x, size.width, latestStart, latestSpan)
                        },
                    ) { change, _ ->
                        selectedWall = timeAt(change.position.x, size.width, latestStart, latestSpan)
                        change.consume()
                    }
                },
        ) {
            drawChart(visible, startMs, spanMs, selectedWall, measurer, bucketMs)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Glissez pour naviguer · touchez ou appui long pour lire une valeur",
            color = Palette.Muted, fontSize = 11.sp,
        )
    }
}

private fun androidx.compose.ui.input.pointer.PointerInputScope.timeAt(px: Float, width: Int, start: Long, span: Long): Long =
    start + ((px - LEFT.toPx()) / (width - LEFT.toPx() - RIGHT.toPx()) * span).toLong()

private val LEFT = 34.dp
private val RIGHT = 40.dp
private val BOTTOM = 18.dp
private val TOP = 18.dp

private fun DrawScope.drawChart(
    visible: List<Sample>,
    startMs: Long,
    spanMs: Long,
    selectedWall: Long?,
    measurer: TextMeasurer,
    bucketMs: Long,
) {
    val label = TextStyle(color = Palette.Muted, fontSize = 10.sp)
    val left = LEFT.toPx()
    val right = size.width - RIGHT.toPx()
    val bottom = size.height - BOTTOM.toPx()
    val top = TOP.toPx()
    val h = bottom - top
    val w = right - left
    fun x(t: Long) = left + (t - startMs).toFloat() / spanMs * w

    if (visible.size < 2) {
        drawText(measurer, "En attente de données…", Offset(left, bottom / 2), label)
        return
    }

    val maxC = (ceil((visible.mapNotNull { it.l100() }.maxOrNull() ?: 10.0).coerceIn(10.0, 30.0) / 5) * 5)
    fun yC(v: Double) = bottom - (v.coerceAtMost(maxC) / maxC * h).toFloat()

    val alts = visible.mapNotNull { it.altitudeM }
    val minA = alts.minOrNull()
    val maxA = alts.maxOrNull()
    val rangeA = if (minA != null && maxA != null) (maxA - minA).coerceAtLeast(10.0) else 10.0
    fun yA(a: Double) = bottom - ((a - minA!!) / rangeA * h * 0.6).toFloat()

    if (minA != null) {
        val area = Path()
        var started = false
        visible.forEach { s ->
            val a = s.altitudeM ?: return@forEach
            if (!started) { area.moveTo(x(s.wallMs), bottom); started = true }
            area.lineTo(x(s.wallMs), yA(a))
        }
        area.lineTo(x(visible.last().wallMs), bottom)
        area.close()
        drawPath(area, Brush.verticalGradient(listOf(Palette.Blue.copy(alpha = 0.45f), Palette.Blue.copy(alpha = 0.05f))))
        drawText(measurer, "%.0f m".format(minA + rangeA), Offset(right + 4, yA(minA + rangeA) - 6), label.copy(color = Palette.Blue))
        drawText(measurer, "%.0f m".format(minA), Offset(right + 4, bottom - 14), label.copy(color = Palette.Blue))
    }

    var level = 0.0
    while (level <= maxC) {
        val y = yC(level)
        drawLine(Palette.Outline, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        drawText(measurer, "%.0f".format(level), Offset(0f, y - 7), label)
        level += 5.0
    }
    drawText(measurer, "L/100 km", Offset(left, -4f), label)

    visible.zipWithNext { a, b ->
        val ca = a.l100() ?: return@zipWithNext
        val cb = b.l100() ?: return@zipWithNext
        val score = listOfNotNull(a.ecoScore, b.ecoScore).average().takeIf { !it.isNaN() }
        drawLine(
            score?.let(Palette::forScore) ?: Palette.Muted,
            Offset(x(a.wallMs), yC(ca)), Offset(x(b.wallMs), yC(cb)),
            strokeWidth = 5f, cap = StrokeCap.Round,
        )
    }

    for (i in 0..3) {
        val t = startMs + spanMs * i / 3
        val fmt = if (spanMs > 20 * 60_000) clockShort else clock
        val text = fmt.format(Instant.ofEpochMilli(t))
        val layout = measurer.measure(text, label)
        val px = (x(t) - layout.size.width / 2f).coerceIn(left, right - layout.size.width)
        drawText(layout, topLeft = Offset(px, bottom + 4))
    }

    val sel = selectedWall?.let { t -> visible.minByOrNull { abs(it.wallMs - t) } } ?: return
    val sx = x(sel.wallMs)
    drawLine(Palette.Text.copy(alpha = 0.6f), Offset(sx, 0f), Offset(sx, bottom), strokeWidth = 2f)
    sel.l100()?.let { drawCircle(Palette.Text, 7f, Offset(sx, yC(it))) }
    sel.altitudeM?.let { if (minA != null) drawCircle(Palette.Blue, 6f, Offset(sx, yA(it))) }

    val lines = listOfNotNull(
        clock.format(Instant.ofEpochMilli(sel.wallMs)) + if (bucketMs > 1_000) " · moy. ${bucketMs / 1_000} s" else "",
        sel.l100()?.let { "%.1f L/100 km".format(it) } ?: sel.litersPerHour?.let { "%.1f L/h (arrêt)".format(it) },
        "%.0f km/h".format(sel.speedKmh) + (sel.ecoScore?.let { " · éco %.0f".format(it) } ?: ""),
        sel.altitudeM?.let { "alt. %.0f m · pente %+.1f %%".format(it, sel.grade * 100) },
    )
    val box = measurer.measure(lines.joinToString("\n"), TextStyle(color = Palette.Text, fontSize = 11.sp, lineHeight = 15.sp))
    val pad = 10f
    val bw = box.size.width + pad * 2
    val bh = box.size.height + pad * 2
    val bx = if (sx + 12 + bw < size.width) sx + 12 else sx - 12 - bw
    drawRoundRect(Palette.SurfaceHigh, Offset(bx, 4f), Size(bw, bh), CornerRadius(16f, 16f))
    drawText(box, topLeft = Offset(bx + pad, 4f + pad))
}
