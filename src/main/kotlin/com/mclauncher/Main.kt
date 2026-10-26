package com.mclauncher

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import androidx.compose.ui.window.rememberWindowState
import com.mclauncher.core.AppPaths
import com.mclauncher.ui.AppState
import com.mclauncher.ui.LauncherRoot
import com.mclauncher.ui.theme.LauncherTheme
import java.awt.Dimension
import java.awt.GraphicsEnvironment

fun main(args: Array<String>) {
    if (Cli.maybeRun(args)) return
    AppPaths.init()
    application {
        val app = remember { AppState() }

        val state = rememberWindowState(
            size = DpSize(960.dp, 620.dp),
            position = WindowPosition(Alignment.Center)
        )

        Window(
            onCloseRequest = { app.requestExit() },
            state = state,
            title = "NGML",
            undecorated = true,
            transparent = true,
            resizable = true
        ) {
            // 尺寸用 dp 固定（1100x690 在 1080p / 100%~150% 缩放下都能完整显示），
            // 再让 AWT 按自己的坐标体系居中，避免 DPI 换算把窗口推出屏幕。
            LaunchedEffect(Unit) {
                // 调试用：直接打开某个二级弹窗，方便截图检查
                when (System.getenv("NGML_DEBUG_SHEET")) {
                    "1" -> { app.screen = com.mclauncher.ui.Screen.INSTANCES; app.openCreateInstance = true }
                    "tools" -> { app.screen = com.mclauncher.ui.Screen.TOOLS }
                    "accounts", "skin" -> { app.screen = com.mclauncher.ui.Screen.ACCOUNTS }
                    "downloads" -> { app.showDownloads = true }
                    "confirm" -> {
                        app.screen = com.mclauncher.ui.Screen.INSTANCES
                        app.contentOf = app.instances.firstOrNull()
                        app.confirmDelete("删除 sodium-0.6.9.jar？", "会从当前版本目录里永久删除，不能恢复。") {
                            com.mclauncher.core.AppLog.log("DEBUG confirm delete executed")
                        }
                    }
                    "content" -> {
                        app.screen = com.mclauncher.ui.Screen.INSTANCES
                        app.contentOf = app.instances.firstOrNull()
                    }
                    "dldetail" -> {
                        app.showDownloads = true
                        val a = app.addDownloadTask("mod", "Sodium", "MOD 下载")
                        app.patchDownloadTask(a) {
                            it.total = 3_200_000; it.done = 1_150_000; it.message = "sodium-fabric.jar"
                            it.detail = listOf("来源" to "Modrinth", "游戏版本" to "1.21.11", "加载器" to "Fabric")
                        }
                        app.downloadsDetailId = a
                    }
                    "dlfake" -> {
                        app.screen = com.mclauncher.ui.Screen.HOME
                        app.showDownloads = true
                        val a = app.addDownloadTask("version", "LatestMC", "游戏版本 · 1.21.11 · Fabric")
                        app.patchDownloadTask(a) { it.total = 268_000_000; it.done = 97_000_000; it.message = "下载资源文件" }
                        val b = app.addDownloadTask("mod", "Sodium", "MOD 下载")
                        app.patchDownloadTask(b) { it.total = 2_400_000; it.done = 940_000; it.message = "sodium-fabric-0.6.9.jar" }
                        val c2 = app.addDownloadTask("mod", "Iris Shaders", "MOD 下载")
                        app.patchDownloadTask(c2) { it.total = 8_100_000; it.done = 8_100_000; it.status = "done"; it.message = "已完成" }
                        val d = app.addDownloadTask("mod", "Xaero Minimap", "MOD 下载")
                        app.patchDownloadTask(d) { it.total = 5_000_000; it.done = 1_200_000; it.status = "failed"; it.message = "连接超时" }
                    }
                    "reloadlang" -> {
                        app.screen = com.mclauncher.ui.Screen.SETTINGS
                        app.scope.launch {
                            kotlinx.coroutines.delay(4000)
                            app.reloadLanguage()
                            com.mclauncher.core.AppLog.log("DEBUG reload done, strings=${app.strings.size}, 更换皮肤=${app.strings["更换皮肤"]}")
                        }
                    }
                    "settings" -> { app.screen = com.mclauncher.ui.Screen.SETTINGS }
                    "about" -> { app.showAbout = true }
                    "versions" -> { app.screen = com.mclauncher.ui.Screen.INSTANCES }
                    "graddel" -> {
                        app.screen = com.mclauncher.ui.Screen.SETTINGS
                        app.deletingGradient = app.settings.gradients.firstOrNull { it.id.startsWith("custom-") }
                    }
                    "wizard" -> {
                        app.screen = com.mclauncher.ui.Screen.MODS
                        app.scope.launch {
                            val found = com.mclauncher.mods.ModsApi.searchModrinth(
                                "sodium", null, "fabric", emptyList(), "relevance", 0, 5
                            )
                            found.firstOrNull()?.let {
                                app.modWizard = com.mclauncher.ui.ModWizardRequest(it, "1.20.1", "fabric")
                            }
                        }
                    }
                    "roach" -> { app.screen = com.mclauncher.ui.Screen.SETTINGS; app.showRoach = true }
                }
                window.minimumSize = Dimension(880, 560)
                // Compose 在高 DPI 下按逻辑像素居中会偏，这里直接钉在左上角附近
                window.setLocation(24, 24)
            }
            window.iconImage = com.mclauncher.core.Brand.icon(128)
            LaunchedEffect(app.settings.alwaysOnTop) {
                window.isAlwaysOnTop = app.settings.alwaysOnTop
            }
            LaunchedEffect(Unit) {
                app.onHideWindow = { window.isVisible = false }
                app.onShowWindow = {
                    window.isVisible = true
                    window.toFront()
                    window.requestFocus()
                }
                com.mclauncher.core.Tray.install(
                    onShow = { app.onShowWindow?.invoke() },
                    onExit = { app.requestExit() }
                )
            }
            LauncherTheme(
                dark = app.isDark,
                palette = app.palette,
                glassTint = app.settings.tint
            ) {
                LauncherRoot(app, state)
            }
        }
    }
}
