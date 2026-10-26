package com.mclauncher.launch

import com.mclauncher.auth.Account
import com.mclauncher.core.AppJson
import com.mclauncher.core.AppLog
import com.mclauncher.core.Fs
import com.mclauncher.meta.Artifact
import com.mclauncher.meta.Library
import com.mclauncher.meta.Mojang
import com.mclauncher.meta.ResolvedVersion
import com.mclauncher.meta.Rule
import com.mclauncher.meta.Rules
import com.mclauncher.meta.mavenPath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class LaunchOptions(
    val versionId: String,
    val gameDir: Path,
    val account: Account,
    val memoryMb: Int = 4096,
    val width: Int = 900,
    val height: Int = 540,
    val fullscreen: Boolean = false,
    val jvmExtra: String = "",
    val clientId: String = "mclauncher",
    /** 目标 Java 大版本，用来挑兼容的 JVM 参数（Java 8 不认 UnlockExperimentalVMOptions） */
    val javaMajor: Int = 21
)

data class LaunchPlan(
    val java: Path,
    val args: List<String>,
    val workDir: Path,
    val logFile: Path
)

/**
 * 构造启动参数并拉起游戏进程。
 * 参数全部走 List 传给 ProcessBuilder，天然不怕路径里的空格与中文。
 */
object Launcher {

    fun buildClasspath(resolved: ResolvedVersion, minecraftRoot: Path): List<Path> {
        val librariesDir = minecraftRoot.resolve("libraries")
        val result = LinkedHashSet<Path>()
        for (library in resolved.libraries) {
            if (!Rules.allows(library.rules)) continue
            val artifact: Artifact? = library.downloads?.artifact
            val rel = artifact?.path?.ifBlank { null } ?: mavenPath(library.name)
            result.add(librariesDir.resolve(rel))
            if (library.natives.isNotEmpty()) {
                val nativeKey = library.natives[Rules.currentOsName()] ?: continue
                val classifier = nativeKey.replace("\${arch}", if (Rules.currentArch() == "x86_64") "64" else "32")
                val nativeArtifact = library.downloads?.classifiers?.get(classifier) ?: continue
                result.add(librariesDir.resolve(nativeArtifact.path.ifBlank { mavenPath("${library.name}:$classifier") }))
            }
        }
        val baseJar = minecraftRoot.resolve("versions").resolve(resolved.baseId).resolve("${resolved.baseId}.jar")
        val ownJar = minecraftRoot.resolve("versions").resolve(resolved.id).resolve("${resolved.id}.jar")
        result.add(if (java.nio.file.Files.exists(ownJar)) ownJar else baseJar)
        return result.toList()
    }

    fun build(java: Path, resolved: ResolvedVersion, minecraftRoot: Path, options: LaunchOptions): LaunchPlan {
        val versionDir = minecraftRoot.resolve("versions").resolve(resolved.id)
        val nativesDir = versionDir.resolve("natives")
        val assetsDir = minecraftRoot.resolve("assets")
        val gameAssetsDir = assetsDir.resolve("virtual").resolve(resolved.assets ?: "legacy")
        val classpath = buildClasspath(resolved, minecraftRoot)
        val classpathString = classpath.joinToString(File.pathSeparator)
        val gameDir = options.gameDir
        val account = options.account

        val placeholders = mapOf(
            "auth_player_name" to account.name,
            "version_name" to resolved.id,
            "game_directory" to gameDir.toString(),
            "assets_root" to assetsDir.toString(),
            "assets_index_name" to (resolved.assets ?: resolved.assetIndex?.id ?: "legacy"),
            "auth_uuid" to account.uuidNoDash,
            "auth_access_token" to account.accessToken.ifBlank { "0" },
            "auth_session" to "token:${account.accessToken}:${account.uuidNoDash}",
            "auth_xuid" to "0",
            "clientid" to options.clientId,
            "user_type" to when (account.type) {
                Account.TYPE_MICROSOFT -> "msa"
                Account.TYPE_YGGDRASIL -> "mojang"
                else -> "legacy"
            },
            "version_type" to resolved.type,
            "user_properties" to "{}",
            "game_assets" to gameAssetsDir.toString(),
            "natives_directory" to nativesDir.toString(),
            "launcher_name" to "MCLauncher",
            "launcher_version" to "1.0.0",
            "classpath" to classpathString,
            "classpath_separator" to File.pathSeparator,
            "library_directory" to minecraftRoot.resolve("libraries").toString(),
            "resolution_width" to options.width.toString(),
            "resolution_height" to options.height.toString()
        )

        val features = mapOf(
            "is_demo_user" to false,
            "has_custom_resolution" to true,
            "has_quick_plays_support" to false,
            "is_quick_play_singleplayer" to false,
            "is_quick_play_multiplayer" to false,
            "is_quick_play_realms" to false
        )

        val jvm = mutableListOf<String>()
        jvm += java.toString()
        jvm += "-Xms${minOf(options.memoryMb, 512)}M"
        jvm += "-Xmx${options.memoryMb}M"
        jvm += "-Dfile.encoding=UTF-8"
        jvm += "-Dstdout.encoding=UTF-8"
        jvm += "-Dstderr.encoding=UTF-8"
        // 老版本 log4j 的 RCE 防护，没坏处
        jvm += "-Dlog4j2.formatMsgNoLookups=true"
        if (options.javaMajor >= 9) {
            jvm += "-XX:+UseG1GC"
            jvm += "-XX:+UnlockExperimentalVMOptions"
            jvm += "-XX:G1NewSizePercent=20"
            jvm += "-XX:G1ReservePercent=20"
            jvm += "-XX:MaxGCPauseMillis=50"
            jvm += "-XX:G1HeapRegionSize=32M"
        } else {
            // Java 8：UnlockExperimentalVMOptions 不存在，带上会直接起不来
            jvm += "-XX:+UseG1GC"
            jvm += "-XX:MaxGCPauseMillis=50"
        }
        if (options.jvmExtra.isNotBlank()) {
            jvm += options.jvmExtra.split(Regex("\\s+")).filter { it.isNotBlank() }
        }

        if (resolved.jvmArguments.isNotEmpty()) {
            jvm += expand(resolved.jvmArguments, placeholders, features)
        } else {
            jvm += "-Djava.library.path=${nativesDir}"
            jvm += "-Dminecraft.launcher.brand=MCLauncher"
            jvm += "-Dminecraft.launcher.version=1.0.0"
            jvm += "-cp"
            jvm += classpathString
        }

        // 某些版本 JSON 缺 java.library.path，补上
        if (jvm.none { it.startsWith("-Djava.library.path") }) {
            jvm += "-Djava.library.path=${nativesDir}"
        }

        val game = mutableListOf<String>()
        if (resolved.gameArguments.isNotEmpty()) {
            game += expand(resolved.gameArguments, placeholders, features)
        } else if (!resolved.minecraftArguments.isNullOrBlank()) {
            game += resolved.minecraftArguments.split(" ")
                .filter { it.isNotBlank() }
                .map { replaceVars(it, placeholders) }
        }
        if (options.fullscreen) game += "--fullscreen"

        val args = mutableListOf<String>()
        args += jvm
        args += resolved.mainClass
        args += game

        return LaunchPlan(
            java = java,
            args = args,
            workDir = gameDir,
            logFile = com.mclauncher.core.AppPaths.logs.resolve("game-${resolved.id}.log")
        )
    }

    private fun replaceVars(input: String, values: Map<String, String>): String {
        var out = input
        for ((key, value) in values) {
            out = out.replace("\${$key}", value)
        }
        return out
    }

    private fun expand(
        entries: List<JsonElement>,
        placeholders: Map<String, String>,
        features: Map<String, Boolean>
    ): List<String> {
        val out = mutableListOf<String>()
        for (entry in entries) {
            when (entry) {
                is JsonPrimitive -> out += replaceVars(entry.content, placeholders)
                is JsonObject -> {
                    val rules = entry["rules"]?.let {
                        runCatching {
                            AppJson.decodeFromJsonElement(ListSerializer(Rule.serializer()), it)
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                    if (!Rules.allows(rules, features)) continue
                    when (val value = entry["value"]) {
                        is JsonPrimitive -> out += replaceVars(value.content, placeholders)
                        is JsonArray -> value.forEach {
                            if (it is JsonPrimitive) out += replaceVars(it.content, placeholders)
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    /**
     * 启动游戏。返回 Process；输出由后台线程持续读走，避免管道塞满。
     */
    fun start(plan: LaunchPlan, onLine: (String) -> Unit = {}): Process {
        Fs.ensureDir(plan.workDir)
        Fs.ensureDir(plan.logFile.parent)
        AppLog.log("启动 ${plan.args.first()} ${plan.args.drop(1).take(2).joinToString(" ")}")
        val builder = ProcessBuilder(plan.args)
            .directory(plan.workDir.toFile())
            .redirectErrorStream(true)
        val process = builder.start()
        Thread {
            runCatching {
                Files.newBufferedWriter(plan.logFile, Charsets.UTF_8).use { writer ->
                    process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        onLine(line)
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "mc-log-${process.pid()}"
        }.start()
        return process
    }

    /** 导出 .bat 启动脚本，方便排查问题或让用户自己加参数 */
    fun exportScript(plan: LaunchPlan, target: Path, versionName: String) {
        val sb = StringBuilder()
        sb.append("@echo off\r\n")
        sb.append("chcp 65001 >nul\r\n")
        sb.append("title $versionName\r\n")
        sb.append("cd /d \"").append(plan.workDir).append("\"\r\n")
        sb.append('"').append(plan.java).append('"')
        for (arg in plan.args.drop(1)) {
            sb.append(" \"").append(arg.replace("\"", "\\\"")).append('"')
        }
        sb.append("\r\n")
        sb.append("echo.\r\n")
        sb.append("echo 游戏已退出，退出码 %ERRORLEVEL%\r\n")
        sb.append("pause\r\n")
        Fs.writeAtomic(target, sb.toString().toByteArray(Charsets.UTF_8))
    }

    fun waitFor(process: Process, seconds: Long = 5): Boolean =
        process.waitFor(seconds, TimeUnit.SECONDS)
}
