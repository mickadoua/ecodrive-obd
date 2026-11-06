package tech.dm.conso.data

expect class TextFile(path: String) {
    val path: String
    fun exists(): Boolean
    fun readText(): String
    fun writeText(text: String)
    fun appendText(text: String)
    fun replaceText(text: String)
}
