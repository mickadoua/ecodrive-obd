package tech.dm.conso.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.dm.conso.auto.AutoStartSettings
import tech.dm.conso.data.Vehicle

data class BondedDevice(val address: String, val name: String)

@Composable
fun AutoStartCard(
    settings: AutoStartSettings,
    vehicle: Vehicle,
    bonded: List<BondedDevice>,
    backgroundLocationGranted: Boolean,
    onChange: (AutoStartSettings) -> Unit,
    onVehicleChange: (Vehicle) -> Unit,
    onRequestBackgroundLocation: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Démarrage automatique", color = Palette.Text, fontWeight = FontWeight.SemiBold)
                Text("Se connecte au module OBD de chaque véhicule dès que le téléphone rejoint sa voiture en Bluetooth.", color = Palette.Muted, fontSize = 12.sp)
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { onChange(settings.copy(enabled = it)) },
                enabled = settings.enabled || (vehicle.carAddress != null && vehicle.obdAddress != null),
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Green),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Module OBD de ${vehicle.name} : ${vehicle.obdName ?: "connectez-vous une fois au module"}", color = Palette.Text, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("Voiture Bluetooth : ${vehicle.carName ?: "non choisie"}", color = Palette.Text, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Chip(if (vehicle.carName == null) "Choisir la voiture" else "Changer", vehicle.carName == null) { picking = !picking }
        if (picking) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bonded.filter { it.address != vehicle.obdAddress }.forEach { d ->
                    Chip(d.name, d.address == vehicle.carAddress) {
                        onVehicleChange(vehicle.copy(carAddress = d.address, carName = d.name))
                        picking = false
                    }
                }
            }
        }
        if (settings.enabled && !backgroundLocationGranted) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Pour la carte et le relief quand l'app démarre seule, la localisation doit être « Toujours autorisée ».",
                color = Palette.Yellow, fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            Chip("Autoriser en arrière-plan", true, onRequestBackgroundLocation)
        }
    }
}
