package com.mclauncher.mods

import com.mclauncher.core.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.mclauncher.core.str
import com.mclauncher.core.text

/** MOD 的依赖声明 */
data class ModDependency(
    val projectId: String?,
    val versionId: String?,
    /** required / optional / incompatible / embedded */
    val kind: String
)

/** 下载计划里的一项（依赖或本体或推荐） */
data class ModPlanItem(
    val role: String,              // self | required | recommended
    val projectId: String,
    val projectTitle: String,
    val version: ModVersionInfo,
    val source: String,            // modrinth / curseforge
    val selected: Boolean = true,
    val note: String = ""
)

data class ModpackInfo(
    val name: String,
    val summary: String,
    val iconUrl: String?,
    val downloads: Long,
    val source: String,
    val projectId: String
)

/** 整合包索引文件（.mrpack 里的 modrinth.index.json） */
data class ModpackIndex(
    val name: String,
    val versionId: String,
    val mcVersion: String,
    val loader: String,
    val loaderVersion: String,
    val files: List<ModpackFile>
)

data class ModpackFile(
    val path: String,
    val url: String,
    val size: Long,
    val sha1: String?
)

object ModpackParser {

    fun parse(indexJson: String): ModpackIndex {
        val root = AppJson.parseToJsonElement(indexJson).jsonObject
        val deps = root["dependencies"] as? JsonObject
        val mc = deps?.str("minecraft") ?: ""
        val fabric = deps?.str("fabric-loader")
        val forge = deps?.str("forge")
        val neo = deps?.str("neoforge")
        val loader = when {
            !fabric.isNullOrBlank() -> "fabric"
            !forge.isNullOrBlank() -> "forge"
            !neo.isNullOrBlank() -> "neoforge"
            else -> "vanilla"
        }
        val loaderVersion = fabric ?: forge ?: neo ?: ""

        val files = mutableListOf<ModpackFile>()
        (root["files"] as? JsonArray)?.forEach { element ->
            val obj = element.jsonObject
            val path = obj.str("path") ?: return@forEach
            val urls = (obj["downloads"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList()
            val size = obj.str("fileSize")?.toLongOrNull() ?: 0L
            val hashes = obj["hashes"] as? JsonObject
            val sha1 = hashes?.str("sha1")
            for (url in urls.take(1)) {
                files += ModpackFile(path, url, size, sha1)
            }
        }
        return ModpackIndex(
            name = root.str("name") ?: "整合包",
            versionId = root.str("versionId") ?: "",
            mcVersion = mc,
            loader = loader,
            loaderVersion = loaderVersion,
            files = files
        )
    }
}
