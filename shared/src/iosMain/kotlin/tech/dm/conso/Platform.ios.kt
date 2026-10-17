package tech.dm.conso

import platform.Foundation.NSProcessInfo

actual fun monotonicMs(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
