package tech.dm.conso.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class TextFile actual constructor(actual val path: String) {
    actual fun exists() = NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun readText(): String =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: throw IllegalStateException("Lecture impossible : $path")

    actual fun writeText(text: String) = write(text, atomically = false)

    actual fun replaceText(text: String) = write(text, atomically = true)

    actual fun appendText(text: String) {
        if (!exists()) return writeText(text)
        val data = NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: throw IllegalStateException("Écriture impossible : $path")
        handle.seekToEndReturningOffset(null, null)
        handle.writeData(data, null)
        handle.closeAndReturnError(null)
    }

    private fun write(text: String, atomically: Boolean) {
        if (!NSString.create(string = text).writeToFile(path, atomically, NSUTF8StringEncoding, null)) {
            throw IllegalStateException("Écriture impossible : $path")
        }
    }
}
