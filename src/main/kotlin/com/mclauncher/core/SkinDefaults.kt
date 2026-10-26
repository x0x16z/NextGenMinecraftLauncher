package com.mclauncher.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * 离线账号可选的"官方角色皮肤"：直接从本地已安装的客户端 jar 里抽取
 * assets/minecraft/textures/entity/player/... 下的默认皮肤（Steve/Alex 等），
 * 抽到 %APPDATA%/MCLauncher/skins/defaults 下面缓存，一次抽取后续直接读。
 */
object SkinDefaults {

    data class Entry(
        val id: String,
        val label: String,
        val file: Path,
        val slim: Boolean
    )

    private val cache = ArrayList<Entry>()

    /** 官方默认皮肤清单（现代版 wide/slim 两套都叫这些名字） */
    private val NAMES = listOf(
        "steve", "alex", "ari", "efe", "kai", "makena", "nur", "sunny", "zuri"
    )

    private fun label(id: String, slim: Boolean): String {
        val base = when (id) {
            "steve" -> "史蒂夫"
            "alex" -> "爱丽克斯"
            "ari" -> "阿里"
            "efe" -> "艾菲"
            "kai" -> "凯"
            "makena" -> "马凯娜"
            "nur" -> "努尔"
            "sunny" -> "桑妮"
            "zuri" -> "祖莉"
            else -> id
        }
        return if (slim) "$base（纤细）" else base
    }

    fun dir(): Path {
        val d = AppPaths.root.resolve("skins").resolve("defaults")
        Fs.ensureDir(d)
        return d
    }

    /** 抽一次、缓存；没有可用的客户端 jar 时返回空列表 */
    fun ensure(): List<Entry> {
        synchronized(cache) {
            if (cache.isNotEmpty()) return cache.toList()
            val out = ArrayList<Entry>()
            val jar = findClientJar() ?: return emptyList()
            java.util.zip.ZipFile(jar.toFile()).use { zf ->
                for (slim in listOf(false, true)) {
                    for (name in NAMES) {
                        val bytes = readSkin(zf, name, slim) ?: continue
                        val target = dir().resolve("${name}${if (slim) "_slim" else ""}.png")
                        runCatching { Files.write(target, bytes) }
                        if (Files.exists(target)) {
                            out += Entry(name + if (slim) "_slim" else "", label(name, slim), target, slim)
                        }
                    }
                }
            }
            cache.clear()
            cache.addAll(out)
            AppLog.log("官方皮肤抽取完成：${out.size} 个（来自 ${jar.fileName}）")
            return out.toList()
        }
    }

    /** 现代版路径 → 老版本路径（1.8 只有 entity/steve.png、entity/alex.png） */
    private fun readSkin(zf: java.util.zip.ZipFile, name: String, slim: Boolean): ByteArray? {
        val modern = "assets/minecraft/textures/entity/player/" +
            (if (slim) "slim/" else "wide/") + "$name.png"
        zf.getEntry(modern)?.let { return zf.getInputStream(it).use { s -> s.readBytes() } }
        if (name == "steve" || name == "alex") {
            val legacy = "assets/minecraft/textures/entity/$name.png"
            zf.getEntry(legacy)?.let { return zf.getInputStream(it).use { s -> s.readBytes() } }
        }
        return null
    }

    /** 在已安装版本里找一个带皮肤贴图的客户端 jar（优先新版本） */
    private fun findClientJar(): Path? {
        val root = AppPaths.defaultMinecraftRoot
        if (!Files.exists(root)) return null
        val candidates = ArrayList<Path>()
        runCatching {
            val versions = root.resolve("versions")
            if (Files.exists(versions)) {
                Files.walk(versions, 3).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".jar", true) }
                        .filter { !it.toString().contains("natives") }
                        .forEach { p -> candidates.add(p) }
                }
            }
        }
        // 大的 jar 更可能是完整客户端
        return candidates
            .sortedByDescending { runCatching { Files.size(it) }.getOrDefault(0L) }
            .firstOrNull { hasSkinAssets(it) }
    }

    private fun hasSkinAssets(jar: Path): Boolean = runCatching {
        java.util.zip.ZipFile(jar.toFile()).use { zf ->
            zf.getEntry("assets/minecraft/textures/entity/steve.png") != null ||
                zf.getEntry("assets/minecraft/textures/entity/player/wide/steve.png") != null
        }
    }.getOrDefault(false)
}