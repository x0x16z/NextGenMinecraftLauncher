package com.mclauncher.instance

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppPaths
import com.mclauncher.core.Fs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class Instance(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String = "",
    var mcVersion: String = "",
    var loader: String = "vanilla",          // vanilla | fabric | forge | neoforge
    var loaderVersion: String = "",
    var versionId: String = "",              // 实际要启动的版本 JSON id
    var gameDir: String = "",
    var memoryMb: Int = 4096,
    var javaMajor: Int = 0,                  // 0 = 自动
    var javaPath: String = "",
    var jvmArgs: String = "",
    var width: Int = 900,
    var height: Int = 540,
    var created: Long = System.currentTimeMillis(),
    var lastPlayed: Long = 0,
    var iconColor: Long = 0xFF5B8CFF,
    var icon: String = "grass"
) {
    val loaderLabel: String
        get() = when (loader) {
            "fabric" -> "Fabric"
            "forge" -> "Forge"
            "forge_optifine" -> "Forge + OptiFine"
            "neoforge" -> "NeoForge"
            else -> "原版"
        }

    val displayVersion: String
        get() = when {
            mcVersion.isBlank() -> loaderLabel
            loader == "vanilla" -> mcVersion
            else -> "$mcVersion · $loaderLabel"
        }

    fun gameDirPath(): Path {
        val raw = gameDir.ifBlank { Paths.get(AppPaths.instancesDir.toString(), id).toString() }
        return Paths.get(raw)
    }
}

object InstanceStore {
    fun load(): List<Instance> = runCatching {
        val text = Fs.readText(AppPaths.instancesFile) ?: return emptyList()
        AppJson.decodeFromString(ListSerializer(Instance.serializer()), text)
    }.getOrDefault(emptyList())

    fun save(list: List<Instance>) {
        runCatching {
            Fs.writeAtomic(AppPaths.instancesFile, AppJson.encodeToString(ListSerializer(Instance.serializer()), list).toByteArray())
        }
    }

    /** 版本隔离：在版本目录里建好标准子目录（mods / 资源包 / 光影 / 存档…） */
    fun ensureGameDirs(versionDir: Path) {
        Fs.ensureDir(versionDir)
        listOf(
            "mods", "resourcepacks", "shaderpacks", "saves",
            "screenshots", "logs", "config", "crash-reports"
        ).forEach { Fs.ensureDir(versionDir.resolve(it)) }
    }



    /**
     * 合并成单版本 JSON 之后，父版本目录（比如 versions/1.21.11）如果没人再用就删掉，
     * 保证"一个游戏版本 = 一个文件夹"。
     */
    fun deleteUnusedBaseDir(root: Path, baseId: String, keepVersionIds: Set<String>): Boolean {
        if (baseId in keepVersionIds) return false
        val versions = root.resolve("versions")
        val baseDir = versions.resolve(baseId)
        if (!Files.isDirectory(baseDir)) return false
        // 还有别的版本 JSON 继承它？
        val stillNeeded = runCatching {
            Files.list(versions).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .anyMatch { dir ->
                        val json = dir.resolve(dir.fileName.toString() + ".json")
                        if (!Files.exists(json)) false
                        else Files.readString(json).contains("\"inheritsFrom\"\\s*:\\s*\"$baseId\"".toRegex())
                    }
            }
        }.getOrDefault(true)
        if (stillNeeded) return false
        return runCatching { Fs.deleteRecursively(baseDir); true }.getOrDefault(false)
    }

    /**
     * 扫描 .minecraft/versions 下所有带 <id>/<id>.json 的目录，
     * 把启动器还不认识的（手动拷进来的）版本转成 Instance。
     */
    fun scanDiskVersions(root: Path, known: List<Instance>): List<Instance> {
        val versionsDir = root.resolve("versions")
        if (!Files.isDirectory(versionsDir)) return emptyList()
        val knownIds = known.map { it.versionId.ifBlank { it.mcVersion } }.toSet()
        val knownDirs = known.map { it.gameDirPath().toAbsolutePath().normalize().toString().lowercase() }.toSet()
        val result = mutableListOf<Instance>()
        runCatching {
            Files.list(versionsDir).use { stream ->
                stream.filter { Files.isDirectory(it) }.toList()
            }
        }.getOrDefault(emptyList()).forEach { dir ->
            val id = dir.fileName.toString()
            if (id in knownIds) return@forEach
            if (dir.toAbsolutePath().normalize().toString().lowercase() in knownDirs) return@forEach
            val jsonFile = dir.resolve("$id.json")
            if (!Files.exists(jsonFile)) {
                com.mclauncher.core.AppLog.log("跳过版本目录 $id：缺少 $id.json（手动加的版本需要带上版本 JSON）")
                return@forEach
            }
            val text = runCatching { Files.readString(jsonFile) }.getOrNull() ?: return@forEach
            val obj = runCatching { com.mclauncher.core.AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: return@forEach
            fun field(name: String): String? =
                (obj[name] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

            val inherits = field("inheritsFrom")
            val libs = runCatching {
                (obj["libraries"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("name")?.toString()?.trim('"') }
                    ?: emptyList()
            }.getOrDefault(emptyList())
            val mainClass = field("mainClass") ?: ""

            val forgeLib = libs.firstOrNull { it.startsWith("net.minecraftforge:forge:") }
            val neoforgeLib = libs.firstOrNull { it.startsWith("net.neoforged:neoforge:") }
            val fabricLib = libs.firstOrNull { it.startsWith("net.fabricmc:fabric-loader:") }
            val loader = when {
                fabricLib != null -> "fabric"
                neoforgeLib != null || mainClass.contains("neoforged") -> "neoforge"
                forgeLib != null || mainClass.contains("launchwrapper") ||
                    mainClass.contains("minecraftforge") || libs.any { it.contains("FMLTweaker") } -> "forge"
                else -> "vanilla"
            }
            val loaderVersion = when (loader) {
                "fabric" -> fabricLib?.split(":")?.getOrNull(2) ?: ""
                "neoforge" -> neoforgeLib?.split(":")?.getOrNull(2) ?: ""
                "forge" -> forgeLib?.split(":")?.getOrNull(2)?.substringAfter('-') ?: ""
                else -> ""
            }
            var mcVersion = inherits ?: ""
            if (mcVersion.isBlank()) {
                val assets = field("assets") ?: ""
                mcVersion = when {
                    id.matches(Regex("\\d+\\.\\d+(\\.\\d+)?")) -> id
                    assets.matches(Regex("\\d+\\.\\d+(\\.\\d+)?")) -> assets
                    else -> ""
                }
                if (mcVersion.isBlank()) {
                    // 从库里找 minecraft 依赖版本
                    val mc = libs.firstOrNull { it.startsWith("com.mojang:minecraft:") }
                    if (mc != null) mcVersion = mc.split(":")[2]
                }
                if (mcVersion.isBlank()) {
                    // 再不行就读客户端 jar 里的 version.json（原版 jar 都带）
                    val jar = dir.resolve("$id.jar")
                    if (Files.exists(jar)) {
                        mcVersion = runCatching {
                            java.util.zip.ZipFile(jar.toFile()).use { zip ->
                                val entry = zip.getEntry("version.json") ?: return@use ""
                                val text2 = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                                (com.mclauncher.core.AppJson.parseToJsonElement(text2).jsonObject["id"]
                                    as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
                            }
                        }.getOrDefault("")
                    }
                }
            }
            val javaMajor = runCatching {
                (obj["javaVersion"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("majorVersion")?.toString()?.trim('"')?.toIntOrNull() ?: 0
            }.getOrDefault(0)
            val hasOptifine = Files.isDirectory(dir.resolve("mods")) &&
                runCatching {
                    Files.list(dir.resolve("mods")).use { s ->
                        s.anyMatch { it.fileName.toString().startsWith("OptiFine", ignoreCase = true) }
                    }
                }.getOrDefault(false)
            val displayLoader = if (loader == "forge" && hasOptifine) "forge_optifine" else loader
            result += Instance(
                name = id,
                mcVersion = mcVersion,
                loader = displayLoader,
                loaderVersion = loaderVersion,
                versionId = id,
                gameDir = dir.toString(),
                memoryMb = com.mclauncher.core.SysInfo.defaultMemoryMb,
                javaMajor = javaMajor
            )
        }
        return result
    }
}