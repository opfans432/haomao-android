package com.catshelf.reader.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal object NuxtPayloadParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val scriptRegex = Regex(
        """<script[^>]*id=[\"']__NUXT_DATA__[\"'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    fun parse(html: String): JsonObject {
        val raw = scriptRegex.find(html)?.groupValues?.get(1)
            ?: error("页面缺少 Nuxt 数据")
        val table = json.parseToJsonElement(raw) as? JsonArray
            ?: error("Nuxt 数据格式无效")
        return Resolver(table).resolveIndex(0) as? JsonObject
            ?: error("Nuxt 根数据格式无效")
    }

    private class Resolver(private val table: JsonArray) {
        private val cache = mutableMapOf<Int, JsonElement>()
        private val resolving = mutableSetOf<Int>()
        private val wrappers = setOf(
            "ShallowReactive", "Reactive", "Ref", "EmptyRef", "ShallowRef",
            "Readonly", "ShallowReadonly",
        )

        fun resolveIndex(index: Int): JsonElement {
            if (index < 0 || index >= table.size) return JsonNull
            cache[index]?.let { return it }
            if (!resolving.add(index)) return JsonNull
            val value = resolveStored(table[index])
            resolving.remove(index)
            cache[index] = value
            return value
        }

        private fun resolveReference(element: JsonElement): JsonElement {
            val index = (element as? JsonPrimitive)?.intOrNull
            return if (index != null) resolveIndex(index) else resolveStored(element)
        }

        private fun resolveStored(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) -> put(key, resolveReference(value)) }
            }
            is JsonArray -> {
                val tag = (element.firstOrNull() as? JsonPrimitive)?.contentOrNull
                when {
                    tag in wrappers -> element.getOrNull(1)?.let(::resolveReference) ?: JsonNull
                    tag == "Set" -> buildJsonArray {
                        element.drop(1).forEach { add(resolveReference(it)) }
                    }
                    else -> buildJsonArray {
                        element.forEach { add(resolveReference(it)) }
                    }
                }
            }
            else -> element
        }
    }
}
