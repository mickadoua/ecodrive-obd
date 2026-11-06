package tech.dm.conso.data

import java.io.File

actual class TextFile actual constructor(actual val path: String) {
    private val file = File(path)

    actual fun exists() = file.exists()
    actual fun readText() = file.readText()
    actual fun writeText(text: String) = file.writeText(text)
    actual fun appendText(text: String) = file.appendText(text)

    actual fun replaceText(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}
