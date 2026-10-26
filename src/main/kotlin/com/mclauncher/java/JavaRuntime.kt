package com.mclauncher.java

import com.mclauncher.core.AppLog
import com.mclauncher.core.AppPaths
import com.mclauncher.core.Fs
import com.mclauncher.core.Http
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

data class JavaInstall(
    val exe: Path,
    val major: Int,
    val version: String,
    val vendor: String
) {
    val label: String get() = "Java $major · $vendor"
}

/**
 * Java 运行时探测 + 自动下载。
 * 探测顺序：JAVA_HOME -> 各厂商安装目录 -> 启动器自己下载的目录。
 */
object JavaRuntime {

    @Volatile private var cache: List<JavaInstall>? = null

    fun scan(force: Boolean = false): List<JavaInstall> {
        val cached = cache
        if (!force && cached != null) return cached
        val result = LinkedHashMap<Path, JavaInstall>()
        for (exe in candidates()) {
            if (!Files.exists(exe)) continue
            val info = probe(exe) ?: continue
            result.putIfAbsent(exe.toAbsolutePath().normalize(), info)
        }
        val list = result.values.sortedWith(compareByDescending<JavaInstall> { it.major }.thenBy { it.vendor })
        cache = list
        return list
    }

    fun find(major: Int): JavaInstall? =
        scan().firstOrNull { it.major == major } ?: scan().filter { it.major >= major }.minByOrNull { it.major }

    fun best(): JavaInstall? = scan().maxByOrNull { it.major }

    private fun candidates(): List<Path> {
        val list = mutableListOf<Path>()
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { list.add(Paths.get(it, "bin", "java.exe")) }

        val roots = mutableListOf<Path>()
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val localApp = System.getenv("LOCALAPPDATA") ?: ""
        roots.add(Paths.get(programFiles))
        if (localApp.isNotBlank()) roots.add(Paths.get(localApp, "Programs"))

        val vendorDirs = listOf(
            "Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Zulu", "BellSoft",
            "Amazon Corretto", "Semeru", "RedHat", "Temurin"
        )
        for (root in roots) {
            for (vendor in vendorDirs) {
                val dir = root.resolve(vendor)
                if (!Files.isDirectory(dir)) continue
                runCatching {
                    Files.list(dir).use { stream ->
                        stream.filter { Files.isDirectory(it) }.forEach { list.add(it.resolve("bin").resolve("java.exe")) }
                    }
                }
            }
        }
        if (Files.isDirectory(AppPaths.javaDir)) {
            runCatching {
                Files.list(AppPaths.javaDir).use { stream ->
                    stream.filter { Files.isDirectory(it) }.forEach { list.add(it.resolve("bin").resolve("java.exe")) }
                }
            }
        }
        return list
    }

    fun probe(exe: Path): JavaInstall? {
        return runCatching {
            val process = ProcessBuilder(exe.toString(), "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(8, TimeUnit.SECONDS)
            val firstLine = output.lineSequence().firstOrNull { it.contains("version") } ?: return null
            val version = Regex("version \"([^\"]+)\"").find(firstLine)?.groupValues?.get(1) ?: return null
            val major = parseMajor(version)
            val vendor = when {
                output.contains("Temurin", true) || output.contains("Adoptium", true) -> "Temurin"
                output.contains("Microsoft", true) -> "Microsoft"
                output.contains("Corretto", true) -> "Corretto"
                output.contains("Zulu", true) -> "Zulu"
                output.contains("GraalVM", true) -> "GraalVM"
                output.contains("OpenJDK", true) -> "OpenJDK"
                output.contains("Oracle", true) -> "Oracle"
                else -> "Java"
            }
            JavaInstall(exe, major, version, vendor)
        }.getOrNull()
    }

    fun parseMajor(version: String): Int {
        val parts = version.split(".", "-", "_", "+")
        val first = parts.firstOrNull()?.toIntOrNull() ?: return 8
        return if (first == 1) parts.getOrNull(1)?.toIntOrNull() ?: 8 else first
    }

    /**
     * 自动下载 JRE（Adoptium Temurin），解压到 %APPDATA%/MCLauncher/java。
     */
    suspend fun download(major: Int, onMessage: (String) -> Unit = {}): Path = withContext(Dispatchers.IO) {
        val arch = if (Fs.osArch() == "arm64") "aarch64" else "x64"
        val url = "https://api.adoptium.net/v3/binary/latest/$major/ga/windows/$arch/jre/hotspot/normal/eclipse"
        val target = AppPaths.javaDir.resolve("temurin-$major")
        val existing = runCatching {
            Files.walk(target).use { s ->
                s.filter { it.fileName.toString().equals("java.exe", true) }.findFirst().orElse(null)
            }
        }.getOrNull()
        if (existing != null) return@withContext existing

        onMessage("正在下载 Java $major 运行时…")
        val zip = AppPaths.cache.resolve("temurin-$major.zip")
        val bytes = Http.bytes(url)
        Files.write(zip, bytes)
        AppLog.log("Java $major 下载完成，共 ${bytes.size / 1024 / 1024} MB")
        onMessage("正在解压 Java $major …")
        Fs.deleteRecursively(target)
        Fs.ensureDir(target)
        Fs.unzip(zip, target)
        runCatching { Files.deleteIfExists(zip) }
        val exe = Files.walk(target).use { s ->
            s.filter { it.fileName.toString().equals("java.exe", true) }.findFirst().orElse(null)
        } ?: throw IllegalStateException("Java 解压后找不到 java.exe")
        // 让下一次 scan 能看到它
        cache = null
        exe
    }

    /**
     * 用 Mojang 官方的 Java 运行时（jre-legacy 就是 8）下载。
     * Adoptium 在某些网络下证书会炸，这条走的是和游戏资源同一个 CDN，更稳。
     */
    suspend fun downloadMojang(javaMajor: Int, onMessage: (String) -> Unit = {}): Path = withContext(Dispatchers.IO) {
        val component = when (javaMajor) {
            8 -> "jre-legacy"
            16 -> "java-runtime-alpha"
            17 -> "java-runtime-gamma"
            else -> "java-runtime-delta"
        }
        onMessage("正在获取 Java $javaMajor 运行时清单…")
        val all = com.mclauncher.core.AppJson.parseToJsonElement(
            Http.text("https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json")
        ).jsonObject
        val platform = all["windows-x64"]?.jsonObject ?: error("没有 windows-x64 运行时")
        val entry = platform[component]?.jsonArray?.firstOrNull()?.jsonObject ?: error("没有 $component 组件")
        val manifestUrl = entry["manifest"]?.jsonObject?.get("url")?.toString()?.trim('"') ?: error("清单缺 url")
        val versionName = entry["version"]?.jsonObject?.get("name")?.toString()?.trim('"') ?: "8"

        val target = AppPaths.javaDir.resolve("mojang-$component-$versionName")
        val existing = runCatching {
            Files.walk(target).use { s -> s.filter { it.fileName.toString().equals("java.exe", true) }.findFirst().orElse(null) }
        }.getOrNull()
        if (existing != null) return@withContext existing

        onMessage("正在下载 Java $javaMajor 运行时（官方）…")
        val manifest = com.mclauncher.core.AppJson.parseToJsonElement(Http.text(manifestUrl)).jsonObject
        val files = manifest["files"]?.jsonObject ?: error("运行时清单为空")
        Fs.ensureDir(target)
        var done = 0
        val total = files.size
        for ((path, element) in files) {
            val obj = element.jsonObject
            val type = obj["type"]?.toString()?.trim('"') ?: "file"
            val dest = target.resolve(path)
            if (type == "directory") { Fs.ensureDir(dest); continue }
            val downloads = obj["downloads"]?.jsonObject
            val raw = downloads?.get("raw")?.jsonObject ?: downloads?.get("lzma")?.jsonObject
            val url = raw?.get("url")?.toString()?.trim('"')
            if (url.isNullOrBlank()) continue
            if (Files.exists(dest) && Files.size(dest) > 0) { done++; continue }
            Fs.ensureDir(dest.parent)
            runCatching { Fs.writeAtomic(dest, Http.bytes(url)) }
            done++
            if (done % 40 == 0) onMessage("Java 运行时 $done/$total")
        }
        runCatching { Files.walk(target).use { s -> s.filter { it.fileName.toString().equals("java.exe", true) }.forEach { it.toFile().setExecutable(true) } } }
        val exe = Files.walk(target).use { s -> s.filter { it.fileName.toString().equals("java.exe", true) }.findFirst().orElse(null) }
            ?: throw IllegalStateException("官方运行时里找不到 java.exe")
        cache = null
        exe
    }

    /** 根据版本要求挑一个可用 Java，没有就自动下 */
    suspend fun ensure(major: Int, onMessage: (String) -> Unit = {}): JavaInstall {
        scan().firstOrNull { it.major == major }?.let { return it }
        // Java 8 是硬性要求：1.16.5 及更老在 17/21 上根本起不来，
        // 之前这里会退回"最小的大于等于 8 的版本"，结果拿 Java 21 跑 1.8.9
        if (major <= 8) {
            val mojangExe = runCatching { downloadMojang(8, onMessage) }.getOrNull()
            val exe = mojangExe ?: download(8, onMessage)
            return probe(exe) ?: JavaInstall(exe, 8, "8", "Mojang")
        }
        find(major)?.let { return it }
        val exe = download(major, onMessage)
        return probe(exe) ?: JavaInstall(exe, major, "$major", "Temurin")
    }
}
