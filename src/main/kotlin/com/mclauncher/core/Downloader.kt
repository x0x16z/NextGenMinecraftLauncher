package com.mclauncher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DownloadTask(
    val url: String,
    val dest: Path,
    val sha1: String? = null,
    val size: Long = 0,
    val label: String = ""
)

data class DownloadProgress(
    val done: Int = 0,
    val total: Int = 0,
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String = ""
) {
    val fileRatio: Float get() = if (total <= 0) 0f else done.toFloat() / total
    val byteRatio: Float get() = if (totalBytes <= 0) fileRatio else (bytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * 并发下载器：跳过已存在且校验通过的文件，失败重试，支持镜像回退，支持随时中断。
 */
class Downloader(
    private val concurrency: Int = 8,
    private val isCancelled: () -> Boolean = { false }
) {

    private val done = AtomicInteger(0)
    private val total = AtomicInteger(0)
    private val bytes = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    @Volatile private var lastEmit = 0L

    suspend fun run(
        tasks: List<DownloadTask>,
        onProgress: (DownloadProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (tasks.isEmpty()) return@withContext
        done.set(0)
        bytes.set(0)
        total.set(tasks.size)
        totalBytes.set(tasks.sumOf { if (it.size > 0) it.size else 0L })
        lastEmit = 0

        val semaphore = Semaphore(concurrency.coerceIn(1, 32))
        coroutineScope {
            tasks.map { task ->
                async {
                    if (isCancelled()) throw CancellationException("任务已取消")
                    semaphore.withPermit {
                        if (isCancelled()) throw CancellationException("任务已取消")
                        runCatching { downloadOne(task) }
                            .onFailure {
                                if (it is CancellationException) throw it
                                AppLog.error("下载失败 ${task.url.take(120)}", it)
                            }
                        done.incrementAndGet()
                        emit(onProgress, task.label)
                    }
                }
            }.awaitAll()
        }
        emit(onProgress, "完成", force = true)
    }

    private fun emit(onProgress: (DownloadProgress) -> Unit, message: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEmit < 120) return
        lastEmit = now
        onProgress(
            DownloadProgress(
                done = done.get(), total = total.get(),
                bytes = bytes.get(), totalBytes = totalBytes.get(),
                message = message
            )
        )
    }

    private fun isComplete(task: DownloadTask): Boolean {
        val dest = task.dest
        if (!Files.exists(dest)) return false
        val len = Files.size(dest)
        if (task.size > 0 && len != task.size) return false
        val expected = task.sha1
        if (!expected.isNullOrBlank()) {
            return runCatching { Hashing.sha1(dest).equals(expected, ignoreCase = true) }.getOrDefault(false)
        }
        return task.size > 0 || len > 0
    }

    private fun downloadOne(task: DownloadTask) {
        if (isComplete(task)) {
            bytes.addAndGet(if (task.size > 0) task.size else Files.size(task.dest))
            return
        }
        Fs.ensureDir(task.dest.parent)
        val tmp = task.dest.resolveSibling(task.dest.fileName.toString() + ".part")

        val candidates = Mirror.candidates(task.url)
        var last: Throwable? = null
        repeat(3) { attempt ->
            for (candidate in candidates) {
                if (isCancelled()) throw CancellationException("任务已取消")
                try {
                    val request = HttpRequest.newBuilder(URI.create(Http.encodeSpaces(candidate)))
                        .timeout(java.time.Duration.ofMinutes(5))
                        .GET().build()
                    val response = Http.client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                    if (response.statusCode() !in 200..299) {
                        response.body().close()
                        throw IllegalStateException("HTTP ${response.statusCode()}")
                    }
                    response.body().use { input ->
                        Files.newOutputStream(tmp).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                if (isCancelled()) throw CancellationException("任务已取消")
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                bytes.addAndGet(n.toLong())
                                Bandwidth.throttle(n)
                            }
                        }
                    }
                    val expected = task.sha1
                    if (!expected.isNullOrBlank() && !Hashing.sha1(tmp).equals(expected, ignoreCase = true)) {
                        Files.deleteIfExists(tmp)
                        throw IllegalStateException("SHA-1 校验失败")
                    }
                    Files.move(tmp, task.dest, StandardCopyOption.REPLACE_EXISTING)
                    return
                } catch (c: CancellationException) {
                    runCatching { Files.deleteIfExists(tmp) }
                    throw c
                } catch (t: Throwable) {
                    last = t
                    runCatching { Files.deleteIfExists(tmp) }
                    if (attempt == candidates.lastIndex) Thread.sleep(300L * (attempt + 1))
                }
            }
        }
        throw(last ?: IllegalStateException("下载失败 ${task.url}"))
    }
}
