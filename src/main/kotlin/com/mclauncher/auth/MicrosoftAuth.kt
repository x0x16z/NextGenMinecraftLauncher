package com.mclauncher.auth

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppLog
import com.mclauncher.core.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

data class DeviceCodeInfo(
    val userCode: String,
    val deviceCode: String,
    val verificationUri: String,
    val interval: Int,
    val expiresIn: Int,
    val message: String
)

data class MsToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

/**
 * 微软账号登录：设备码流程 -> Xbox Live -> XSTS -> Minecraft Services。
 * 需要在 Azure 注册一个“移动和桌面应用”并允许公共客户端流，把 ID 填进设置。
 */
object MicrosoftAuth {

    private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val SCOPE = "XboxLive.signin offline_access"

    suspend fun requestDeviceCode(clientId: String): DeviceCodeInfo = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "还没有配置微软客户端 ID" }
        val response = Http.postForm(DEVICE_CODE_URL, mapOf("client_id" to clientId, "scope" to SCOPE))
        val json = AppJson.parseToJsonElement(response).jsonObject
        DeviceCodeInfo(
            userCode = json.textOf("user_code") ?: error("设备码响应异常"),
            deviceCode = json.textOf("device_code") ?: error("设备码响应异常"),
            verificationUri = json.textOf("verification_uri") ?: "https://microsoft.com/link",
            interval = json.textOf("interval")?.toIntOrNull() ?: 5,
            expiresIn = json.textOf("expires_in")?.toIntOrNull() ?: 900,
            message = json.textOf("message") ?: ""
        )
    }

    /** 轮询直到用户完成授权。 */
    suspend fun pollToken(
        clientId: String,
        info: DeviceCodeInfo,
        isCancelled: () -> Boolean = { false },
        onStatus: (String) -> Unit = {}
    ): MsToken = withContext(Dispatchers.IO) {
        var interval = info.interval.coerceAtLeast(3)
        val deadline = System.currentTimeMillis() + info.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) throw kotlinx.coroutines.CancellationException("已取消登录")
            delay(interval * 1000L)
            if (isCancelled()) throw kotlinx.coroutines.CancellationException("已取消登录")
            onStatus("等待浏览器中完成授权…")
            val response = Http.postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code" to info.deviceCode
                )
            )
            val json = runCatching { AppJson.parseToJsonElement(response).jsonObject }.getOrNull() ?: continue
            val token = json.textOf("access_token")
            if (token != null) {
                return@withContext MsToken(
                    accessToken = token,
                    refreshToken = json.textOf("refresh_token") ?: "",
                    expiresAt = System.currentTimeMillis() + (json.textOf("expires_in")?.toLongOrNull() ?: 3600) * 1000
                )
            }
            when (json.textOf("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 2
                "expired_token" -> error("设备码已过期，请重试")
                "authorization_declined" -> error("授权被拒绝")
                else -> {
                    val desc = json.textOf("error_description") ?: json.textOf("error")
                    error(desc ?: "登录失败")
                }
            }
        }
        error("授权超时，请重试")
    }

    suspend fun refresh(clientId: String, refreshToken: String): MsToken = withContext(Dispatchers.IO) {
        val response = Http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "scope" to SCOPE
            )
        )
        val json = AppJson.parseToJsonElement(response).jsonObject
        val token = json.textOf("access_token") ?: error(json.textOf("error_description") ?: "刷新登录失败")
        MsToken(
            accessToken = token,
            refreshToken = json.textOf("refresh_token") ?: refreshToken,
            expiresAt = System.currentTimeMillis() + (json.textOf("expires_in")?.toLongOrNull() ?: 3600) * 1000
        )
    }

    /** 用微软 token 换 Minecraft 账密信息 */
    suspend fun loginWithXbox(ms: MsToken): Account = withContext(Dispatchers.IO) {
        // 1. Xbox Live
        val xblBody = """
            {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=${ms.accessToken}"},
             "RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
        """.trimIndent()
        val xbl = AppJson.parseToJsonElement(Http.postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody).body()).jsonObject
        val xblToken = xbl.textOf("Token") ?: error("Xbox Live 认证失败")
        val uhs = xbl["DisplayClaims"]?.jsonObject?.get("xui")
            ?.let { (it as kotlinx.serialization.json.JsonArray)[0].jsonObject.textOf("uhs") }
            ?: error("Xbox Live 返回缺少 uhs")

        // 2. XSTS
        val xstsBody = """
            {"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},
             "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
        """.trimIndent()
        val xstsResponse = Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody)
        if (xstsResponse.statusCode() == 401) {
            val err = AppJson.parseToJsonElement(xstsResponse.body()).jsonObject.textOf("XErr")
            throw IllegalStateException(
                when (err) {
                    "2148916233" -> "该微软账号还没有 Xbox 档案，请先登录 xbox.com 创建"
                    "2148916235" -> "该账号所在地区不支持 Xbox Live"
                    "2148916236", "2148916237" -> "账号需要先完成成人验证"
                    "2148916238" -> "未成年账号需要先加入家庭组"
                    else -> "XSTS 认证失败 ($err)"
                }
            )
        }
        val xsts = AppJson.parseToJsonElement(xstsResponse.body()).jsonObject
        val xstsToken = xsts.textOf("Token") ?: error("XSTS 认证失败")

        // 3. Minecraft Services
        val mcBody = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val mc = AppJson.parseToJsonElement(
            Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody).body()
        ).jsonObject
        val mcToken = mc.textOf("access_token") ?: error("Minecraft 登录失败（账号可能没有游戏本体）")
        val expiresIn = mc.textOf("expires_in")?.toLongOrNull() ?: 86400

        // 4. 档案
        val profile = runCatching { fetchProfile(mcToken) }.getOrNull()
        val name = profile?.textOf("name") ?: error("获取 Minecraft 档案失败")
        val uuid = profile.textOf("id") ?: ""
        val skin = (profile["skins"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonObject
        val cape = (profile["capes"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonObject

        AppLog.log("微软登录成功：$name")
        Account(
            type = Account.TYPE_MICROSOFT,
            name = name,
            uuid = uuid,
            accessToken = mcToken,
            refreshToken = ms.refreshToken,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000,
            skinUrl = skin?.textOf("url") ?: "",
            skinModel = skin?.textOf("variant")?.lowercase() ?: "classic",
            capeUrl = cape?.textOf("url") ?: ""
        )
    }

    suspend fun fetchProfile(mcToken: String): JsonObject = withContext(Dispatchers.IO) {
        AppJson.parseToJsonElement(
            Http.text(
                "https://api.minecraftservices.com/minecraft/profile",
                mapOf("Authorization" to "Bearer $mcToken")
            )
        ).jsonObject
    }

    /** 校验 token 是否仍然可用，失效则用 refresh token 续期 */
    suspend fun ensureValid(account: Account, clientId: String): Account = withContext(Dispatchers.IO) {
        if (account.type != Account.TYPE_MICROSOFT) return@withContext account
        if (account.expiresAt > System.currentTimeMillis() + 60_000) return@withContext account
        if (account.refreshToken.isBlank()) return@withContext account
        runCatching {
            val ms = refresh(clientId, account.refreshToken)
            val renewed = loginWithXbox(ms)
            renewed.copy(id = account.id, addedAt = account.addedAt)
        }.getOrElse {
            AppLog.error("刷新微软登录失败", it)
            account
        }
    }

    fun uploadSkinUrl(): String = "https://api.minecraftservices.com/minecraft/profile/skins"

    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
