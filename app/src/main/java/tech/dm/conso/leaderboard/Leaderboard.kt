package tech.dm.conso.leaderboard

import tech.dm.conso.data.CarSegment
import tech.dm.conso.data.Segment
import tech.dm.conso.data.VehicleProfile
import tech.dm.conso.obd.FuelType
import kotlin.random.Random

data class PortionStats(
    val key: String,
    val segments: List<Segment>,
    val myL100: Double,
    val communityMedianL100: Double,
    val drivers: Int,
    val betterThanPct: Int,
    val avgGrade: Double,
)

data class ModelEntry(val label: String, val medianL100: Double, val drivers: Int, val isMe: Boolean = false)

interface LeaderboardRepository {
    val isSimulated: Boolean
    fun portions(mine: List<Segment>): List<PortionStats>
    fun equivalentModels(profile: VehicleProfile, myL100: Double?): List<ModelEntry>
}

class MockLeaderboard : LeaderboardRepository {
    override val isSimulated = true

    override fun portions(mine: List<Segment>): List<PortionStats> =
        mine.groupBy { it.key }
            .filterValues { segs -> segs.sumOf { it.distanceKm } > 0.05 }
            .map { (key, segs) ->
                val dist = segs.sumOf { it.distanceKm }
                val my = segs.sumOf { it.fuelL } / dist * 100
                val expected = segs.sumOf { it.expectedFuelL } / dist * 100
                val rnd = Random(key.hashCode())
                val drivers = 15 + rnd.nextInt(180)
                val peers = List(drivers) { expected * (1.05 + rnd.nextDouble(-0.25, 0.45)) }
                PortionStats(
                    key = key,
                    segments = segs,
                    myL100 = my,
                    communityMedianL100 = peers.sorted()[drivers / 2],
                    drivers = drivers,
                    betterThanPct = (peers.count { it > my } * 100 / drivers),
                    avgGrade = segs.sumOf { it.avgGrade * it.distanceKm } / dist,
                )
            }
            .sortedByDescending { it.segments.size * 1000 + it.segments.sumOf { s -> s.distanceKm } }

    override fun equivalentModels(profile: VehicleProfile, myL100: Double?): List<ModelEntry> {
        val catalog = CATALOG[profile.segment to profile.fuel].orEmpty()
        val rnd = Random(profile.vehicleClass.hashCode())
        val entries = catalog.map { (label, l100) ->
            ModelEntry(label, l100 * (1 + rnd.nextDouble(-0.04, 0.04)), 200 + rnd.nextInt(4000))
        }
        val me = myL100?.let { ModelEntry("Vous · ${profile.label}", it, 1, isMe = true) }
        return (entries + listOfNotNull(me)).sortedBy { it.medianL100 }
    }

    companion object {
        private val CATALOG: Map<Pair<CarSegment, FuelType>, List<Pair<String, Double>>> = mapOf(
            (CarSegment.CITY to FuelType.GASOLINE) to listOf(
                "Dacia Sandero 1.0 TCe 90" to 6.1, "Renault Clio V 1.0 TCe 90" to 5.9,
                "Peugeot 208 1.2 PureTech 100" to 6.0, "Toyota Yaris 1.5 Hybrid" to 4.4,
                "Volkswagen Polo 1.0 TSI 95" to 5.8, "Citroën C3 1.2 PureTech 83" to 6.2,
                "Opel Corsa 1.2 75" to 6.1, "Hyundai i20 1.0 T-GDi" to 6.0,
            ),
            (CarSegment.CITY to FuelType.DIESEL) to listOf(
                "Renault Clio V 1.5 Blue dCi 100" to 4.6, "Peugeot 208 1.5 BlueHDi 100" to 4.5,
                "Dacia Sandero 1.5 dCi 95" to 4.8, "Citroën C3 1.5 BlueHDi 100" to 4.7,
                "Volkswagen Polo 1.6 TDI 95" to 4.7,
            ),
            (CarSegment.COMPACT to FuelType.GASOLINE) to listOf(
                "Renault Mégane 1.3 TCe 140" to 6.8, "Peugeot 308 1.2 PureTech 130" to 6.5,
                "Volkswagen Golf 1.5 TSI 130" to 6.4, "Toyota Corolla 1.8 Hybrid" to 4.8,
                "Ford Focus 1.0 EcoBoost 125" to 6.6, "Seat Leon 1.5 TSI 130" to 6.4,
                "Kia Ceed 1.5 T-GDi 160" to 6.9,
            ),
            (CarSegment.COMPACT to FuelType.DIESEL) to listOf(
                "Renault Mégane 1.5 Blue dCi 115" to 5.0, "Peugeot 308 1.5 BlueHDi 130" to 5.0,
                "Volkswagen Golf 2.0 TDI 115" to 5.1, "Skoda Octavia 2.0 TDI 116" to 5.0,
                "Ford Focus 1.5 EcoBlue 120" to 5.1,
            ),
            (CarSegment.SEDAN to FuelType.GASOLINE) to listOf(
                "Peugeot 508 1.6 PureTech 180" to 7.3, "Volkswagen Passat 1.5 TSI 150" to 6.9,
                "Toyota Camry 2.5 Hybrid" to 5.6, "BMW 320i" to 7.5, "Skoda Superb 1.5 TSI 150" to 6.9,
            ),
            (CarSegment.SEDAN to FuelType.DIESEL) to listOf(
                "Peugeot 508 1.5 BlueHDi 130" to 5.2, "Volkswagen Passat 2.0 TDI 150" to 5.5,
                "BMW 320d" to 5.4, "Skoda Superb 2.0 TDI 150" to 5.5, "Mercedes C 220 d" to 5.6,
            ),
            (CarSegment.SUV to FuelType.GASOLINE) to listOf(
                "Dacia Duster 1.3 TCe 130" to 7.2, "Renault Captur 1.3 TCe 140" to 6.9,
                "Peugeot 2008 1.2 PureTech 130" to 6.8, "Peugeot 3008 1.2 PureTech 130" to 7.1,
                "Toyota C-HR 1.8 Hybrid" to 5.2, "Volkswagen T-Roc 1.5 TSI 150" to 7.0,
                "Hyundai Tucson 1.6 T-GDi" to 7.8, "Nissan Qashqai 1.3 DIG-T 140" to 7.1,
            ),
            (CarSegment.SUV to FuelType.DIESEL) to listOf(
                "Dacia Duster 1.5 Blue dCi 115" to 5.6, "Peugeot 3008 1.5 BlueHDi 130" to 5.5,
                "Renault Kadjar 1.5 Blue dCi 115" to 5.6, "Volkswagen Tiguan 2.0 TDI 150" to 6.1,
                "Nissan Qashqai 1.5 dCi 115" to 5.7,
            ),
            (CarSegment.VAN to FuelType.GASOLINE) to listOf(
                "Dacia Jogger 1.0 TCe 110" to 6.6, "Renault Scénic 1.3 TCe 140" to 7.2,
                "Citroën Berlingo 1.2 PureTech 110" to 7.4, "Volkswagen Touran 1.5 TSI 150" to 7.1,
            ),
            (CarSegment.VAN to FuelType.DIESEL) to listOf(
                "Dacia Jogger 1.5 dCi" to 5.4, "Renault Kangoo 1.5 Blue dCi 95" to 5.9,
                "Citroën Berlingo 1.5 BlueHDi 100" to 5.6, "Volkswagen Touran 2.0 TDI 122" to 5.5,
            ),
        )
    }
}
