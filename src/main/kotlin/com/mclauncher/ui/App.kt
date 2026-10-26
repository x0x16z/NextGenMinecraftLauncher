package com.mclauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.FrameWindowScope
import com.mclauncher.core.Acrylic
import com.mclauncher.core.Win32
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.NgmlLogo
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.WindowControls
import com.mclauncher.ui.screens.AccountsScreen
import com.mclauncher.ui.screens.HomeScreen
import com.mclauncher.ui.screens.InstancesScreen
import com.mclauncher.ui.screens.ModsScreen
import com.mclauncher.ui.screens.SettingsScreen
import com.mclauncher.ui.theme.LocalBackdrop
import com.mclauncher.ui.theme.LocalBackdropSize
import com.mclauncher.ui.theme.LocalTokens
import com.mclauncher.ui.theme.GradientFill
import com.mclauncher.ui.theme.toImageBitmap
import com.mclauncher.ui.theme.rememberBackdrop
import java.awt.Frame

@Composable
fun FrameWindowScope.LauncherRoot(app: AppState, windowState: WindowState) {
    val tokens = LocalTokens.current
    var sizePx by remember { mutableStateOf(window.width to window.height) }

    var winRect by remember { mutableStateOf(intArrayOf(0, 0, 0, 0)) }
    var dragUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(120)
            if (dragUntil > 0 && System.currentTimeMillis() > dragUntil) {
                dragUntil = 0
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(window) {
        val listener = object : java.awt.event.ComponentAdapter() {
            override fun componentMoved(e: java.awt.event.ComponentEvent?) { sync() }
            override fun componentResized(e: java.awt.event.ComponentEvent?) { sync() }
            fun sync() {
                winRect = intArrayOf(window.x, window.y, window.width, window.height)
                dragUntil = System.currentTimeMillis() + 260
            }
        }
        window.addComponentListener(listener)
        winRect = intArrayOf(window.x, window.y, window.width, window.height)
        onDispose { window.removeComponentListener(listener) }
    }

    // 启动流程：先只有背景 → 问候语 → 问候语淡出、界面渐显
    var bootPhase by remember { mutableStateOf(0) }
    // NGML_BOOT_HOLD=1 时把问候语停留时间拉长，方便截图/录屏核对
    val holdMs = if (System.getenv("NGML_BOOT_HOLD") == "1") 8000L else 2100L
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(420)
        bootPhase = 1
        kotlinx.coroutines.delay(holdMs)
        bootPhase = 2
        kotlinx.coroutines.delay(900)
        bootPhase = 3
    }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (bootPhase >= 2) 1f else 0f,
        animationSpec = tween(800), label = "content-alpha"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }
        val screen = window.graphicsConfiguration.bounds
        // Compose 的像素坐标是 AWT 逻辑坐标乘 density，这里统一换算成像素，避免背景图和卡片错位
        val scale = density.density
        val backdrop = rememberBackdrop(
            settings = app.settings,
            width = wPx.coerceAtLeast(64),
            height = hPx.coerceAtLeast(64),
            windowX = (winRect[0] * scale).toInt(),
            windowY = (winRect[1] * scale).toInt(),
            screenWidth = (screen.width * scale).toInt(),
            screenHeight = (screen.height * scale).toInt(),
            dark = app.isDark
        )

        // 背景图还没加载出来时，如果有原生亚克力就先用它顶上；不可用就继续走自定义渐变
        var bootGlass by remember { mutableStateOf(false) }
        LaunchedEffect(app.settings.backdropMode) {
            val wantGlass = app.settings.backdropMode == com.mclauncher.core.Settings.BACKDROP_IMAGE &&
                com.mclauncher.core.Win32.supportsNativeAcrylic()
            bootGlass = wantGlass
            if (!wantGlass) return@LaunchedEffect
            // 主动轮询背景是否就绪（最多 3 秒），就绪后立刻撤掉临时毛玻璃
            var waited = 0L
            while (waited < 3000L) {
                if (backdrop.base != null || backdrop.blurred != null) break
                kotlinx.coroutines.delay(50)
                waited += 50
            }
            bootGlass = false
            com.mclauncher.core.AppLog.log("BOOTGLASS off waited=${waited}ms base=${backdrop.base != null}")
        }
        CompositionLocalProvider(
            com.mclauncher.ui.LocalStrings provides app.strings,
            LocalBackdrop provides (
                when {
                    app.settings.useLive -> null
                    backdrop.panelUsesSharp -> backdrop.base
                    else -> backdrop.blurred
                }
                ),
            com.mclauncher.ui.theme.LocalSidebarBackdrop provides backdrop.blurred,
            LocalBackdropSize provides backdrop.size
        ) {
            val windowShape = RoundedCornerShape(16.dp)
            // 现代模式 = Windows 原生亚克力（DWM 合成）。系统不支持时直接拒绝切换。
            // 窗口尺寸变化（含首次显示）时也重新应用一次，避免"启动没生效、重选才生效"
            LaunchedEffect(app.settings.backdropMode, app.isDark, bootGlass, sizePx.first, sizePx.second) {
                if (!app.settings.useLive && !bootGlass) {
                    Acrylic.hwndOf(window).takeIf { it != 0L }?.let { Acrylic.clear(it) }
                    return@LaunchedEffect
                }
                if (!com.mclauncher.core.Win32.supportsNativeAcrylic()) {
                    com.mclauncher.core.AppLog.log("ACRYLIC 不支持：build=${com.mclauncher.core.Win32.winBuild}")
                    app.updateSettings {
                        it.copy(backdropMode = com.mclauncher.core.Settings.BACKDROP_GRADIENT)
                    }
                    app.toast("这台电脑不支持原生亚克力（需要 Windows 11 22H2 及以上），已切回自定义渐变")
                    return@LaunchedEffect
                }
                // AWT peer / 原生句柄要等窗口真正显示后才存在，这里轮询等待
                var hwnd = 0L
                var waitTries = 0
                while (hwnd == 0L && waitTries < 60) {
                    hwnd = Acrylic.hwndOf(window)
                    if (hwnd == 0L || !window.isVisible) {
                        kotlinx.coroutines.delay(100)
                        waitTries++
                    }
                }
                if (hwnd == 0L) {
                    com.mclauncher.core.AppLog.log("ACRYLIC 等不到窗口句柄，放弃")
                    return@LaunchedEffect
                }
                // DWM 有时候要等第一帧画完才接受 backdrop，失败就重试几次
                var ok = false
                var applyTries = 0
                while (!ok && applyTries < 8) {
                    ok = Acrylic.apply(
                        hwnd, app.isDark,
                        if (app.isDark) 0x99202630.toInt() else 0x99F2F4F8.toInt()
                    )
                    if (!ok) {
                        kotlinx.coroutines.delay(220)
                        applyTries++
                    }
                }
                com.mclauncher.core.AppLog.log("ACRYLIC apply=$ok tries=$applyTries ${Acrylic.lastError ?: ""}")
                if (!ok) {
                    app.updateSettings {
                        it.copy(backdropMode = com.mclauncher.core.Settings.BACKDROP_GRADIENT)
                    }
                    app.toast("系统拒绝了亚克力请求，已切回自定义渐变")
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(windowShape)
                    .then(
                        // 现代模式交给 DWM 合成，自己不铺底色；其它模式保留近黑兜底
                        if (app.settings.useLive) Modifier
                        else Modifier.background(Color(0xFF06080C))
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.16f), windowShape)
            ) {
                // 图案/渐变模式画原图（模糊只留给玻璃卡片），否则几何形状会被糊成一团；
                // 图片模式按用户在设置里选的方式决定是否整体模糊
                val baseImage = when {
                    app.settings.useLive || bootGlass -> null
                    backdrop.backgroundUsesBlur -> backdrop.blurred ?: backdrop.base
                    else -> backdrop.base ?: backdrop.blurred
                }
                if (baseImage != null) {
                    Image(baseImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(tokens.scrimOverlay))
                    // 自定义图片通常比较亮，多压一层保证文字可读
                    if (app.settings.useImage) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
                    }
                } else if (!app.settings.useLive && !bootGlass) {
                    GradientFill(
                        preset = app.settings.gradient(),
                        alpha = app.settings.gradient().alpha * app.settings.gradientAlpha
                    )
                }
                // 现代模式：窗口全透明交给 DWM 亚克力，只压一层淡色保证文字可读。
                // 这一层同时保证每帧整窗重绘，透明窗口不会留残影。
                if (app.settings.useLive || bootGlass) {
                    // 有图案主题时压得更淡，否则亚克力的模糊会被盖住
                    val veil = if (app.settings.liveTheme != "none") 0x30 else 0x5A
                    Box(
                        Modifier.fillMaxSize().background(
                            if (tokens.dark) Color(veil.shl(24) or 0x090C12) else Color(0x40F2F4F8)
                        )
                    )
                }
                // 现代模式的主题图案：叠在背景之上、界面之下（之前画在背景前面，被盖住了）
                if (app.settings.useLive && app.settings.liveTheme != "none") {
                    // 尺寸按 64px 量化 + 半分辨率渲染：拖动窗口时不会每帧重画整张图
                    val qw = ((wPx / 2).coerceAtLeast(64) / 64) * 64
                    val qh = ((hPx / 2).coerceAtLeast(64) / 64) * 64
                    val themeBmp = remember(app.settings.liveTheme, app.isDark, qw, qh) {
                        com.mclauncher.core.Patterns.render(app.settings.liveTheme, qw, qh, app.isDark, transparent = true).toImageBitmap()
                    }
                    Image(themeBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (app.settings.useLive) 0.38f else 0.55f)
                }

                // 启动问候语：只显示背景 → 浮现问候 → 淡出，再渐显界面
                if (bootPhase in 1..3) {
                    val hour = java.time.LocalTime.now().hour
                    val prefix = when (hour) {
                        in 5..10 -> "早上好"
                        in 11..12 -> "中午好"
                        in 13..17 -> "下午好"
                        in 18..22 -> "晚上好"
                        else -> "夜深了"
                    }
                    val name = app.activeAccount?.name ?: "神秘的MC玩家"
                    val greetAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (bootPhase == 1) 1f else 0f,
                        animationSpec = tween(700), label = "greeting-alpha"
                    )
                    Box(
                        Modifier.fillMaxSize().graphicsLayer(alpha = greetAlpha),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$prefix，$name",
                                color = tokens.text,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("正在准备启动器…", color = tokens.subtext, fontSize = 12.sp)
                        }
                    }
                }

                Row(Modifier.fillMaxSize().graphicsLayer(alpha = contentAlpha)) {
                    Sidebar(app)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TitleBar(app, windowState, window, onClose = { app.requestExit() })
                        Box(Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = app.screen,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 26 }) togetherWith
                                        (fadeOut(tween(140)) + slideOutHorizontally(tween(160)) { -it / 26 })
                                },
                                label = "screen"
                            ) { screen ->
                                when (screen) {
                                    Screen.HOME -> HomeScreen(app)
                                    Screen.INSTANCES -> InstancesScreen(app)
                                    Screen.MODS -> ModsScreen(app)
                                    Screen.ACCOUNTS -> AccountsScreen(app)
                                    Screen.TOOLS -> com.mclauncher.ui.screens.ToolsScreen(app)
                                    Screen.SETTINGS -> SettingsScreen(app)
                                }
                            }
                        }
                    }
                }

                // 全局任务浮层
                app.task?.let { task ->
                    TaskBadge(app, task, Modifier.align(Alignment.BottomEnd).padding(20.dp))
                }
                // 轻提示
                app.message?.let { text ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) {
                        ToastPill(text)
                    }
                }
                // 内容管理（版本资源）
                app.contentOf?.let { v ->
                    com.mclauncher.ui.screens.ContentSheet(app, v) { app.contentOf = null }
                }
                // 下载中心（网格 / 列表 + 详情）
                if (app.showDownloads) {
                    com.mclauncher.ui.DownloadsSheet(app) { app.showDownloads = false }
                }
                // 下载失败：右下角弹窗
                app.errorToast?.let { text ->
                    LaunchedEffect(text) {
                        kotlinx.coroutines.delay(5200)
                        if (app.errorToast == text) app.errorToast = null
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
                        com.mclauncher.ui.components.ErrorToast(text) { app.errorToast = null }
                    }
                }
                // 下载面板（旧的详细信息，作为兜底保留）
                if (app.showDownloadPanel && app.downloadSession != null) {
                    com.mclauncher.ui.DownloadPanel(app) { app.showDownloadPanel = false }
                }
                // MOD 下载向导 / 整合包面板：放在根布局，遮住整个窗口，主界面点不到
                app.modWizard?.let { request ->
                    com.mclauncher.ui.screens.ModDownloadWizard(
                        app, request.project, request.gameVersion, request.loader
                    ) { app.modWizard = null }
                }
                if (app.showAbout) {
                    com.mclauncher.ui.screens.AboutSheet { app.showAbout = false }
                }
                app.modpackSheet?.let { project ->
                    com.mclauncher.ui.screens.ModpackSheet(app, project) { app.modpackSheet = null }
                }
                // 渐变编辑 / 删除弹窗（根布局 → 遮罩铺满整窗）
                com.mclauncher.ui.screens.GradientSheetsHost(app)
                // 彩蛋
                if (app.showRoach) {
                    com.mclauncher.ui.components.RoachOverlay { app.showRoach = false }
                }

                // 通用二次确认（删除存档 / 截图 / MOD 等）
                app.pendingDelete?.let { pending ->
                    com.mclauncher.ui.components.Sheet(
                        visible = true,
                        onDismiss = { app.pendingDelete = null },
                        title = "确认删除",
                        subtitle = null,
                        width = 420.dp,
                        maxHeight = 260.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                            Text(pending.title, color = LocalTokens.current.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(pending.subtitle, color = LocalTokens.current.subtext, fontSize = 11.sp)
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                com.mclauncher.ui.components.GhostButton("取消") { app.pendingDelete = null }
                                Spacer(Modifier.width(8.dp))
                                com.mclauncher.ui.components.PrimaryButton("删除") { app.performPendingDelete() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(app: AppState) {
    val tokens = LocalTokens.current
    var hovered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    val auto = app.settings.sidebarAutoCollapse
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    LaunchedEffect(isHovered, auto) {
        if (!auto) {
            expanded = true
        } else if (isHovered) {
            expanded = true
        } else {
            kotlinx.coroutines.delay(90)
            expanded = false
        }
    }

    val width by androidx.compose.animation.core.animateDpAsState(
        if (expanded || !auto) 212.dp else 76.dp,
        androidx.compose.animation.core.tween(140),
        label = "sidebar-width"
    )

    val sidebarGlass = app.settings.useImage && app.settings.imageBlur == com.mclauncher.core.Settings.IMAGE_BLUR_SIDEBAR
    val sidebarBackdrop = com.mclauncher.ui.theme.LocalSidebarBackdrop.current
    val backdrop = if (app.settings.useImage && app.settings.imageBlur == com.mclauncher.core.Settings.IMAGE_BLUR_SIDEBAR)
        sidebarBackdrop else LocalBackdrop.current
    val windowPx = com.mclauncher.ui.theme.LocalBackdropSize.current

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .hoverable(interaction)
            // 关键：不裁剪的话，drawBehind 里那张"窗口大小"的模糊图会盖到整个窗口上
            .clipToBounds()
            .drawBehind {
                if (sidebarGlass && backdrop != null) {
                    drawImage(
                        image = backdrop,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(backdrop.width, backdrop.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        dstSize = windowPx,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                    )
                    drawRect(Color.Black.copy(alpha = if (tokens.dark) 0.25f else 0.12f))
                }
            }
            .padding(start = 14.dp, top = 16.dp, bottom = 16.dp, end = 6.dp)
    ) {
        // 顶部：账号
        AccountChip(app, expanded)
        Spacer(Modifier.height(18.dp))

        NavEntry(Screen.HOME, Icons.Filled.Home, app, expanded)
        NavEntry(Screen.INSTANCES, Icons.AutoMirrored.Filled.List, app, expanded)
        NavEntry(Screen.MODS, Icons.Filled.Extension, app, expanded)
        NavEntry(Screen.ACCOUNTS, Icons.Filled.Person, app, expanded)
        NavEntry(Screen.TOOLS, Icons.Filled.Build, app, expanded)
        NavEntry(Screen.SETTINGS, Icons.Filled.Settings, app, expanded)

        Spacer(Modifier.weight(1f))

        // 下载栏目：展示所有进行中的版本 / MOD 下载
        DownloadEntry(app, expanded)

        // 底部：NGML 图标 + 名字
        Row(
            Modifier.height(44.dp).fillMaxWidth().padding(horizontal = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { app.showAbout = true }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.mclauncher.ui.components.NgmlLogo(size = 32.dp, accent = tokens.accent, accent2 = tokens.accent2)
            if (expanded) {
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("NGML", color = tokens.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Next-Gen Minecraft Launcher", color = tokens.subtext, fontSize = 8.sp, maxLines = 1)
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                "v1.0.0 · " + if (app.runningPids.isEmpty()) "空闲" else "游戏运行中",
                color = tokens.subtext.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun AccountChip(app: AppState, expanded: Boolean) {
    val tokens = LocalTokens.current
    val account = app.activeAccount
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) tokens.field else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { app.screen = Screen.ACCOUNTS }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(app, 34.dp)
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    account?.name ?: "未登录",
                    color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1
                )
                Text(
                    account?.badge ?: "点击添加账号",
                    color = tokens.subtext, fontSize = 10.sp, maxLines = 1
                )
            }
        }
    }
}
@Composable
private fun NavEntry(screen: Screen, icon: ImageVector, app: AppState, expanded: Boolean = true) {
    val tokens = LocalTokens.current
    val selected = app.screen == screen
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when {
        selected -> Brush.linearGradient(
            listOf(tokens.accent.copy(alpha = 0.30f), tokens.accent2.copy(alpha = 0.14f))
        )
        hovered -> Brush.linearGradient(listOf(tokens.field, tokens.field))
        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { app.screen = screen }
            .padding(horizontal = if (expanded) 13.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        Icon(
            icon, null,
            tint = if (selected) tokens.text else tokens.subtext,
            modifier = Modifier.size(18.dp)
        )
        if (expanded) {
            Spacer(Modifier.width(11.dp))
            Text(
                screenTitle(screen),
                color = if (selected) tokens.text else tokens.text.copy(alpha = 0.78f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(tokens.accent))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TitleBar(
    app: AppState,
    windowState: WindowState,
    window: androidx.compose.ui.awt.ComposeWindow,
    onClose: () -> Unit
) {
    val tokens = LocalTokens.current
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        val cursor = Win32.cursorPos()
                        if (cursor != null) {
                            dragOffset = IntOffset(cursor.first - window.x, cursor.second - window.y)
                        }
                    },
                    onDragEnd = { },
                    onDragCancel = { },
                    onDrag = {
                        val cursor = Win32.cursorPos() ?: return@detectDragGestures
                        window.setLocation(cursor.first - dragOffset.x, cursor.second - dragOffset.y)
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            screenTitle(app.screen),
            color = tokens.text.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp)
        )
        Spacer(Modifier.weight(1f))
        WindowControls(
            onMinimize = { windowState.isMinimized = true },
            onMaximize = {
                window.extendedState =
                    if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) Frame.NORMAL
                    else Frame.MAXIMIZED_BOTH
            },
            onClose = onClose
        )
    }
}

@Composable
fun Avatar(app: AppState, size: androidx.compose.ui.unit.Dp, name: String? = null, skinUrl: String? = null) {
    val tokens = LocalTokens.current
    val account = app.activeAccount
    val url = skinUrl ?: account?.skinUrl
    val head = com.mclauncher.ui.components.rememberSkinHead(url?.ifBlank { null })
    val label = (name ?: account?.name ?: "?").take(1).uppercase()

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(
                Brush.linearGradient(
                    listOf(tokens.accent.copy(alpha = 0.55f), tokens.accent2.copy(alpha = 0.45f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (head != null) {
            Image(
                head, null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(label, color = Color.White, fontSize = (size.value / 2.4f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TaskBadge(app: AppState, task: TaskState, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    GlassPanel(
        modifier = modifier.width(280.dp).clickable { app.showDownloadPanel = true },
        shape = RoundedCornerShape(16.dp),
        tint = if (tokens.dark) Color(0xCC12161E) else Color(0xE6FFFFFF)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.mclauncher.ui.components.LoadingDots()
                Spacer(Modifier.width(10.dp))
                Text(task.title, color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                com.mclauncher.ui.components.CircleIconButton(
                    Icons.Filled.Close,
                    size = 24.dp,
                    onClick = { app.cancelTask() }
                )
            }
            if (task.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(task.detail, color = tokens.subtext, fontSize = 10.sp, maxLines = 1)
            }
            if (task.progress >= 0f) {
                Spacer(Modifier.height(9.dp))
                com.mclauncher.ui.components.ProgressBar(task.progress)
            }
        }
    }
}

@Composable
private fun ToastPill(text: String) {
    val tokens = LocalTokens.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (tokens.dark) Color(0xE61B2029) else Color(0xF2FFFFFF))
            .border(1.dp, tokens.border, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text, color = tokens.text, fontSize = 12.sp)
    }
}
@Composable
fun screenTitle(screen: Screen): String = when (screen) {
    Screen.HOME -> com.mclauncher.ui.tr("nav.home", "首页")
    Screen.INSTANCES -> com.mclauncher.ui.tr("nav.versions", "游戏版本")
    Screen.MODS -> com.mclauncher.ui.tr("nav.mods", "MOD 工坊")
    Screen.ACCOUNTS -> com.mclauncher.ui.tr("nav.accounts", "账号")
    Screen.TOOLS -> com.mclauncher.ui.tr("nav.tools", "工具箱")
    Screen.SETTINGS -> com.mclauncher.ui.tr("nav.settings", "设置")
}
/** 侧边栏"下载"入口：右上角显示进行中的数量，新增任务时闪一下 */
@Composable
private fun DownloadEntry(app: AppState, expanded: Boolean) {
    val tokens = LocalTokens.current
    val running = app.runningDownloads
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // 闪烁动画：任务变化时把高亮推一下
    val pulse = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(app.downloadsFlash) {
        if (app.downloadsFlash == 0) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, androidx.compose.animation.core.tween(900))
    }
    val glow = pulse.value

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (glow > 0.01f) tokens.accent.copy(alpha = 0.30f * glow)
                else if (hovered) tokens.field else Color.Transparent
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                app.downloadsDetailId = null
                app.showDownloads = true
            }
            .padding(start = 10.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Download,
            null,
            tint = if (running > 0) tokens.accent else tokens.subtext,
            modifier = Modifier.size(18.dp)
        )
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Text(
                t("下载"),
                color = if (running > 0) tokens.text else tokens.subtext,
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.width(6.dp))
        }
        if (running > 0) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(tokens.accent.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("$running", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}