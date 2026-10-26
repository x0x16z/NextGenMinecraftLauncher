package com.mclauncher.meta

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppPaths
import com.mclauncher.core.Fs
import com.mclauncher.core.Http
import com.mclauncher.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Mojang 元数据客户端。所有 JSON 都会落盘缓存，断网时至少还能列出已下载的版本。
 */
object Mojang {

    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private var manifestCache: VersionManifest? = null
    private var manifestTime = 0L

    suspend fun manifest(force: Boolean = false): VersionManifest = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && manifestCache != null && now - manifestTime < 5 * 60_000) return@withContext manifestCache!!
        Fs.ensureDir(AppPaths.cacheVersions)
        val cached = AppPaths.cacheVersions.resolve("version_manifest_v2.json")
        val manifest = runCatching {
            val text = Http.text(MANIFEST_URL)
            Fs.writeAtomic(cached, text.toByteArray())
            AppJson.decodeFromString(VersionManifest.serializer(), text)
        }.getOrElse { e ->
            AppLog.error("获取版本清单失败，使用本地缓存", e)
            if (Files.exists(cached)) {
                AppJson.decodeFromString(VersionManifest.serializer(), Files.readString(cached))
            } else throw e
        }
        manifestCache = manifest
        manifestTime = now
        manifest
    }

    fun entry(manifest: VersionManifest, id: String): ManifestEntry? = manifest.versions.firstOrNull { it.id == id }

    /** 版本 JSON 原文，缓存到 cache/versions/<id>.json */
    suspend fun versionRaw(id: String, url: String): String = withContext(Dispatchers.IO) {
        Fs.ensureDir(AppPaths.cacheVersions)
        val cached = AppPaths.cacheVersions.resolve("$id.json")
        if (Files.exists(cached)) return@withContext Files.readString(cached)
        val text = Http.text(url)
        Fs.writeAtomic(cached, text.toByteArray())
        text
    }

    suspend fun version(id: String, url: String): VersionDetail = withContext(Dispatchers.IO) {
        AppJson.decodeFromString(VersionDetail.serializer(), versionRaw(id, url))
    }

    /** 从公共根目录的 versions/ 读取已安装版本（含 Forge/Fabric 生成出来的） */
    fun localVersionJson(minecraftRoot: Path, id: String): VersionDetail? {
        val file = minecraftRoot.resolve("versions").resolve(id).resolve("$id.json")
        if (!Files.exists(file)) return null
        return runCatching {
            AppJson.decodeFromString(VersionDetail.serializer(), Files.readString(file))
        }.getOrNull()
    }

    fun installedVersionIds(minecraftRoot: Path): List<String> {
        val dir = minecraftRoot.resolve("versions")
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .filter { Files.exists(dir.resolve(it).resolve("$it.json")) }
                .toList()
        }.sortedDescending()
    }

    suspend fun assetIndex(id: String, url: String): AssetIndexDetail = withContext(Dispatchers.IO) {
        Fs.ensureDir(AppPaths.cacheVersions)
        val cached = AppPaths.cacheVersions.resolve("assets-$id.json")
        val text = if (Files.exists(cached)) Files.readString(cached) else {
            val t = Http.text(url)
            Fs.writeAtomic(cached, t.toByteArray())
            t
        }
        AppJson.decodeFromString(AssetIndexDetail.serializer(), text)
    }

    /** Vanilla 客户端 jar 的下载地址 */
    fun clientJarUrl(detail: VersionDetail): String? = detail.downloads?.client?.url?.ifBlank { null }
        ?: detail.downloads?.client?.path?.let { "https://launcher.mojang.com/v1/objects/${detail.downloads?.client?.sha1}/client.jar" }

    fun loggingArgument(detail: VersionDetail): Pair<String, String>? {
        val logging = detail.logging ?: return null
        val client = (logging["client"] as? JsonObject) ?: return null
        val argument = (client["argument"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
        val file = client["file"] as? JsonObject ?: return null
        val id = (file["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
        val url = (file["url"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null
        return argument.replace("\${path}", "") to "$id|$url"
    }
}
