package com.mclauncher

import com.mclauncher.core.AppPaths
import com.mclauncher.install.Installer
import com.mclauncher.java.JavaRuntime
import com.mclauncher.meta.Loaders
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * 隐藏的无界面安装模式，用来在没有 GUI 的环境里验证 Fabric / Forge 安装链路。
 *   gradlew run --args="--cli-install 1.21.11 fabric"
 */
object Cli {

    fun maybeRun(args: Array<String>): Boolean {
        if (args.size >= 2 && args[0] == "--export-icon") {
            exportIcon(args[1])
            return true
        }
        if (args.firstOrNull() == "--cli-create-instance") {
            AppPaths.init()
            runBlocking {
                runCatching {
                    val mc = args.getOrElse(1) { "1.21.11" }
                    val loaderName = args.getOrElse(2) { "fabric" }
                    val root = java.nio.file.Paths.get(com.mclauncher.core.SettingsStore.load().minecraftRoot)
                    val loaderVersion = when (loaderName) {
                        "fabric" -> com.mclauncher.meta.Loaders.fabricLoaders(mc).firstOrNull() ?: ""
                        "forge" -> com.mclauncher.meta.Loaders.forgeVersions()[mc]?.firstOrNull() ?: ""
                        else -> ""
                    }
                    var versionId = mc
                    when (loaderName) {
                        "fabric" -> versionId = com.mclauncher.meta.Loaders.installFabric(mc, loaderVersion, root)
                        "forge" -> versionId = com.mclauncher.meta.Loaders.installForge(
                            com.mclauncher.java.JavaRuntime.ensure(17).exe, mc, loaderVersion, root
                        ) { println("[installer] $it") }
                    }
                    val installer = com.mclauncher.install.Installer(root, 8)
                    installer.install(versionId) { s -> println("[cli] ${s.title}") }
                    val gameDir = java.nio.file.Files.createDirectories(root.resolve("versions").resolve(versionId))
                    com.mclauncher.instance.InstanceStore.ensureGameDirs(gameDir)
                    com.mclauncher.instance.InstanceStore.deleteUnusedBaseDir(root, mc, setOf(versionId))
                    val baseJar = root.resolve("versions").resolve(mc).resolve("$mc.jar")
                    val ownJar = gameDir.resolve("$versionId.jar")
                    if (java.nio.file.Files.exists(baseJar) && !java.nio.file.Files.exists(ownJar)) {
                        java.nio.file.Files.copy(baseJar, ownJar)
                    }
                    val instance = com.mclauncher.instance.Instance(
                        name = args.getOrElse(3) { "$mc $loaderName" },
                        mcVersion = mc,
                        loader = loaderName,
                        loaderVersion = loaderVersion,
                        versionId = versionId,
                        gameDir = gameDir.toString(),
                        memoryMb = com.mclauncher.core.SysInfo.defaultMemoryMb
                    )
                    val list = com.mclauncher.instance.InstanceStore.load() + instance
                    com.mclauncher.instance.InstanceStore.save(list)
                    println("[cli] instance created: ${instance.name} -> $gameDir")
                    println("[cli] files: " + java.nio.file.Files.list(gameDir).use { s -> s.map { it.fileName.toString() }.toList() })
                }.onFailure { println("[cli] FAILED: ${it.message}") }
            }
            return true
        }
        if (args.firstOrNull() == "--cli-settings-migrate") {
            AppPaths.init()
            val loaded = com.mclauncher.core.SettingsStore.load()
            println("[settings] loaded ok, language=${loaded.language}, palette=${loaded.palette}")
            com.mclauncher.core.SettingsStore.save(loaded)
            println("[settings] rewritten: " + com.mclauncher.core.AppPaths.configFile)
            return true
        }
        if (args.firstOrNull() == "--cli-devcode") {
            AppPaths.init()
            runBlocking {
                runCatching {
                    val id = com.mclauncher.core.Settings.MSA_CLIENT_ID
                    val info = com.mclauncher.auth.MicrosoftAuth.requestDeviceCode(id)
                    println("[devcode] client=$id")
                    println("[devcode] url=${info.verificationUri} code=${info.userCode} expires=${info.expiresIn}s")
                }.onFailure { println("[devcode] FAILED: ${it.message}") }
            }
            return true
        }
        if (args.firstOrNull() == "--cli-mod-search") {
            AppPaths.init()
            val q = args.getOrElse(1) { "sodium" }
            runBlocking {
                runCatching {
                    val hits = com.mclauncher.mods.ModsApi.searchCurseforge(q, null, null, "relevance", 0, 5)
                    println("[cf] ${hits.size} hits")
                    hits.forEach { println("  ${it.title} (${it.id})") }
                    val first = hits.firstOrNull()
                    if (first != null) {
                        val versions = com.mclauncher.mods.ModsApi.versionsCurseforge(first.id, null, null)
                        println("[cf] ${versions.size} versions, first=${versions.firstOrNull()?.versionNumber} file=${versions.firstOrNull()?.file?.fileName}")
                    }
                }.onFailure { println("[cf] FAILED: ${it.message}") }
            }
            return true
        }
        if (args.firstOrNull() == "--cli-args") {
            AppPaths.init()
            val id = args.getOrElse(1) { "1.8.9" }
            val root = Paths.get(System.getenv("APPDATA") ?: "", ".minecraft")
            runBlocking {
                runCatching {
                    val installer = com.mclauncher.install.Installer(root, 4)
                    val resolved = installer.resolve(id)
                    val major = resolved.javaVersion.majorVersion
                    val java = JavaRuntime.ensure(major).exe
                    val plan = com.mclauncher.launch.Launcher.build(
                        java, resolved, root,
                        com.mclauncher.launch.LaunchOptions(
                            versionId = id,
                            gameDir = root,
                            account = com.mclauncher.auth.Account.offline("TestPlayer"),
                            memoryMb = 2048,
                            javaMajor = major
                        )
                    )
                    println("[args] java=$major exe=$java")
                    println("[args] workDir=${plan.workDir}")
                    plan.args.forEach { println("  $it") }
                }.onFailure { println("[args] FAILED: ${it.message}") }
            }
            return true
        }
        if (args.firstOrNull() != "--cli-install") return false
        AppPaths.init()
        val mc = args.getOrElse(1) { "1.21.11" }
        val loader = args.getOrElse(2) { "fabric" }
        val wantedLoader = args.getOrElse(3) { "" }
        val root = Paths.get(System.getenv("APPDATA") ?: "", ".minecraft")

        runBlocking {
            try {
                val loaderVersion = wantedLoader.ifBlank {
                    when (loader) {
                        "fabric" -> Loaders.fabricLoaders(mc).firstOrNull() ?: ""
                        "forge" -> Loaders.forgeVersions()[mc]?.firstOrNull() ?: ""
                        "neoforge" -> Loaders.neoForgeVersions()[mc]?.firstOrNull() ?: ""
                        else -> ""
                    }
                }
                println("[cli] mc=$mc loader=$loader loaderVersion=$loaderVersion")

                val id = when (loader) {
                    "fabric" -> Loaders.installFabric(mc, loaderVersion, root)
                    "forge" -> Loaders.installForge(JavaRuntime.ensure(17).exe, mc, loaderVersion, root) { println("[installer] $it") }
                    "neoforge" -> Loaders.installNeoForge(JavaRuntime.ensure(21).exe, mc, loaderVersion, root) { println("[installer] $it") }
                    else -> mc
                }
                println("[cli] version id = $id")

                val installed = Installer(root, 8).install(id) { stage ->
                    val p = stage.progress
                    println("[cli] ${stage.title}" + if (p != null) " ${p.done}/${p.total} ${p.message}" else "")
                }
                println("[cli] OK ${installed.versionId} classpath=${installed.classpath.size} natives=${installed.nativesDir}")
            } catch (t: Throwable) {
                println("[cli] FAILED: ${t.javaClass.simpleName}: ${t.message}")
                t.printStackTrace()
            }
        }
        return true
    }

    /**
     * 把 Brand 的多尺寸图标导成 .ico。
     * 用经典的 BMP(BITMAPINFOHEADER + BGRA + AND mask) 结构，
     * 不用 PNG 压缩条目 —— jpackage 只认这种，PNG 条目会报"无法更新图标"。
     */
    private fun exportIcon(path: String) {
        val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
        // 顺便存一张 256 的 PNG，方便外部工具转 ico
        runCatching {
            javax.imageio.ImageIO.write(
                com.mclauncher.core.Brand.icon(256), "png",
                java.io.File(path + ".png")
            )
        }
        val images = sizes.map { size ->
            val img = com.mclauncher.core.Brand.icon(size)
            // 统一成 ARGB
            val argb = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = argb.createGraphics()
            g.drawImage(img, 0, 0, null)
            g.dispose()
            argb
        }

        val entries = images.map { img ->
            val size = img.width
            val maskRow = ((size + 31) / 32) * 4
            val xorSize = size * size * 4
            val andSize = maskRow * size
            val out = java.io.ByteArrayOutputStream()
            val dos = java.io.DataOutputStream(out)
            // BITMAPINFOHEADER
            dos.writeInt(40)
            dos.writeInt(size)
            dos.writeInt(size * 2)
            dos.writeShort(1)
            dos.writeShort(32)
            dos.writeInt(0)
            dos.writeInt(xorSize + andSize)
            dos.writeInt(0); dos.writeInt(0); dos.writeInt(0); dos.writeInt(0)
            // XOR：自下而上 BGRA
            for (y in size - 1 downTo 0) {
                for (x in 0 until size) {
                    val px = img.getRGB(x, y)
                    dos.writeByte(px and 0xFF)          // B
                    dos.writeByte((px shr 8) and 0xFF)  // G
                    dos.writeByte((px shr 16) and 0xFF) // R
                    dos.writeByte((px ushr 24) and 0xFF)// A
                }
            }
            // AND mask：全 0（用 alpha 决定透明）
            val zeros = ByteArray(andSize)
            dos.write(zeros)
            dos.flush()
            out.toByteArray()
        }

        val out = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(out)
        dos.writeShort(0)
        dos.writeShort(1)
        dos.writeShort(sizes.size)
        var offset = 6 + 16 * sizes.size
        sizes.forEachIndexed { index, size ->
            dos.writeByte(if (size >= 256) 0 else size)
            dos.writeByte(if (size >= 256) 0 else size)
            dos.writeByte(0)
            dos.writeByte(0)
            dos.writeShort(1)
            dos.writeShort(32)
            dos.writeInt(entries[index].size)
            dos.writeInt(offset)
            offset += entries[index].size
        }
        entries.forEach { dos.write(it) }
        dos.flush()
        java.io.File(path).apply { parentFile?.mkdirs() }.writeBytes(out.toByteArray())
        println("icon written to $path (${out.size()} bytes)")
    }
}