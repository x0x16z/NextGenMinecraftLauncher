package com.mclauncher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files

@Serializable
data class LangMeta(val code: String, val name: String)

/**
 * 文本集中管理：
 * - 内置语言包在 resources/lang/<code>.json
 * - 用户自定义翻译放在 %APPDATA%/MCLauncher/lang/<code>.json，同名 key 覆盖内置
 * 改完在设置里点"重新加载"即可生效，不需要重启。
 */
object Lang {

    private val cache = HashMap<String, Map<String, String>>()

    fun builtinCodes(): List<String> = listOf("zh_cn", "en_us")

    fun displayName(code: String): String = when (code) {
        "zh_cn" -> "简体中文"
        "en_us" -> "English"
        else -> code
    }

    fun load(code: String): Map<String, String> {
        cache[code]?.let { if (it.isNotEmpty()) return it }
        val base = readResource(code)
        val custom = readOverride(code)
        val merged = base + custom
        cache[code] = merged
        return merged
    }

    fun reload(code: String): Map<String, String> {
        cache.remove(code)
        lastError = null
        val loaded = load(code)
        AppLog.log("语言包已重新加载：$code，${loaded.size} 条")
        return loaded
    }

    @Volatile
    var lastError: String? = null
        private set

    /** 去掉 UTF-8 BOM（Windows 记事本保存会带），否则 JSON 解析直接失败 */
    private fun stripBom(text: String): String =
        if (text.startsWith("\uFEFF")) text.substring(1) else text

    private fun parse(text: String): Map<String, String> =
        AppJson.decodeFromString<Map<String, String>>(stripBom(text))

    private fun readResource(code: String): Map<String, String> = runCatching {
        val stream = Lang::class.java.getResourceAsStream("/lang/$code.json") ?: return emptyMap()
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        parse(text)
    }.getOrDefault(emptyMap())

    private fun readOverride(code: String): Map<String, String> = runCatching {
        val file = AppPaths.langDir.resolve("$code.json")
        if (!Files.exists(file)) return emptyMap()
        parse(Files.readString(file))
    }.onFailure {
        lastError = "$code.json 解析失败：${it.message}"
        AppLog.error("语言包解析失败", it)
    }.getOrDefault(emptyMap())

    /** 把当前内置文案导出成模板，方便用户照着改 */
    fun exportTemplate(code: String): java.nio.file.Path {
        Fs.ensureDir(AppPaths.langDir)
        val target = AppPaths.langDir.resolve("$code.json")
        val merged = (readResource(code).ifEmpty { readResource("zh_cn") }) + readOverride(code)
        val json = AppJson.encodeToString<Map<String, String>>(merged)
        Fs.writeAtomic(target, json.toByteArray(Charsets.UTF_8))
        return target
    }

    fun langDir(): java.nio.file.Path {
        Fs.ensureDir(AppPaths.langDir)
        return AppPaths.langDir
    }
}
