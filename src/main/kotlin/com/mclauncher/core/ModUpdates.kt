package com.mclauncher.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 本地 MOD 的版本检测：从 jar 里读 modid / version，
 * 再去 Modrinth 按游戏版本 + 加载器查最新版，比版本号判断是否有更新。
 */
object ModUpdates {

    data class LocalMod(
        val file: Path,
        val modId: String,
        val version: String,
        val title: String = ""
    )

    data class Update(
        val file: Path,
        val name: String,
        val modId: String,
        val installed: String,
        val latest: String,
        val url: String,
        val fileName: String,
        val projectTitle: String
    )

    /** 读 jar 元数据：Fabric / Forge / NeoForge / 老 mcmod.info 都试一遍 */
    fun scan(modsDir: Path): List<LocalMod> {
        if (!Files.isDirectory(modsDir)) return emptyList()
        val out = ArrayList<LocalMod>()
        val files = runCatching {
            Files.list(modsDir).use { s ->
                s.filter { Files.isRegularFile(it) && it.toString().endsWith(".jar", true) }.toList()
            }
        }.getOrDefault(emptyList())
        for (file in files) {
            runCatching {
                ZipFile(file.toFile()).use { zf ->
                    val fabric = zf.getEntry("fabric.mod.json")
                    if (fabric != null) {
                        val text = zf.getInputStream(fabric).use { it.readBytes().toString(Charsets.UTF_8) }
                        val obj = AppJson.parseToJsonElement(text).jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val version = obj["version"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val title = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (id.isNotBlank()) out += LocalMod(file, id, version, title)
                        return@use
                    }
                    val toml = zf.getEntry("META-INF/mods.toml") ?: zf.getEntry("META-INF/neoforge.mods.toml")
                    if (toml != null) {
                        val text = zf.getInputStream(toml).use { it.readBytes().toString(Charsets.UTF_8) }
                        val id = Regex("""modId\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1).orEmpty()
                        val version = Regex("""version\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1).orEmpty()
                        val name = Regex("""displayName\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1).orEmpty()
                        if (id.isNotBlank()) out += LocalMod(file, id, version, name)
                        return@use
                    }
                    val legacy = zf.getEntry("mcmod.info")
                    if (legacy != null) {
                        val text = zf.getInputStream(legacy).use { it.readBytes().toString(Charsets.UTF_8) }
                        val arr = runCatching { AppJson.parseToJsonElement(text).jsonArray }.getOrNull()
                        val first = arr?.firstOrNull()?.jsonObject
                        val id = first?.get("modid")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val version = first?.get("version")?.jsonPrimitive?.contentOrNull.orEmpty()
                        val name = first?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (id.isNotBlank()) out += LocalMod(file, id, version, name)
                    }
                }
            }.onFailure { AppLog.error("读取 MOD 元数据失败 ${file.fileName}", it) }
        }
        return out
    }

    /** 查 Modrinth：按 modid 搜项目 → 拉对应加载器/版本的发布 → 比分版本号 */
    suspend fun check(modsDir: Path, mcVersion: String, loader: String): List<Update> {
        val loaderFacet = when (loader.lowercase()) {
            "fabric", "quilt" -> "fabric"
            "forge" -> "forge"
            "neoforge" -> "neoforge"
            else -> ""
        }
        val result = ArrayList<Update>()
        for (local in scan(modsDir)) {
            runCatching {
                val projectId = findProject(local) ?: return@runCatching
                val versions = fetchVersions(projectId, mcVersion, loaderFacet)
                val newest = versions.firstOrNull() ?: return@runCatching
                val newestNo = newest["version_number"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (newestNo.isBlank() || newestNo == local.version) return@runCatching
                if (compareVersions(newestNo, local.version) <= 0) return@runCatching
                val files = newest["files"]?.jsonArray
                val file = files?.firstOrNull()?.jsonObject ?: return@runCatching
                val url = file["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val name = file["filename"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (url.isBlank() || name.isBlank()) return@runCatching
                result += Update(
                    file = local.file,
                    name = local.file.fileName.toString(),
                    modId = local.modId,
                    installed = local.version,
                    latest = newestNo,
                    url = url,
                    fileName = name,
                    projectTitle = local.title.ifBlank { local.modId }
                )
            }.onFailure { AppLog.error("检查更新失败 ${local.file.fileName}", it) }
        }
        return result
    }

    private suspend fun findProject(local: LocalMod): String? = ModrinthJson.searchProject(local.modId, local.title)

    private suspend fun fetchVersions(projectId: String, mcVersion: String, loader: String): List<JsonObject> =
        ModrinthJson.projectVersions(projectId, mcVersion, loader)

    /** 粗略比较版本号（1.2.3 / 1.2.3+build / 1.2.3-beta） */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('+').substringBefore('-')
            .split('.', '_').mapNotNull { it.toIntOrNull() }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** 同 modId 的重复文件：只保留最新/最大的那个 */
    fun duplicates(modsDir: Path): List<Pair<Path, Path>> {
        val byId = scan(modsDir).filter { it.modId.isNotBlank() }.groupBy { it.modId }
        val out = ArrayList<Pair<Path, Path>>()
        byId.forEach { (_, list) ->
            if (list.size <= 1) return@forEach
            val keep = list.maxWithOrNull(
                compareBy<LocalMod> { it.version }
                    .thenBy { runCatching { Files.size(it.file) }.getOrDefault(0L) }
            ) ?: return@forEach
            list.filter { it.file != keep.file }.forEach { out += keep.file to it.file }
        }
        // 没有元数据的：按"去掉版本号的文件名"判断
        val unnamed = runCatching {
            Files.list(modsDir).use { s ->
                s.filter { Files.isRegularFile(it) && it.toString().endsWith(".jar", true) }.toList()
            }
        }.getOrDefault(emptyList())
        val withMeta = scan(modsDir).map { it.file }.toSet()
        unnamed.filter { it !in withMeta }.groupBy { normalizeName(it.fileName.toString()) }
            .forEach { (key, list) ->
                if (key.isBlank() || list.size <= 1) return@forEach
                val keep = list.maxByOrNull { runCatching { Files.size(it) }.getOrDefault(0L) } ?: return@forEach
                list.filter { it != keep }.forEach { out += keep to it }
            }
        return out
    }

    private fun normalizeName(name: String): String = name
        .removeSuffix(".disabled")
        .removeSuffix(".jar")
        .replace(Regex("""[-_]?\d+(\.\d+)*([-+][A-Za-z0-9.\-]+)?"""), "")
        .lowercase()
        .trim('-', '_')
}