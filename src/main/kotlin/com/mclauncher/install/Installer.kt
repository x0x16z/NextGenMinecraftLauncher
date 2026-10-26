package com.mclauncher.install

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppLog
import com.mclauncher.core.AppPaths
import com.mclauncher.core.DownloadProgress
import com.mclauncher.core.DownloadTask
import com.mclauncher.core.Downloader
import com.mclauncher.core.Fs
import com.mclauncher.core.Http
import com.mclauncher.core.Hashing
import com.mclauncher.meta.Artifact
import com.mclauncher.meta.AssetIndexRef
import com.mclauncher.meta.Library
import com.mclauncher.meta.Mojang
import com.mclauncher.meta.ResolvedVersion
import com.mclauncher.meta.Rules
import com.mclauncher.meta.VersionDetail
import com.mclauncher.meta.mavenPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class InstallStage(
    val title: String,
    val progress: DownloadProgress? = null,
    val running: Boolean = true
)

data class InstalledVersion(
    val versionId: String,
    val resolved: ResolvedVersion,
    val versionDir: Path,
    val nativesDir: Path,
    val classpath: List<Path>,
    val assetsDir: Path,
    val gameAssetsDir: Path,
    val loggingConfig: Path?
)

/**
 * 原版安装器：客户端 jar + 库 + natives + 资源索引。
 * Forge / Fabric 的额外库会走同一条库下载路径，因为它们的版本 JSON 也合并进了链。
 */
class Installer(
    private val minecraftRoot: Path,
    private val threads: Int = 8,
    private val isCancelled: () -> Boolean = { false }
) {

    private val versionsDir get() = minecraftRoot.resolve("versions")
    private val librariesDir get() = minecraftRoot.resolve("libraries")
    private val assetsDir get() = minecraftRoot.resolve("assets")

    suspend fun install(
        versionId: String,
        onStage: (InstallStage) -> Unit
    ): InstalledVersion = withContext(Dispatchers.IO) {
        onStage(InstallStage("读取版本信息"))
        val manifest = Mojang.manifest()
        val chain = resolveChain(versionId, manifest)
        val resolved = ResolvedVersion.merge(chain)
        AppLog.log("准备安装 ${resolved.id}（基础版本 ${resolved.baseId}）")

        val versionDir = Fs.ensureDir(versionsDir.resolve(versionId))
        // 把链上每个版本的 JSON 都落盘，Forge 等启动时仍需要 inheritsFrom
        for (version in chain) {
            val file = versionsDir.resolve(version.id).resolve("${version.id}.json")
            if (!Files.exists(file)) {
                Fs.writeAtomic(file, AppJson.encodeToString(VersionDetail.serializer(), version).toByteArray())
            }
        }

        val downloader = Downloader(threads, isCancelled)
        val deferred = mutableListOf<DownloadTask>()

        /* ---------- 1. 客户端 jar ---------- */
        val baseDir = Fs.ensureDir(versionsDir.resolve(resolved.baseId))
        val clientJar = baseDir.resolve("${resolved.baseId}.jar")
        val clientArtifact = resolved.downloads?.client
        if (clientArtifact != null && clientArtifact.url.isNotBlank()) {
            deferred += DownloadTask(
                url = clientArtifact.url,
                dest = clientJar,
                sha1 = clientArtifact.sha1.ifBlank { null },
                size = clientArtifact.size,
                label = "客户端核心"
            )
        }

        /* ---------- 2. 库与 natives ---------- */
        val nativesDir = Fs.ensureDir(versionDir.resolve("natives"))
        val classpath = mutableListOf<Path>()
        val nativeJars = mutableListOf<Pair<Path, Library>>()
        val modernNativeJars = mutableListOf<Path>()
        val classpathSeen = HashSet<Path>()

        for (library in resolved.libraries) {
            if (!Rules.allows(library.rules)) continue
            val artifact = library.downloads?.artifact
            val path = artifactPath(library, artifact)
            if (artifact != null && artifact.url.isNotBlank()) {
                deferred += DownloadTask(
                    url = artifact.url,
                    dest = path,
                    sha1 = artifact.sha1.ifBlank { null },
                    size = artifact.size,
                    label = library.name
                )
            } else if (library.downloads?.classifiers.isNullOrEmpty() && library.natives.isEmpty()) {
                // 少数老库只有 url 基址
                val base = library.url.ifBlank { "https://libraries.minecraft.net/" }
                val rel = artifact?.path?.ifBlank { null } ?: mavenPath(library.name)
                deferred += DownloadTask(url = base.trimEnd('/') + "/" + rel, dest = path, label = library.name)
            }
            // 现代版本（1.19+）的 natives 是独立库：org.lwjgl:lwjgl:3.3.3:natives-windows
            val classifier = library.name.split(":").getOrNull(3) ?: ""
            val modernNative = classifier.startsWith("natives-") &&
                classifier.contains(Rules.currentOsName(), ignoreCase = true)

            if (modernNative) {
                // 解压到 natives 根；同时留在 classpath 上，LWJGL 的 classpath 加载器可以兜底
                modernNativeJars.add(path)
                if (classpathSeen.add(path)) classpath.add(path)
            } else if (classpathSeen.add(path)) {
                classpath.add(path)
            }

            // 老式 natives（库自带 natives 映射 + classifiers）
            if (library.natives.isNotEmpty()) {
                val nativeKey = library.natives[Rules.currentOsName()] ?: continue
                val classifier = nativeKey.replace("\${arch}", if (Rules.currentArch() == "x86_64") "64" else "32")
                val nativeArtifact = library.downloads?.classifiers?.get(classifier) ?: continue
                val nativePath = librariesDir.resolve(
                    nativeArtifact.path.ifBlank { mavenPath("${library.name}:$classifier") }
                )
                deferred += DownloadTask(
                    url = nativeArtifact.url,
                    dest = nativePath,
                    sha1 = nativeArtifact.sha1.ifBlank { null },
                    size = nativeArtifact.size,
                    label = library.name + " natives"
                )
                nativeJars += nativePath to library
                if (classpathSeen.add(nativePath)) classpath.add(nativePath)
            }
        }
        // 客户端 jar 放最后（Forge 要求库优先）
        if (Files.exists(clientJar) || clientArtifact != null) classpath.add(clientJar)

        /* ---------- 3. 资源索引 ---------- */
        val gameAssetsDir = assetsDir.resolve("virtual").resolve(resolved.assets ?: "legacy")
        var loggingConfig: Path? = null
        val indexRef = resolved.assetIndex
        if (indexRef != null) {
            // assets/indexes/<id>.json 必须落盘：少了它游戏找不到语言文件和贴图，
            // 表现就是"只有英文、没有中文、方块全是紫黑格"
            val indexPath = assetsDir.resolve("indexes").resolve("${indexRef.id}.json")
            deferred += DownloadTask(
                url = indexRef.url,
                dest = indexPath,
                sha1 = indexRef.sha1.ifBlank { null },
                size = indexRef.size,
                label = "资源索引 ${indexRef.id}.json"
            )
            val index = runCatching { Mojang.assetIndex(indexRef.id, indexRef.url) }.getOrElse {
                AppLog.error("下载资源索引失败", it)
                null
            }
            if (index != null) {
                val objects = assetsDir.resolve("objects")
                for ((name, obj) in index.objects) {
                    if (obj.hash.length < 2) continue
                    val rel = "${obj.hash.substring(0, 2)}/${obj.hash}"
                    deferred += DownloadTask(
                        url = "https://resources.download.minecraft.net/$rel",
                        dest = objects.resolve(rel),
                        sha1 = obj.hash,
                        size = obj.size,
                        label = name
                    )
                }
            }
        }

        /* ---------- 4. 日志配置 ---------- */
        resolved.logging?.let { logging ->
            val client = logging["client"] as? JsonObject
            val file = client?.get("file") as? JsonObject
            val id = (file?.get("id") as? JsonPrimitive)?.content
            val url = (file?.get("url") as? JsonPrimitive)?.content
            if (!id.isNullOrBlank() && !url.isNullOrBlank()) {
                val target = assetsDir.resolve("log_configs").resolve(id)
                deferred += DownloadTask(url = url, dest = target, label = "日志配置")
                loggingConfig = target
            }
        }

        onStage(InstallStage("下载 ${deferred.size} 个文件", DownloadProgress(0, deferred.size, 0, deferred.sumOf { it.size })))
        downloader.run(deferred) { progress ->
            onStage(InstallStage("下载文件", progress))
        }

        /* ---------- 5. 解压 natives ---------- */
        onStage(InstallStage("解压 natives"))
        Fs.deleteRecursively(nativesDir)
        Fs.ensureDir(nativesDir)
        for (jar in modernNativeJars) {
            if (!Files.exists(jar)) continue
            runCatching { extractModernNatives(jar, nativesDir) }
                .onFailure { AppLog.error("解压 natives 失败 ${jar.fileName}", it) }
        }
        for ((jar, library) in nativeJars) {
            if (!Files.exists(jar)) continue
            val excludes = library.extract?.exclude?.ifEmpty { listOf("META-INF/") } ?: listOf("META-INF/")
            runCatching {
                Fs.unzip(jar, nativesDir) { entry -> excludes.none { entry.startsWith(it) } }
            }.onFailure { AppLog.error("解压 natives 失败 ${jar.fileName}", it) }
        }

        /* ---------- 5.5 合并成单版本 JSON，做到"一个版本一个文件夹" ---------- */
        runCatching {
            val merged = com.mclauncher.meta.VersionDetail(
                id = versionId,
                type = resolved.type,
                mainClass = resolved.mainClass,
                inheritsFrom = null,
                arguments = if (resolved.gameArguments.isEmpty() && resolved.jvmArguments.isEmpty()) null
                    else com.mclauncher.meta.Arguments(resolved.gameArguments, resolved.jvmArguments),
                minecraftArguments = resolved.minecraftArguments,
                assetIndex = resolved.assetIndex,
                assets = resolved.assets,
                downloads = resolved.downloads,
                libraries = resolved.libraries,
                javaVersion = resolved.javaVersion,
                releaseTime = resolved.releaseTime,
                logging = resolved.logging
            )
            val mergedFile = versionDir.resolve("$versionId.json")
            Fs.writeAtomic(mergedFile, AppJson.encodeToString(
                com.mclauncher.meta.VersionDetail.serializer(), merged
            ).toByteArray())
            // 客户端 jar 也放进本版本目录
            if (resolved.id != resolved.baseId) {
                val ownJar = versionDir.resolve("$versionId.jar")
                if (!Files.exists(ownJar) && Files.exists(clientJar)) {
                    Files.copy(clientJar, ownJar)
                }
            }
            AppLog.log("已合并版本 JSON：$versionId")
        }.onFailure { AppLog.error("合并版本 JSON 失败", it) }

        /* ---------- 6. 老版本的虚拟资源目录 ---------- */'@'
        if (indexRef != null) {
            val index = runCatching { Mojang.assetIndex(indexRef.id, indexRef.url) }.getOrNull()
            if (index != null && (index.virtual || index.mapToResources)) {
                onStage(InstallStage("整理老版本资源"))
                val objects = assetsDir.resolve("objects")
                for ((name, obj) in index.objects) {
                    val source = objects.resolve("${obj.hash.substring(0, 2)}/${obj.hash}")
                    if (!Files.exists(source)) continue
                    val target = if (index.mapToResources) {
                        minecraftRoot.resolve("resources").resolve(name)
                    } else {
                        gameAssetsDir.resolve(name)
                    }
                    runCatching {
                        Fs.ensureDir(target.parent)
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        // 供 Forge 安装器识别的 profile 占位
        val profiles = minecraftRoot.resolve("launcher_profiles.json")
        if (!Files.exists(profiles)) {
            Fs.writeAtomic(
                profiles,
                """{"profiles":{},"settings":{},"version":3}""".toByteArray()
            )
        }

        onStage(InstallStage("完成", null, running = false))
        Fs.writeAtomic(versionDir.resolve(".ready"), System.currentTimeMillis().toString().toByteArray())
        InstalledVersion(
            versionId = versionId,
            resolved = resolved,
            versionDir = versionDir,
            nativesDir = nativesDir,
            classpath = classpath,
            assetsDir = assetsDir,
            gameAssetsDir = gameAssetsDir,
            loggingConfig = loggingConfig
        )
    }

    private fun artifactPath(library: Library, artifact: Artifact?): Path {
        val rel = artifact?.path?.ifBlank { null } ?: mavenPath(library.name)
        return librariesDir.resolve(rel)
    }

    /** 沿 inheritsFrom 解析整条链（子在前） */
    suspend fun resolveChain(versionId: String, manifest: com.mclauncher.meta.VersionManifest? = null): List<VersionDetail> {
        val mf = manifest ?: Mojang.manifest()
        val chain = mutableListOf<VersionDetail>()
        var currentId: String? = versionId
        var guard = 0
        while (currentId != null && guard++ < 10) {
            val id: String = currentId
            val detail = Mojang.localVersionJson(minecraftRoot, id)
                ?: Mojang.entry(mf, id)?.let { Mojang.version(id, it.url) }
                ?: throw IllegalStateException("找不到版本 $id 的定义")
            chain += detail
            currentId = detail.inheritsFrom
        }
        return chain
    }

    /** 资源是否已经完整（粗略判断，用于首页给个状态提示） */
    fun isInstalled(versionId: String): Boolean {
        val dir = versionsDir.resolve(versionId)
        return Files.exists(dir.resolve("$versionId.json"))
    }

    /** 是否已经完整安装过（有 .ready 标记），启动时用它跳过校验 */
    fun isReady(versionId: String): Boolean {
        val dir = versionsDir.resolve(versionId)
        if (!Files.exists(dir.resolve("$versionId.json")) || !Files.exists(dir.resolve(".ready"))) return false
        // 以前漏解压现代版本的 natives，这里兜底：dll/so/dylib 一个都没有就当作没装好
        val natives = dir.resolve("natives")
        if (!Files.exists(natives)) return false
        val ext = when (Rules.currentOsName()) {
            "windows" -> ".dll"
            "osx" -> ".dylib"
            else -> ".so"
        }
        return com.mclauncher.core.countFilesWithExtension(natives, ext) > 0
    }

    /** 直接从本地 JSON 解析出版本信息，不联网 */
    suspend fun resolve(versionId: String): ResolvedVersion = withContext(Dispatchers.IO) {
        ResolvedVersion.merge(resolveChain(versionId))
    }

    fun assetSize(): Long = Fs.sizeOf(assetsDir)

    companion object {
        fun detectJavaMajorHint(versionId: String): Int = when {
            versionId.startsWith("1.6") || versionId.startsWith("1.7") || versionId.startsWith("1.8") -> 8
            else -> 17
        }

        fun assetUrl(hash: String): String =
            "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash"
    }
    /**
     * 新版 LWJGL 的 natives jar 不是把 dll 放在根目录，而是
     * windows/x64/org/lwjgl/lwjgl.dll 这样的结构。
     * 官方启动器会按当前平台/架构挑出对应文件再拍平，这里照做。
     */
    private fun extractModernNatives(jar: Path, dest: Path) {
        val os = Rules.currentOsName()
        val arch = Rules.currentArch()
        val osDir = when (os) {
            "windows" -> "windows"
            "osx" -> "macos"
            else -> "linux"
        }
        val archDir = when (arch) {
            "arm64" -> "arm64"
            "x86" -> "x86"
            else -> "x64"
        }
        java.util.zip.ZipFile(jar.toFile()).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("META-INF/")) continue
                val parts = name.split('/')
                // 形如 windows/x64/... 的平台目录
                if (parts.size > 2 && parts[0] in setOf("windows", "linux", "macos")) {
                    val sameOs = parts[0] == osDir
                    val sameArch = parts[1].equals(archDir, ignoreCase = true)
                    if (!sameOs || !sameArch) continue
                    val relative = parts.drop(2).joinToString("/")
                    if (relative.isBlank()) continue
                    // 只保留文件名：LWJGL 只在 java.library.path 根目录找 dll
                    val target = dest.resolve(parts.last())
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(target).use { output -> input.copyTo(output) }
                    }
                } else if (parts.size == 1) {
                    // 根目录的 dll / LICENSE 之类，直接放过去
                    val target = dest.resolve(parts[0])
                    Files.createDirectories(target.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(target).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

typealias MojangVersionManifest = com.mclauncher.meta.VersionManifest
