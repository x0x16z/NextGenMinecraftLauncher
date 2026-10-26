package com.mclauncher.auth

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppLog
import com.mclauncher.core.Fs
import com.mclauncher.core.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * 外置登录（Yggdrasil），默认适配 LittleSkin。
 */
object Yggdrasil {

    const val LITTLESKIN = "https://littleskin.cn/api/yggdrasil"

    data class Session(val accessToken: String, val clientToken: String, val name: String, val uuid: String)

    suspend fun login(
        apiRoot: String,
        username: String,
        password: String
    ): Session = withContext(Dispatchers.IO) {
        val clientToken = java.util.UUID.randomUUID().toString()
        val body = """
            {"username":"${escape(username)}","password":"${escape(password)}",
             "clientToken":"$clientToken","agent":{"name":"Minecraft","version":1},"requestUser":false}
        """.trimIndent()
        val response = Http.postJson("${apiRoot.trimEnd('/')}/authserver/authenticate", body)
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                when (response.statusCode()) {
                    403 -> "用户名或密码错误"
                    401 -> "验证失败，可能需要在网页端完成邮箱验证"
                    else -> "登录失败 (HTTP ${response.statusCode()})"
                }
            )
        }
        val json = AppJson.parseToJsonElement(response.body()).jsonObject
        val token = json.textOf("accessToken") ?: error("返回缺少 accessToken")
        val profile = json["selectedProfile"]?.jsonObject
            ?: error("该账号还没有创建角色")
        Session(token, clientToken, profile.textOf("name") ?: username, profile.textOf("id") ?: "")
    }

    suspend fun refresh(apiRoot: String, accessToken: String, clientToken: String): Session =
        withContext(Dispatchers.IO) {
            val body = """{"accessToken":"$accessToken","clientToken":"$clientToken","requestUser":false}"""
            val response = Http.postJson("${apiRoot.trimEnd('/')}/authserver/refresh", body)
            if (response.statusCode() !in 200..299) throw IllegalStateException("登录已过期，请重新添加账号")
            val json = AppJson.parseToJsonElement(response.body()).jsonObject
            val profile = json["selectedProfile"]?.jsonObject ?: error("账号没有可用角色")
            Session(
                json.textOf("accessToken") ?: accessToken,
                json.textOf("clientToken") ?: clientToken,
                profile.textOf("name") ?: "",
                profile.textOf("id") ?: ""
            )
        }

    /** 取皮肤地址（带签名），失败返回 null */
    suspend fun skinUrl(apiRoot: String, uuid: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val text = Http.text("${apiRoot.trimEnd('/')}/sessionserver/session/minecraft/profile/$uuid")
            val json = AppJson.parseToJsonElement(text).jsonObject
            val props = json["properties"] as? JsonArray ?: return@runCatching null
            val textures = props.firstOrNull()?.jsonObject?.textOf("value") ?: return@runCatching null
            val decoded = String(java.util.Base64.getDecoder().decode(textures))
            val skin = AppJson.parseToJsonElement(decoded).jsonObject["textures"]?.jsonObject
                ?.get("SKIN")?.jsonObject
            val url = skin?.textOf("url") ?: return@runCatching null
            val model = skin["metadata"]?.jsonObject?.textOf("model") ?: "classic"
            url to model
        }.getOrNull()
    }

    /** LittleSkin 的皮肤上传接口 */
    suspend fun uploadSkin(apiRoot: String, accessToken: String, png: ByteArray, slim: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val boundary = "----MLB" + System.nanoTime()
                val body = buildMultipart(
                    boundary,
                    mapOf("model" to if (slim) "slim" else "classic"),
                    "file", "skin.png", "image/png", png
                )
                val response = Http.put(
                    "${apiRoot.trimEnd('/')}/minecraft/profile/skins",
                    body,
                    "multipart/form-data; boundary=$boundary",
                    mapOf("Authorization" to "Bearer $accessToken")
                )
                if (response.statusCode() !in 200..299) error("上传失败 HTTP ${response.statusCode()}")
                AppLog.log("皮肤上传成功")
                Fs.ensureDir(com.mclauncher.core.AppPaths.cacheSkins)
                Unit
            }
        }

    fun buildMultipart(
        boundary: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        contentType: String,
        fileBytes: ByteArray
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        for ((k, v) in fields) {
            w("--$boundary\r\n")
            w("Content-Disposition: form-data; name=\"$k\"\r\n\r\n")
            w("$v\r\n")
        }
        w("--$boundary\r\n")
        w("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n")
        w("Content-Type: $contentType\r\n\r\n")
        out.write(fileBytes)
        w("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")
}
