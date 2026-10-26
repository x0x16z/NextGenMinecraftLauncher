package com.mclauncher.core

import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 下载源策略。auto = 先官方再镜像；official = 只官方；bmcl = 先镜像再官方。
 */
object Mirror {
    const val AUTO = "auto"
    const val OFFICIAL = "official"
    const val BMCL = "bmcl"

    @Volatile
    var mode: String = AUTO

    private const val BMCL_BASE = "https://bmclapi2.bangbang93.com"

    private val replacements = listOf(
        "https://piston-meta.mojang.com" to BMCL_BASE,
        "https://launchermeta.mojang.com" to BMCL_BASE,
        "https://launcher.mojang.com" to BMCL_BASE,
        "https://resources.download.minecraft.net" to "$BMCL_BASE/assets",
        "https://libraries.minecraft.net" to "$BMCL_BASE/maven",
        "https://files.minecraftforge.net/maven" to "$BMCL_BASE/maven",
        "https://maven.minecraftforge.net" to "$BMCL_BASE/maven",
        "https://maven.neoforged.net/releases" to "$BMCL_BASE/maven"
    )

    fun mirrored(url: String): String? {
        for ((from, to) in replacements) {
            if (url.startsWith(from)) return to + url.removePrefix(from)
        }
        return null
    }

    /** 返回按优先级排序的候选地址。 */
    fun candidates(url: String): List<String> {
        val m = mirrored(url) ?: return listOf(url)
        return when (mode) {
            OFFICIAL -> listOf(url)
            BMCL -> listOf(m, url)
            // neoforged 的官方 maven 在国内基本连不上，auto 模式直接先走镜像
            else -> if (url.contains("neoforged")) listOf(m, url) else listOf(url, m)
        }
    }
}

object Http {
    val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    fun text(url: String, headers: Map<String, String> = emptyMap()): String =
        String(bytes(url, headers), Charsets.UTF_8)

    fun json(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        AppJson.parseToJsonElement(text(url, headers))

    /** 带镜像回退的 GET，最多重试 2 轮。 */
    fun bytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val candidates = Mirror.candidates(url)
        var last: Throwable? = null
        repeat(2) {
            for (candidate in candidates) {
                try {
                    return single(candidate, headers)
                } catch (t: Throwable) {
                    last = t
                }
            }
            Thread.sleep(400)
        }
        throw last ?: IllegalStateException("请求失败: $url")
    }

    private fun single(url: String, headers: Map<String, String>): ByteArray {
        val builder = HttpRequest.newBuilder(URI.create(encodeSpaces(url)))
            .timeout(Duration.ofSeconds(60))
            .GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode()} $url")
        }
        return response.body()
    }

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
    }

    fun put(url: String, body: ByteArray, contentType: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", contentType)
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
    }

    fun encodeSpaces(url: String): String = url.replace(" ", "%20").replace("|", "%7C")

    /** 表单 POST，无论状态码如何都返回响应体，方便解析 OAuth 的 error 字段 */
    fun postForm(url: String, fields: Map<String, String>): String {
        val body = fields.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)).body()
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8)
}
