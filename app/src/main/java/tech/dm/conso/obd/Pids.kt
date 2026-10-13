package tech.dm.conso.obd

object Pids {
    const val MAP = 0x0B
    const val RPM = 0x0C
    const val SPEED = 0x0D
    const val INTAKE_TEMP = 0x0F
    const val MAF = 0x10
    const val ENGINE_RUNTIME = 0x1F
    const val FUEL_TYPE = 0x51
    const val FUEL_RATE = 0x5E

    fun rpm(d: IntArray) = (256 * d[0] + d[1]) / 4.0
    fun speedKmh(d: IntArray) = d[0].toDouble()
    fun mapKpa(d: IntArray) = d[0].toDouble()
    fun intakeTempC(d: IntArray) = d[0] - 40.0
    fun mafGs(d: IntArray) = (256 * d[0] + d[1]) / 100.0
    fun engineRuntimeS(d: IntArray) = 256 * d[0] + d[1]
    fun fuelRateLh(d: IntArray) = (256 * d[0] + d[1]) / 20.0
}
