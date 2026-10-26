package com.mclauncher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object Hashing {
    fun sha1(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

    fun sha1(file: Path): String {
        val md = MessageDigest.getInstance("SHA-1")
        BufferedInputStream(Files.newInputStream(file)).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    fun md5(text: String): String =
        hex(MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)))

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /** 离线登录用的 UUID：和官方一致，基于 OfflinePlayer:<name> 的 MD5 加版本位。 */
    fun offlineUuid(name: String): String {
        val raw = md5("OfflinePlayer:$name").toCharArray()
        raw[12] = '3'
        raw[16] = '4'
        val s = String(raw)
        return "${s.substring(0, 8)}-${s.substring(8, 12)}-${s.substring(12, 16)}-${s.substring(16, 20)}-${s.substring(20, 32)}"
    }
}

object Fs {
    fun ensureDir(path: Path): Path {
        Files.createDirectories(path)
        return path
    }

    fun sizeOf(path: Path): Long = runCatching {
        if (Files.isDirectory(path)) {
            var sum = 0L
            for (p in walk(path)) sum += runCatching { Files.size(p) }.getOrDefault(0L)
            sum
        } else {
            Files.size(path)
        }
    }.getOrDefault(0L)

    fun walk(root: Path): List<Path> =
        runCatching {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) }.toList()
            }
        }.getOrDefault(emptyList())

    fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }

    /** 先写临时文件再原子替换，防止半截写入把配置弄坏。 */
    fun writeAtomic(path: Path, bytes: ByteArray) {
        ensureDir(path.parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.write(tmp, bytes)
        runCatching {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun copy(from: Path, to: Path) {
        ensureDir(to.parent)
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
    }

    /** 解压 zip，带 zip-slip 防护与条目过滤。 */
    fun unzip(zip: Path, target: Path, keep: (String) -> Boolean = { true }) {
        ZipInputStream(Files.newInputStream(zip).buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory || !keep(name)) {
                    zis.closeEntry()
                    continue
                }
                val out = target.resolve(name).normalize()
                if (!out.startsWith(target.normalize())) {
                    zis.closeEntry()
                    continue
                }
                ensureDir(out.parent)
                Files.newOutputStream(out).use { fos -> zis.copyTo(fos, 64 * 1024) }
                zis.closeEntry()
            }
        }
    }

    fun readText(path: Path): String? = runCatching { Files.readString(path) }.getOrNull()

    fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    fun osArch(): String = when (val a = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> a
    }

    fun openFolder(path: Path) {
        runCatching {
            ensureDir(path)
            if (isWindows()) ProcessBuilder("explorer.exe", path.toString()).start()
            else ProcessBuilder("open", path.toString()).start()
        }
    }
}

/** 一个很简单的文件日志 + 内存环形缓冲，供 UI 控制台实时显示。 */
object AppLog {
    private val file: Path = AppPaths.logs.resolve("launcher.log")
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    fun addListener(l: (String) -> Unit) {
        listeners += l
    }

    fun removeListener(l: (String) -> Unit) {
        listeners -= l
    }

    fun log(message: String) {
        val stamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val line = "[$stamp] $message"
        listeners.forEach { runCatching { it(line) } }
        runCatching {
            Fs.ensureDir(AppPaths.logs)
            Files.writeString(
                file, line + System.lineSeparator(),
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            )
        }
    }

    fun error(message: String, t: Throwable? = null) {
        log("ERROR $message${t?.let { " -> " + it.message } ?: ""}")
        t?.printStackTrace()
    }
}

suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/** 让 ProcessBuilder 在 Windows 上干净地拉起子进程。 */
fun ProcessBuilder.startDetached(): Process {
    redirectErrorStream(true)
    return start()
}

fun File.ensureExists(): Boolean = this.exists()

/** 递归数某个扩展名的文件个数 */
fun countFilesWithExtension(dir: java.nio.file.Path, extension: String): Int {
    if (!java.nio.file.Files.isDirectory(dir)) return 0
    return runCatching {
        java.nio.file.Files.walk(dir).use { stream ->
            stream.filter { java.nio.file.Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(extension, ignoreCase = true) }
                .count().toInt()
        }
    }.getOrDefault(0)
}