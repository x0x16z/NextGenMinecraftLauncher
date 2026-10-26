package com.mclauncher.meta

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppLog
import com.mclauncher.core.Fs
import com.mclauncher.core.Http
import com.mclauncher.core.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Fabric / Forge / NeoForge 的版本列表与安装。
 * Fabric 是纯 JSON 方案；Forge 系直接跑官方 installer，最稳。
 */
object Loaders {

    private const val FABRIC_META = "https://meta.fabricmc.net/v2"
    private const val FORGE_MAVEN = "https://maven.minecraftforge.net"
    private const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases"

    /* ------------------------------ Fabric ------------------------------ */

    /**
     * Fabric 的 /versions/loader/<游戏版本> 返回的是
     * `[{ "loader": { "version": "0.16.14", ... }, "intermediary": {...}, ... }, ...]`，
     * 版本号在 loader 子对象里 —— 之前直接在外层取 version，永远拿到空列表，
     * 于是拼出 `.../loader/<mc>//profile/json` 这种 404 地址。这就是 Fabric 装不上的原因。
     */
    suspend fun fabricLoaders(gameVersion: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val array = AppJson.parseToJsonElement(Http.text("$FABRIC_META/versions/loader/$gameVersion")).jsonArray
            array.mapNotNull { item ->
                val obj = item.jsonObject
                obj["loader"]?.jsonObject?.str("version") ?: obj.str("version")
            }
                .distinct()
                .sortedWith { a, b -> compareLoaderVersions(b, a) }
        }.getOrElse {
            AppLog.error("获取 Fabric 加载器列表失败", it)
            emptyList()
        }
    }

    /** 写入 versions/<id>/<id>.json，返回版本 id */
    suspend fun installFabric(
        gameVersion: String,
        loaderVersion: String,
        minecraftRoot: Path
    ): String = withContext(Dispatchers.IO) {
        val url = "$FABRIC_META/versions/loader/$gameVersion/$loaderVersion/profile/json"
        val text = Http.text(url)
        val json = AppJson.parseToJsonElement(text).jsonObject
        val id = json.str("id") ?: "fabric-loader-$loaderVersion-$gameVersion"
        val dir = Fs.ensureDir(minecraftRoot.resolve("versions").resolve(id))
        Fs.writeAtomic(dir.resolve("$id.json"), text.toByteArray(Charsets.UTF_8))
        AppLog.log("已写入 Fabric 版本 $id")
        id
    }

    /* ------------------------------ Forge ------------------------------ */

    /**
     * 返回 mc 版本 -> 可用的 Forge 版本（倒序）。
     *
     * files.minecraftforge.net 的 maven-metadata.json 里，key 既可能是 "1.20.1"，
     * 也可能是完整的 "1.20.1-47.4.28"；value 里的条目是 "1.20.1-47.4.28" 这种全名。
     * 早先直接按纯数字过滤，导致一个版本都取不到 —— 这就是"装不了 Forge"的真正原因。
     */
    suspend fun forgeVersions(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = AppJson.parseToJsonElement(
                Http.text("https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json")
            ).jsonObject

            val mcKey = Regex("^(1\\.\\d+(\\.\\d+)?|\\d{2}w\\d{2}[a-z])$")
            val result = LinkedHashMap<String, List<String>>()
            for ((key, value) in json) {
                if (!mcKey.matches(key)) continue
                val list = (value as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                } ?: continue
                val versions = list
                    .filter { it.startsWith("$key-") }
                    .map { it.removePrefix("$key-") }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedWith { a, b -> compareLoaderVersions(b, a) }
                if (versions.isNotEmpty()) result[key] = versions
            }
            AppLog.log("Forge 版本表已加载：${result.size} 个游戏版本")
            result
        }.getOrElse {
            AppLog.error("获取 Forge 版本列表失败", it)
            emptyMap()
        }
    }

    suspend fun installForge(
        java: Path,
        gameVersion: String,
        forgeVersion: String,
        minecraftRoot: Path,
        onLine: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val full = "$gameVersion-$forgeVersion"
        val fileName = "forge-$full-installer.jar"
        val url = "$FORGE_MAVEN/net/minecraftforge/forge/$full/$fileName"
        val expectedId = "$gameVersion-forge-$forgeVersion"
        if (Files.exists(minecraftRoot.resolve("versions").resolve(expectedId).resolve("$expectedId.json"))) {
            return@withContext expectedId
        }
        val jar = minecraftRoot.resolve("cache").let { Fs.ensureDir(it) }.resolve(fileName)
        onLine("下载 Forge 安装器 $full")
        Fs.writeAtomic(jar, Http.bytes(url))
        if (!supportsClientInstall(java, jar)) {
            // 老版本的 Forge 安装器（1.8.9 等）没有 --installClient，只能解包 profile 自己装
            onLine("这个版本没有命令行安装，改用解包方式安装 Forge")
            val id = legacyForgeProfile(jar, minecraftRoot, expectedId)
            materializeForgeUniversal(minecraftRoot, full, onLine)
            runCatching { Files.deleteIfExists(jar) }
            return@withContext id
        }
        onLine("运行 Forge 安装器（首次会下载较多依赖）")
        runInstaller(java, jar, minecraftRoot, onLine)
        runCatching { Files.deleteIfExists(jar) }
        detectedVersion(minecraftRoot, expectedId) ?: expectedId
    }

    /* ------------------------------ NeoForge ------------------------------ */

    /** mc 版本 -> NeoForge 版本列表 */
    suspend fun neoForgeVersions(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val xml = Http.text("https://bmclapi2.bangbang93.com/maven/net/neoforged/neoforge/maven-metadata.xml")
            val versions = Regex("<version>([^<]+)</version>").findAll(xml)
                .map { it.groupValues[1] }
                .filter { it.matches(Regex("\\d+\\.\\d+(\\.\\d+)?")) }
                .toList()
                .sortedDescending()
            versions.groupBy { neoforgeToMc(it) ?: "" }.filterKeys { it.isNotBlank() }
        }.getOrElse {
            AppLog.error("获取 NeoForge 版本列表失败", it)
            emptyMap()
        }
    }

    /** 21.1.66 -> 1.21.1，20.2.59 -> 1.20.2，21.0.5 -> 1.21 */
    fun neoforgeToMc(version: String): String? {
        val parts = version.split(".")
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        if (major < 20) return null
        return if (minor == 0) "1.$major" else "1.$major.$minor"
    }

    suspend fun installNeoForge(
        java: Path,
        gameVersion: String,
        version: String,
        minecraftRoot: Path,
        onLine: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val expectedId = "neoforge-$version"
        if (Files.exists(minecraftRoot.resolve("versions").resolve(expectedId).resolve("$expectedId.json"))) {
            return@withContext expectedId
        }
        val fileName = "neoforge-$version-installer.jar"
        val url = "$NEOFORGE_MAVEN/net/neoforged/neoforge/$version/$fileName"
        val jar = Fs.ensureDir(minecraftRoot.resolve("cache")).resolve(fileName)
        onLine("下载 NeoForge 安装器 $version")
        Fs.writeAtomic(jar, Http.bytes(url))
        onLine("运行 NeoForge 安装器（首次会下载较多依赖）")
        runInstaller(java, jar, minecraftRoot, onLine)
        runCatching { Files.deleteIfExists(jar) }
        detectedVersion(minecraftRoot, expectedId) ?: expectedId
    }

    /* ------------------------------ 公共 ------------------------------ */

    /** 47.4.9 < 47.4.28 这种数字版本比较 */
    private fun compareLoaderVersions(a: String, b: String): Int {
        val pa = a.split('.').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return a.compareTo(b)
    }


    /* ------------------------------ OptiFine ------------------------------ */

    data class OptifineBuild(val type: String, val patch: String, val filename: String, val forge: String)

    /** 用 BMCLAPI 的 OptiFine 列表（国内可用，返回 JSON） */
    suspend fun optifineBuilds(mc: String): List<OptifineBuild> = withContext(Dispatchers.IO) {
        runCatching {
            val text = Http.text("https://bmclapi2.bangbang93.com/optifine/$mc")
            val arr = AppJson.parseToJsonElement(text).jsonArray
            arr.mapNotNull { element ->
                val o = element.jsonObject
                val type = o["type"]?.toString()?.trim('"') ?: return@mapNotNull null
                val patch = o["patch"]?.toString()?.trim('"') ?: return@mapNotNull null
                val filename = o["filename"]?.toString()?.trim('"') ?: return@mapNotNull null
                val forge = o["forge"]?.toString()?.trim('"') ?: ""
                OptifineBuild(type, patch, filename, forge)
            }
        }.getOrElse {
            AppLog.error("获取 OptiFine 列表失败", it)
            emptyList()
        }
    }

    /** 下一份 OptiFine 到实例的 mods 目录（Forge 会把它当普通 MOD 加载） */
    suspend fun installOptifine(
        gameDir: Path,
        mc: String,
        wantType: String = "",
        wantPatch: String = "",
        onLine: (String) -> Unit = {}
    ): Path? = withContext(Dispatchers.IO) {
        val builds = optifineBuilds(mc)
        val pick = builds.firstOrNull { it.type == wantType && it.patch == wantPatch }
            ?: builds.firstOrNull()
        if (pick == null) {
            onLine("没找到 $mc 的 OptiFine 版本")
            return@withContext null
        }
        onLine("准备安装 ${pick.filename}")
        val url = "https://bmclapi2.bangbang93.com/optifine/$mc/${pick.type}/${pick.patch}"
        val mods = Fs.ensureDir(gameDir.resolve("mods"))
        val target = mods.resolve(pick.filename)
        runCatching { Fs.writeAtomic(target, Http.bytes(url)) }
            .onFailure {
                AppLog.error("下载 OptiFine 失败", it)
                onLine("下载 OptiFine 失败：${it.message}")
                return@withContext null
            }
        onLine("OptiFine 已放进 mods：${pick.filename}")
        target
    }

    /* ------------------------------ 老版 Forge 兼容 ------------------------------ */

    /** 跑一下 --help，看安装器认不认 --installClient */
    private fun supportsClientInstall(java: Path, jar: Path): Boolean {
        return runCatching {
            val process = ProcessBuilder(java.toString(), "-jar", jar.toString(), "--help")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (!process.waitFor(25, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                text.contains("installClient", ignoreCase = true)
            }
        }.getOrDefault(false)
    }

    /** 从安装器里抽出 install_profile.json 的 versionInfo，直接落成版本 JSON */
    private fun legacyForgeProfile(jar: Path, minecraftRoot: Path, fallbackId: String): String {
        java.util.zip.ZipFile(jar.toFile()).use { zip ->
            val entry = zip.getEntry("install_profile.json")
                ?: zip.getEntry("version.json")
                ?: error("安装器里没有 install_profile.json")
            val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            val obj = AppJson.parseToJsonElement(text).jsonObject
            val info = (obj["versionInfo"] as? kotlinx.serialization.json.JsonObject)
            val json = info?.toString() ?: text
            val id = runCatching {
                AppJson.parseToJsonElement(json).jsonObject["id"]?.toString()?.trim('"')
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackId
            val dir = Fs.ensureDir(minecraftRoot.resolve("versions").resolve(id))
            java.nio.file.Files.writeString(dir.resolve("$id.json"), json)
            return id
        }
    }

    /** 老版本 Forge 的核心是 universal jar，安装器里那份或从 maven 拉一份 */
    private suspend fun materializeForgeUniversal(
        minecraftRoot: Path,
        full: String,
        onLine: (String) -> Unit
    ) {
        val target = minecraftRoot.resolve("libraries")
            .resolve("net/minecraftforge/forge/$full/forge-$full.jar")
        if (Files.exists(target)) return
        Fs.ensureDir(target.parent)
        val url = "$FORGE_MAVEN/net/minecraftforge/forge/$full/forge-$full-universal.jar"
        onLine("下载 Forge universal $full")
        runCatching { Fs.writeAtomic(target, Http.bytes(url)) }
            .onFailure { onLine("universal 下载失败：${it.message}") }
    }
    private fun runInstaller(java: Path, jar: Path, minecraftRoot: Path, onLine: (String) -> Unit) {
        val process = ProcessBuilder(
            java.toString(), "-jar", jar.toString(),
            "--installClient", minecraftRoot.toString()
        ).directory(minecraftRoot.toFile())
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            onLine(line)
            AppLog.log("[installer] $line")
        }
        val finished = process.waitFor(20, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            error("安装器超时")
        }
        if (process.exitValue() != 0) {
            error("安装器退出码 ${process.exitValue()}，请查看日志")
        }
    }

    private fun detectedVersion(minecraftRoot: Path, expected: String): String? {
        val dir = minecraftRoot.resolve("versions")
        if (!Files.exists(dir.resolve(expected).resolve("$expected.json"))) {
            // 有些版本的 id 拼法不同，扫一遍最新的
            val candidates = Files.list(dir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .sorted(compareByDescending { Files.getLastModifiedTime(it).toMillis() })
                    .limit(5).toList()
            }
            for (candidate in candidates) {
                val id = candidate.fileName.toString()
                if (Files.exists(candidate.resolve("$id.json"))) return id
            }
            return null
        }
        return expected
    }
}
