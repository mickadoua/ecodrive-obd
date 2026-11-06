package tech.dm.conso

import android.os.SystemClock

actual fun monotonicMs(): Long = SystemClock.elapsedRealtime()
