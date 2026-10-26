package com.mclauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/** 当前语言包（key -> 文案），由 LauncherRoot 注入 */
val LocalStrings = compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
fun tr(key: String, fallback: String): String = LocalStrings.current[key] ?: fallback
/**
 * 以"原文"为 key 的取词函数：中文原文本身既是 key 也是兜底，
 * 英文包只需要给出 中文 -> English 的映射，缺翻译就自动回落中文。
 */
@Composable
fun t(text: String): String = LocalStrings.current[text] ?: text