package tech.dm.conso.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.double(key: String) = getValue(key).jsonPrimitive.double
internal fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long
internal fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
internal fun JsonObject.array(key: String) = getValue(key).jsonArray
internal fun JsonObject.optDouble(key: String, default: Double) = (get(key) as? JsonPrimitive)?.doubleOrNull ?: default
internal fun JsonObject.longOrNull(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonObject.optBoolean(key: String, default: Boolean) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: default
internal fun JsonObject.optObject(key: String) = get(key) as? JsonObject
internal fun JsonObject.optString(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonArray.doubleOrNull(i: Int) = (get(i) as? JsonPrimitive)?.doubleOrNull

internal fun Number?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
internal fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
