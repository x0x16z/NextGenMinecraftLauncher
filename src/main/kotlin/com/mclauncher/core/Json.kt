package com.mclauncher.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull

/** 全局 JSON 配置，容错优先。 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
    coerceInputValues = true
}

/** JsonElement 取字符串，非原始值返回 null */
fun kotlinx.serialization.json.JsonElement.text(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
