package com.starlr.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 标签反序列化：接受 "a" 或 {"name":"a"} 两种元素形态 */
object TagValueSerializer : KSerializer<TagValue> {
    private val delegate = ListSerializer(JsonElement.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): TagValue {
        val array = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonArray ?: return TagValue("")
        val first = array.firstOrNull() ?: return TagValue("")
        return TagValue(first.extractName())
    }

    override fun serialize(encoder: Encoder, value: TagValue) {
        encoder.encodeSerializableValue(ListSerializer(String.serializer()), listOf(value.name))
    }

    private fun JsonElement.extractName(): String = when (this) {
        is JsonPrimitive -> content
        is JsonObject -> this["name"]?.jsonPrimitive?.content ?: ""
        is JsonArray -> firstOrNull()?.extractName() ?: ""
        is JsonNull -> ""
    }
}

/** 0/1 int 或 true/false bool 的兼容布尔反序列化 */
object FlexibleBoolSerializer : KSerializer<FlexibleBool> {
    override val descriptor: SerialDescriptor = JsonPrimitive.serializer().descriptor

    override fun deserialize(decoder: Decoder): FlexibleBool {
        val prim = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
            ?: return FlexibleBool.FALSE
        val value = prim.booleanOrNull ?: (prim.intOrNull?.let { it != 0 } ?: false)
        return FlexibleBool(value)
    }

    override fun serialize(encoder: Encoder, value: FlexibleBool) {
        encoder.encodeSerializableValue(JsonPrimitive.serializer(), JsonPrimitive(value.value))
    }
}

/** 所有帖子的标签名（便捷扩展） */
val Post.tagNames: List<String> get() = tags.map { it.name }
