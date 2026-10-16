package tech.dm.conso.car

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import tech.dm.conso.ConsoApp
import tech.dm.conso.data.LiveState
import tech.dm.conso.ui.ArcGauge
import tech.dm.conso.ui.Palette
import tech.dm.conso.ui.TripChart

class DashboardSurface<T>(private val context: Context, private val owner: T) : SurfaceCallback
    where T : LifecycleOwner, T : SavedStateRegistryOwner {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private val visible: MutableState<Rect?> = mutableStateOf(null)

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        val virtual = context.getSystemService(DisplayManager::class.java)
            .createVirtualDisplay("conso", container.width, container.height, container.dpi, surface, 0)
        display = virtual
        presentation = Presentation(context, virtual.display).apply {
            setContentView(
                ComposeView(context).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setContent { SurfaceDashboard(visible) }
                }
            )
            show()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible.value = visibleArea
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        presentation?.dismiss()
        display?.release()
        presentation = null
        display = null
    }
}

@Composable
private fun SurfaceDashboard(visible: MutableState<Rect?>) {
    val state by ConsoApp.instance.telemetry.state.collectAsState()
    val area = visible.value
    val base = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        val inner = if (area == null) Modifier.fillMaxSize() else with(base) {
            Modifier
                .offset { IntOffset(area.left, area.top) }
                .size(area.width().toDp(), area.height().toDp())
        }
        CompositionLocalProvider(LocalDensity provides Density(base.density * 1.3f, base.fontScale)) {
            Column(inner.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConsoGauge(state, Modifier.weight(1f))
                    EcoGauge(state, Modifier.weight(1.3f))
                }
                Panel(Modifier.fillMaxWidth().weight(1f)) { TripChart(state.samples, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(14.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ConsoGauge(s: LiveState, modifier: Modifier) {
    val l100 = s.litersPer100
    val value = l100 ?: s.litersPerHour
    val color = when {
        l100 != null -> Palette.forConsumption(l100)
        value != null -> Palette.Blue
        else -> Palette.Outline
    }
    Panel(modifier) {
        ArcGauge(((value ?: 0.0) / if (l100 != null) 20.0 else 3.0).toFloat(), color, Modifier.aspectRatio(1f).fillMaxSize(), strokeWidth = 40f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.let { "%.1f".format(it) } ?: "–", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text(if (l100 != null) "L/100 km" else "L/h", color = Palette.Muted, fontSize = 13.sp)
            Text("%.0f km/h".format(s.speedKmh), color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EcoGauge(s: LiveState, modifier: Modifier) {
    val score = s.eco?.score
    val color = score?.let(Palette::forScore) ?: Palette.Outline
    Panel(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                ArcGauge(((score ?: 0.0) / 100).toFloat(), color, Modifier.fillMaxSize(), strokeWidth = 30f)
                Text(score?.let { "%.0f".format(it) } ?: "–", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(carTips(s).firstOrNull() ?: "En attente", color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Moy. " + (s.tripAvgL100?.let { "%.1f L/100".format(it) } ?: "–"), color = Palette.Text, fontSize = 15.sp)
                Text("%.1f km · %.2f L".format(s.tripKm, s.tripLiters), color = Palette.Muted, fontSize = 13.sp)
                Text("Pente %+.1f %%".format(s.grade * 100), color = Palette.Muted, fontSize = 13.sp)
            }
        }
    }
}
