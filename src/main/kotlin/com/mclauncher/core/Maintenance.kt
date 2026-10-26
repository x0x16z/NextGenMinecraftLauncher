package com.mclauncher.core

import java.nio.file.Files
import java.nio.file.Path

/** 清理与日志诊断。 */
object Maintenance {

    fun cacheSize(): Long = Fs.sizeOf(AppPaths.cache)

    fun logSize(): Long = Fs.sizeOf(AppPaths.logs)

    fun clearCaches(): Long {
        val before = cacheSize() + logSize()
        Fs.deleteRecursively(AppPaths.cache)
        runCatching {
            if (Files.isDirectory(AppPaths.logs)) {
                Files.list(AppPaths.logs).use { stream ->
                    stream.toList().forEach { runCatching { Files.deleteIfExists(it) } }
                }
            }
        }
        Fs.ensureDir(AppPaths.cache)
        Fs.ensureDir(AppPaths.logs)
        return before
    }

    data class Diagnosis(val title: String, val detail: String, val severity: String)

    /** 扫最新的游戏日志，按已知错误模式给建议 */
    fun analyzeLatestLog(minecraftRoot: Path): List<Diagnosis> {
        val candidates = listOf(
            minecraftRoot.resolve("logs").resolve("latest.log"),
            AppPaths.logs.resolve("launcher.log")
        ).filter { Files.exists(it) }

        val text = candidates.firstOrNull()?.let { runCatching { Files.readString(it) }.getOrNull() } ?: return listOf(
            Diagnosis("没找到日志", "启动一次游戏后再来分析", "info")
        )

        val findings = mutableListOf<Diagnosis>()
        fun check(pattern: String, title: String, detail: String, severity: String = "warn") {
            if (text.contains(pattern, ignoreCase = true)) findings += Diagnosis(title, detail, severity)
        }

        check(
            "OutOfMemoryError", "内存不足",
            "分配的内存不够。去版本设置里把运行内存调大，或者减少同时装的 MOD。", "error"
        )
        check(
            "UnsupportedClassVersionError", "Java 版本太新/太旧",
            "游戏要求的 Java 版本和当前不一致。到版本设置里换成对应的 Java（老版本用 8，1.17+ 用 17/21）。", "error"
        )
        check(
            "NoClassDefFoundError: net/minecraft", "加载器没装好",
            "看起来是 Forge/Fabric 的库缺失，重新安装一次加载器。", "error"
        )
        check(
            "java.lang.NoSuchMethodError", "MOD 与游戏版本不匹配",
            "有 MOD 编译时的游戏版本和当前不一致，检查 MOD 的适配版本。", "error"
        )
        check(
            "Incompatible mod set", "MOD 依赖冲突",
            "有一组 MOD 互相要求不同版本，按日志里提到的 mod 名字逐个排查。", "error"
        )
        check(
            "Pixel format not accelerated", "显卡驱动问题",
            "更新显卡驱动，或者在启动参数里换用软件渲染。", "warn"
        )
        check(
            "GLFW error", "OpenGL 初始化失败",
            "多半还是显卡驱动，试试更新驱动或关掉 OptiFine 的快速渲染。", "warn"
        )
        check(
            "java.net.ConnectException", "网络连不上",
            "登录或资源下载失败，检查网络/代理，或到设置里切下载源。", "warn"
        )
        check(
            "Failed to download", "资源下载失败",
            "有文件没下全，重新启动一次让它补齐，或切换镜像源。", "warn"
        )
        check(
            "DuplicateModsFoundException", "同一个 MOD 装了两份",
            "mods 文件夹里有重复的 MOD，删掉旧的那份。", "warn"
        )

        if (findings.isEmpty()) {
            findings += Diagnosis("没发现明显问题", "日志里没有命中常见的错误特征。", "ok")
        }
        return findings
    }

    /** 最近一次游戏日志的末尾几行，方便直接看 */
    fun tailLatestLog(minecraftRoot: Path, lines: Int = 12): List<String> {
        val file = minecraftRoot.resolve("logs").resolve("latest.log")
        if (!Files.exists(file)) return emptyList()
        return runCatching {
            Files.readAllLines(file).takeLast(lines)
        }.getOrDefault(emptyList())
    }

    fun openFolderSafe(path: Path) = Fs.openFolder(path)
}
