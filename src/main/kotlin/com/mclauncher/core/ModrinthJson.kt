package com.mclauncher.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** Modrinth 上"检测更新"用到的两个接口 */
object ModrinthJson {

    private const val BASE = "https://api.modrinth.com/v2"

    private fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)

    /** 用 modid / 标题搜项目，返回最匹配的 projectId */
    suspend fun searchProject(modId: String, title: String): String? {
        val queries = listOfNotNull(modId.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() })
        for (q in queries) {
            val url = "$BASE/search?query=${enc(q)}&limit=5&facets=${enc("[[\"project_type:mod\"]]")}"
            val text = runCatching { Http.bytes(url).toString(Charsets.UTF_8) }.getOrNull() ?: continue
            val hits = runCatching { AppJson.parseToJsonElement(text).jsonObject["hits"]?.jsonArray }.getOrNull()
                ?: continue
            val best = hits.firstOrNull { hit ->
                val slug = hit.jsonObject["slug"]?.jsonPrimitive?.contentOrNull.orEmpty()
                slug.equals(q, ignoreCase = true)
            } ?: hits.firstOrNull()
            val id = best?.jsonObject?.get("project_id")?.jsonPrimitive?.contentOrNull
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    /** 拉某个项目在该游戏版本 + 加载器下的全部发布（按时间倒序，Modrinth 默认如此） */
    suspend fun projectVersions(projectId: String, mcVersion: String, loader: String): List<JsonObject> {
        val gv = enc("[\"$mcVersion\"]")
        val ld = if (loader.isBlank()) "" else "&loaders=${enc("[\"$loader\"]")}"
        val url = "$BASE/project/$projectId/version?game_versions=$gv$ld"
        val text = runCatching { Http.bytes(url).toString(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        return runCatching { AppJson.parseToJsonElement(text).jsonArray.map { it.jsonObject } }
            .getOrDefault(emptyList())
    }
}