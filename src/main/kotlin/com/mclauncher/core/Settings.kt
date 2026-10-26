package com.mclauncher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Files

@Serializable
data class Settings(
    val palette: String = "azure",              // azure | sakura | forest | ...
    val mode: String = "auto",                  // auto | light | dark
    val blur: Int = 26,                         // 玻璃模糊强度
    val tint: Long = 0x2EFFFFFF,                // 卡片染色 ARGB
    val dim: Float = 0.12f,
    val animations: Boolean = true,
    val mirror: String = Mirror.AUTO,
    val threads: Int = 8,
    val minecraftRoot: String = AppPaths.defaultMinecraftRoot.toString(),
    val showSnapshots: Boolean = false,
    val lastInstance: String = "",
    /** acrylic = 系统亚克力；live = 现代（实时模糊 + 可选图案）；gradient = 自绘渐变；image = 图片 */
    val backdropMode: String = BACKDROP_LIVE,
    val gradients: List<GradientPreset> = GradientPreset.defaultPresets(),
    val activeGradient: String = "aurora",
    val gradientAlpha: Float = 1f,
    val hideAfterLaunch: Boolean = false,
    val alwaysOnTop: Boolean = false,
    val liveIntervalMs: Int = 140,
    /** 现代模式下的图案主题（none = 纯毛玻璃） */
    val liveTheme: String = "none",
    /** 自定义图片背景 */
    val imagePath: String = "",
    /** 图片模糊方式：all / sidebar / none */
    val imageBlur: String = "all",
    /** 图片模式的模糊强度（和全局 blur 独立） */
    val imageBlurRadius: Int = 24,
    /** 图片模式的暗化（和全局 dim 独立） */
    val imageDim: Float = 0.10f,
    /** 图片铺满方式：crop / fit / stretch / tile */
    val imageScale: String = "crop",
    /** 侧边栏自动折叠 */
    val sidebarAutoCollapse: Boolean = true,
    val language: String = "zh_cn",
    /** 下载限速，单位 KB/s，0 = 不限速 */
    val downloadLimitKbps: Int = 0
) {
    companion object {
        /**
         * 内置的公共 Microsoft 客户端 ID（开源启动器社区长期在用的那个）。
         * 走 /consumers 设备码 + XboxLive.signin，个人微软账号可直接登录，
         * 用户不用自己去 Azure 注册应用。
         */
        const val MSA_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb"

        const val BACKDROP_LIVE = "live"
        const val BACKDROP_GRADIENT = "gradient"
        const val BACKDROP_PATTERN = "pattern"   // 仅用于读取旧配置
        const val BACKDROP_IMAGE = "image"

        const val IMAGE_BLUR_ALL = "all"
        const val IMAGE_BLUR_SIDEBAR = "sidebar"
        const val IMAGE_BLUR_NONE = "none"

        const val SCALE_CROP = "crop"
        const val SCALE_FIT = "fit"
        const val SCALE_STRETCH = "stretch"
        const val SCALE_TILE = "tile"

        fun blank(): Settings = Settings()
    }

    fun gradient(): GradientPreset =
        gradients.firstOrNull { it.id == activeGradient }
            ?: gradients.firstOrNull()
            ?: GradientPreset.defaultPresets().first()

    val useLive: Boolean get() = backdropMode == BACKDROP_LIVE
    val useImage: Boolean get() = backdropMode == BACKDROP_IMAGE && imagePath.isNotBlank()
}

object SettingsStore {

    fun load(): Settings {
        val loaded = runCatching {
            if (!Files.exists(AppPaths.configFile)) return Settings()
            AppJson.decodeFromString<Settings>(Files.readString(AppPaths.configFile))
        }.getOrElse { Settings() }
        // 老配置：pattern / acrylic 都合并进了"现代"
        return when (loaded.backdropMode) {
            Settings.BACKDROP_PATTERN, "acrylic" -> loaded.copy(backdropMode = Settings.BACKDROP_LIVE)
            else -> loaded
        }
    }

    fun save(settings: Settings) {
        runCatching {
            Fs.writeAtomic(
                AppPaths.configFile,
                AppJson.encodeToString(Settings.serializer(), settings).toByteArray()
            )
        }.onFailure { AppLog.error("保存配置失败", it) }
    }
}

/* ---------------- 通用小工具 ---------------- */

fun JsonObject.str(key: String): String? = this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
fun JsonObject.bool(key: String): Boolean = str(key)?.toBooleanStrictOrNull() ?: false
fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()
fun JsonObject.long(key: String): Long? = str(key)?.toLongOrNull()
fun String.ifBlankNull(): String? = if (isBlank()) null else this

/** 把毫秒时间戳变成"3 天前"这种，UI 里到处用。 */
fun humanTime(epochMillis: Long): String {
    if (epochMillis <= 0) return "从未"
    val diff = System.currentTimeMillis() - epochMillis
    val min = diff / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "$min 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        min < 60 * 24 * 30 -> "${min / 60 / 24} 天前"
        else -> "${min / 60 / 24 / 30} 个月前"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format("%.1f %s", v, units[i])
}
