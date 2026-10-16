package tech.dm.conso.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.PointL
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import tech.dm.conso.data.Segment
import kotlin.math.abs
import org.osmdroid.util.GeoPoint as OsmPoint

class SegmentsOverlay(
    segments: List<Segment>,
    style: (Segment) -> Pair<Int, Float>,
    private val onSelect: (Segment) -> Unit,
) : Overlay() {
    private class Drawn(val segment: Segment, val points: List<OsmPoint>, val box: BoundingBox, val paint: Paint) {
        var projected: List<PointL>? = null
    }

    private val paints = HashMap<Pair<Int, Float>, Paint>()
    private val drawn = segments.map { s ->
        val points = s.points.map { OsmPoint(it.lat, it.lon) }
        val (color, width) = style(s)
        val paint = paints.getOrPut(color to width) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = width
                this.style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
        Drawn(s, points, BoundingBox.fromGeoPointsSafe(points), paint)
    }
    private val paths = paints.values.associateWith { Path() }
    private val pathEnds = paints.values.associateWith { Point() }
    private val pixel = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val view = projection.boundingBox
        val power = projection.projectedPowerDifference
        paths.values.forEach { it.rewind() }
        drawn.forEach { d ->
            if (!d.box.intersects(view)) return@forEach
            val path = paths.getValue(d.paint)
            val end = pathEnds.getValue(d.paint)
            var lastX = Int.MIN_VALUE
            var lastY = Int.MIN_VALUE
            val projected = d.projected ?: d.points.map { projection.toProjectedPixels(it.latitude, it.longitude, PointL()) }.also { d.projected = it }
            projected.forEachIndexed { i, p ->
                projection.getPixelsFromProjected(p, power, pixel)
                when {
                    i == 0 && !path.isEmpty && abs(pixel.x - end.x) + abs(pixel.y - end.y) < MIN_STEP_PX -> Unit
                    i == 0 -> path.moveTo(pixel.x.toFloat(), pixel.y.toFloat())
                    i < d.points.lastIndex && abs(pixel.x - lastX) + abs(pixel.y - lastY) < MIN_STEP_PX -> return@forEachIndexed
                    else -> path.lineTo(pixel.x.toFloat(), pixel.y.toFloat())
                }
                lastX = pixel.x
                lastY = pixel.y
            }
            end.set(lastX, lastY)
        }
        paths.entries.sortedBy { it.key.strokeWidth }.forEach { (paint, path) -> canvas.drawPath(path, paint) }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val (nearest, distance) = drawn.map { d ->
            d to d.points.minOf { p ->
                projection.toPixels(p, pixel)
                abs(pixel.x - e.x) + abs(pixel.y - e.y)
            }
        }.minByOrNull { it.second } ?: return false
        if (distance > TAP_RADIUS_PX) return false
        onSelect(nearest.segment)
        return true
    }

    private fun BoundingBox.intersects(o: BoundingBox) =
        latSouth <= o.latNorth && latNorth >= o.latSouth && lonWest <= o.lonEast && lonEast >= o.lonWest

    private companion object {
        const val MIN_STEP_PX = 3
        const val TAP_RADIUS_PX = 48f
    }
}
