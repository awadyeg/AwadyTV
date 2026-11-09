package com.awadytv.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Xtream panels are inconsistent: ids arrive as numbers or strings, `null` and `""` are interchangeable,
 * empty objects are sometimes `[]`. These serializers accept every shape seen in the wild and never throw.
 */
val XtreamJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    allowSpecialFloatingPointValues = true
}

private fun Decoder.element(): JsonElement =
    (this as? JsonDecoder)?.decodeJsonElement() ?: JsonPrimitive(decodeString())

private fun JsonElement.primitiveText(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> contentOrNull?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    else -> null
}

@OptIn(ExperimentalSerializationApi::class)
private abstract class NullableLenient<T : Any>(name: String, kind: PrimitiveKind) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, kind).nullable
    abstract fun parse(element: JsonElement): T?
    abstract fun write(encoder: Encoder, value: T)
    override fun deserialize(decoder: Decoder): T? = runCatching { parse(decoder.element()) }.getOrNull()
    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else write(encoder, value)
    }
}

/** String from string/number/bool; `""`, `null` and objects become null. */
object LenientStringSerializer : KSerializer<String?> by object : NullableLenient<String>("LenientString", PrimitiveKind.STRING) {
    override fun parse(element: JsonElement): String? = element.primitiveText()
    override fun write(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Int from 7, "7", "7.0", true; anything else is null. */
object LenientIntSerializer : KSerializer<Int?> by object : NullableLenient<Int>("LenientInt", PrimitiveKind.INT) {
    override fun parse(element: JsonElement): Int? {
        (element as? JsonPrimitive)?.booleanOrNull?.let { if (!element.isString) return if (it) 1 else 0 }
        return element.primitiveText()?.toDoubleOrNull()?.toInt()
    }
    override fun write(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Long from number or numeric string (ids, unix timestamps). */
object LenientLongSerializer : KSerializer<Long?> by object : NullableLenient<Long>("LenientLong", PrimitiveKind.LONG) {
    override fun parse(element: JsonElement): Long? {
        val text = element.primitiveText() ?: return null
        return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
    }
    override fun write(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** Double from 7.5, "7.5", "7,5"; NaN/blank become null. */
object LenientDoubleSerializer : KSerializer<Double?> by object : NullableLenient<Double>("LenientDouble", PrimitiveKind.DOUBLE) {
    override fun parse(element: JsonElement): Double? =
        element.primitiveText()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    override fun write(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

/** `["a","b"]`, `"a"` or null -> list of non-blank strings (backdrop_path, allowed_output_formats). */
object LenientStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<String> = when (val e = runCatching { decoder.element() }.getOrNull()) {
        is JsonArray -> e.mapNotNull { it.primitiveText() }
        is JsonPrimitive -> listOfNotNull(e.primitiveText())
        else -> emptyList()
    }
    override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)
}

/**
 * List that tolerates `{}` / `null` / object-of-items, and skips individual malformed elements.
 */
open class LenientListSerializer<T>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(elementSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<T> {
        val element = runCatching { decoder.element() }.getOrNull() ?: return emptyList()
        return decodeLenientList(element, elementSerializer)
    }
    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)
}

fun <T> decodeLenientList(element: JsonElement, serializer: KSerializer<T>, json: Json = XtreamJson): List<T> {
    val items: List<JsonElement> = when (element) {
        is JsonArray -> element
        is JsonObject -> element.values.filter { it is JsonObject }
        else -> emptyList()
    }
    return items.mapNotNull { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }
}

/** Object decoded with [serializer], or null when the panel sent `[]`, `""`, or garbage. */
open class ObjectOrNullSerializer<T : Any>(private val serializer: KSerializer<T>) : KSerializer<T?> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = serializer.descriptor.nullable
    override fun deserialize(decoder: Decoder): T? {
        val element = runCatching { decoder.element() }.getOrNull() as? JsonObject ?: return null
        return runCatching { XtreamJson.decodeFromJsonElement(serializer, element) }.getOrNull()
    }
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else serializer.serialize(encoder, value)
    }
}
