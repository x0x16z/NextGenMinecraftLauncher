package com.mclauncher.auth

import com.mclauncher.core.AppJson
import com.mclauncher.core.Hashing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

@Serializable
data class Account(
    val id: String = java.util.UUID.randomUUID().toString(),
    var type: String = TYPE_OFFLINE,          // offline | microsoft | yggdrasil
    var name: String = "",
    var uuid: String = "",
    var accessToken: String = "",
    var refreshToken: String = "",
    var server: String = "",                   // 外置登录 API 根地址
    var skinModel: String = "classic",
    var skinUrl: String = "",
    var capeUrl: String = "",
    var expiresAt: Long = 0,
    var clientId: String = "",
    var addedAt: Long = System.currentTimeMillis()
) {
    val isOnline: Boolean get() = type != TYPE_OFFLINE
    val uuidNoDash: String get() = uuid.replace("-", "")

    /** 启动器里显示的简称 */
    val badge: String
        get() = when (type) {
            TYPE_MICROSOFT -> "微软"
            TYPE_YGGDRASIL -> if (server.contains("littleskin", true)) "LittleSkin" else "外置"
            else -> "离线"
        }

    companion object {
        const val TYPE_OFFLINE = "offline"
        const val TYPE_MICROSOFT = "microsoft"
        const val TYPE_YGGDRASIL = "yggdrasil"

        fun offline(name: String): Account = Account(
            type = TYPE_OFFLINE,
            name = name,
            uuid = Hashing.offlineUuid(name)
        )
    }
}

object AccountStore {
    fun load(): List<Account> = runCatching {
        val text = com.mclauncher.core.Fs.readText(com.mclauncher.core.AppPaths.accountsFile) ?: return emptyList()
        AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Account.serializer()), text)
    }.getOrDefault(emptyList())

    fun save(accounts: List<Account>) {
        runCatching {
            com.mclauncher.core.Fs.writeAtomic(
                com.mclauncher.core.AppPaths.accountsFile,
                AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Account.serializer()), accounts).toByteArray()
            )
        }
    }
}

data class LoginResult(
    val account: Account,
    val warning: String? = null
)

internal fun JsonObject.textOf(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
