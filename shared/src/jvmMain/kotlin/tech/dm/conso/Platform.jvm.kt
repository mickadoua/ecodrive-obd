package tech.dm.conso

actual fun monotonicMs(): Long = System.nanoTime() / 1_000_000
