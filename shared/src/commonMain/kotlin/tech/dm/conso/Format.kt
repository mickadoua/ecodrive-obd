package tech.dm.conso

import kotlin.math.abs
import kotlin.math.roundToLong

internal fun Int.hex(width: Int): String = toString(16).uppercase().padStart(width, '0')

internal fun Double.fixed(decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (abs(this) * factor).roundToLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    return "$sign$whole." + (scaled % factor).toString().padStart(decimals, '0')
}
