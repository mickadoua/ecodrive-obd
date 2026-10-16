package tech.dm.conso.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.data.ConnectionStatus
import tech.dm.conso.data.DayStats
import tech.dm.conso.data.LiveState
import tech.dm.conso.data.ObdSource
import tech.dm.conso.obd.FuelMethod
import kotlin.math.atan

@Composable
fun Dashboard(
    state: LiveState,
    days: List<DayStats>,
    onConnectClick: () -> Unit,
    onDisconnect: () -> Unit,
    onDayClick: (DayStats) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state, onConnectClick, onDisconnect)
        state.error?.takeIf { state.status == ConnectionStatus.ERROR }?.let {
            Card { Text("Erreur : $it", color = Palette.Red) }
        }
        HeroCard(state)
        EcoCard(state)
        Card {
            SectionTitle("Trajet", "consommation et relief")
            Spacer(Modifier.height(12.dp))
            TripChart(state.samples)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(Palette.Green, "L/100 km, couleur = éco-conduite")
                Legend(Palette.Blue, "Relief")
            }
        }
        Card {
            val total = days.sumOf { it.distanceKm }
            val fuel = days.sumOf { it.fuelL }
            SectionTitle(
                "7 derniers jours · touchez un jour",
                if (total > 0.5) "%.0f km · %.1f L/100 km".format(total, fuel / total * 100) else "Aucun trajet",
            )
            Spacer(Modifier.height(12.dp))
            DaysBarChart(days, onDayClick = onDayClick)
        }
        if (state.status == ConnectionStatus.CONNECTED) {
            Text("Mesure : ${state.method.label}", color = Palette.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Header(state: LiveState, onConnectClick: () -> Unit, onDisconnect: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Conso", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            val (dot, label) = when (state.status) {
                ConnectionStatus.CONNECTED -> Palette.Green to (state.sourceLabel ?: "Connecté")
                ConnectionStatus.CONNECTING -> Palette.Yellow to "Connexion à ${state.sourceLabel ?: "l'OBD"}…"
                ConnectionStatus.ERROR -> if (state.retrying) Palette.Yellow to "Nouvelle tentative…" else Palette.Red to "Non connecté"
                ConnectionStatus.DISCONNECTED -> Palette.Muted to "Déconnecté"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Palette.Muted, fontSize = 13.sp)
            }
        }
        val active = state.status == ConnectionStatus.CONNECTED || state.status == ConnectionStatus.CONNECTING
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) Palette.SurfaceHigh else Palette.Green)
                .clickable { if (active) onDisconnect() else onConnectClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (active) Icons.Rounded.PowerSettingsNew else Icons.Rounded.Bluetooth, null,
                tint = if (active) Palette.Text else Palette.Background, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (active) "Arrêter" else "Connecter",
                color = if (active) Palette.Text else Palette.Background, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HeroCard(state: LiveState) {
    val l100 = state.litersPer100
    val display = l100 ?: state.litersPerHour
    val unit = if (l100 != null) "L/100 km" else "L/h"
    val gaugeMax = if (l100 != null) 20f else 3f
    val fraction by animateFloatAsState(((display ?: 0.0) / gaugeMax).toFloat(), label = "gauge")
    val color by animateColorAsState(
        when {
            l100 != null -> Palette.forConsumption(l100)
            display != null -> Palette.Blue
            else -> Palette.Outline
        },
        label = "gaugeColor",
    )
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                ArcGauge(fraction, color, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        display?.let { "%.1f".format(it) } ?: "–",
                        fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Palette.Text,
                    )
                    Text(unit, color = Palette.Muted, fontSize = 13.sp)
                    Text("%.0f km/h".format(state.speedKmh), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Stat("Moyenne trajet", state.tripAvgL100?.let { "%.1f".format(it) } ?: "–", "L/100")
                Stat("Distance", "%.1f".format(state.tripKm), "km")
                Stat("Carburant", "%.2f".format(state.tripLiters), "L")
                Stat("Régime", state.rpm?.let { "%.0f".format(it) } ?: "–", "tr/min")
            }
        }
        if (state.status == ConnectionStatus.CONNECTED && state.method == FuelMethod.NONE) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Ce véhicule n'expose pas de PID permettant un calcul fiable de la consommation.",
                color = Palette.Yellow, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun EcoCard(state: LiveState) {
    val eco = state.eco
    val score = eco?.score ?: 0.0
    val color by animateColorAsState(if (eco != null) Palette.forScore(score) else Palette.Outline, label = "eco")
    val fraction by animateFloatAsState((score / 100).toFloat(), label = "ecoFraction")
    Card {
        SectionTitle("Éco-conduite", "tient compte du dénivelé")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                ArcGauge(fraction, color, Modifier.fillMaxSize(), strokeWidth = 18f)
                Text(eco?.let { "%.0f".format(score) } ?: "–", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(eco?.message ?: "En attente de données", color = color, fontWeight = FontWeight.SemiBold)
                GradeRow(state)
                eco?.let {
                    Text(
                        "Attendu sur ce relief : %.1f L/h".format(it.expectedLh),
                        color = Palette.Muted, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeRow(state: LiveState) {
    val pct = state.grade * 100
    val angle = Math.toDegrees(atan(state.grade)).toFloat() * 3
    val tint = when {
        pct > 2 -> Palette.Red
        pct < -2 -> Palette.Green
        else -> Palette.Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Landscape, null, tint = tint, modifier = Modifier.size(20.dp).rotate(-angle))
        Spacer(Modifier.width(6.dp))
        Text("%+.1f %%".format(pct), color = Palette.Text, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Text(
            state.altitudeM?.let { "alt. %.0f m · %s".format(it, state.altitudeSource ?: "") } ?: "altitude en attente",
            color = Palette.Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String) {
    Column {
        Text(label, color = Palette.Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(subtitle, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Palette.Muted, fontSize = 12.sp)
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(18.dp)
    ) { content() }
}

@Composable
fun SourcePicker(sources: List<ObdSource>, onPick: (ObdSource) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Choisir le module OBD") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { s ->
                    SourceRow(
                        if (s is ObdSource.Simulator) Icons.Rounded.Science else Icons.Rounded.Bluetooth,
                        s.label,
                    ) { onPick(s) }
                }
                if (sources.none { it is ObdSource.Bluetooth }) {
                    Text("Aucun appareil Bluetooth appairé.", color = Palette.Muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Palette.Text)
    }
}
