package com.mclauncher.ui.screens

import com.mclauncher.ui.t

import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.core.AppPaths
import com.mclauncher.core.Fs
import com.mclauncher.core.GradientMath
import com.mclauncher.core.GradientPreset
import com.mclauncher.core.Mirror
import com.mclauncher.core.Settings
import com.mclauncher.ui.AppState
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GhostButton
import com.mclauncher.ui.components.HScroll
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.GlassTextField
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.Sheet
import androidx.compose.foundation.lazy.items
import com.mclauncher.ui.tr
import com.mclauncher.ui.theme.AC_PALETTES
import com.mclauncher.ui.theme.GradientFill
import com.mclauncher.ui.theme.toImageBitmap
import com.mclauncher.ui.theme.LocalTokens

@Composable
fun SettingsScreen(app: AppState) {
    val tokens = LocalTokens.current
    val s = app.settings
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 22.dp, end = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.padding(top = 6.dp)) {
            Text(tr("settings.title", "设置"), color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(tr("settings.subtitle", "所有改动自动保存"), color = tokens.subtext, fontSize = 12.sp)
        }

        SettingsGroup(tr("settings.background", "背景 · 毛玻璃")) {
            HScroll { 
                Text(t("模式"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                FilterChip(t("现代"), s.backdropMode == Settings.BACKDROP_LIVE || s.backdropMode == Settings.BACKDROP_PATTERN) {
                    app.updateSettings { it.copy(backdropMode = Settings.BACKDROP_LIVE) }
                }
                Spacer(Modifier.width(6.dp))
                FilterChip(t("自定义渐变"), s.backdropMode == Settings.BACKDROP_GRADIENT) {
                    app.updateSettings { it.copy(backdropMode = Settings.BACKDROP_GRADIENT) }
                }
                Spacer(Modifier.width(6.dp))
                FilterChip(t("图片"), s.backdropMode == Settings.BACKDROP_IMAGE) {
                    app.updateSettings { it.copy(backdropMode = Settings.BACKDROP_IMAGE) }
                }
            }
            Text(
                when (s.backdropMode) {
                    Settings.BACKDROP_LIVE -> "系统原生亚克力（DWM 合成，可被截图/录屏），可选叠加几何图案主题"
                    Settings.BACKDROP_GRADIENT -> "使用下面的自定义渐变，不依赖桌面"
                    Settings.BACKDROP_PATTERN -> "已并入现代模式"
                    Settings.BACKDROP_IMAGE -> "用你自己的图片做背景，可以选择模糊方式"
                    else -> "调用 Windows DWM 材质（部分显卡/系统上可能无效）"
                },
                color = tokens.subtext.copy(alpha = 0.75f), fontSize = 10.sp,
                modifier = Modifier.padding(start = 70.dp, top = 6.dp)
            )



            HScroll(Modifier.padding(top = 8.dp)) {
                Text(t("玻璃色"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                listOf(
                    "白" to 0x38FFFFFFL,
                    "蓝" to 0x385B8CFFL,
                    "紫" to 0x389A7BFFL,
                    "粉" to 0x38FF6FA5L,
                    "绿" to 0x383FD9A4L,
                    "墨" to 0x40000000L
                ).forEach { pair ->
                    val selected = s.tint == pair.second
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(Color(pair.second.toInt()))
                            .border(1.5.dp, if (selected) Color.White else tokens.border, CircleShape)
                            .clickable { app.updateSettings { it.copy(tint = pair.second) } }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            if (s.backdropMode == Settings.BACKDROP_IMAGE) {
                Text(t("图片背景"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                HScroll {
                    GhostButton(t("选择图片")) {
                        app.chooseImageFile()?.let { path ->
                            app.updateSettings { it.copy(imagePath = path) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("删除图片"), enabled = s.imagePath.isNotBlank()) {
                        app.updateSettings { it.copy(imagePath = "") }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.imagePath.ifBlank { t("还没有选择图片") },
                        color = tokens.subtext, fontSize = 10.sp, maxLines = 1
                    )
                }
                Spacer(Modifier.height(8.dp))
                HScroll {
                    Text(t("模糊方式"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    FilterChip(t("全部模糊"), s.imageBlur == Settings.IMAGE_BLUR_ALL) {
                        app.updateSettings { it.copy(imageBlur = Settings.IMAGE_BLUR_ALL) }
                    }
                    Spacer(Modifier.width(6.dp))
                    FilterChip(t("仅侧边栏"), s.imageBlur == Settings.IMAGE_BLUR_SIDEBAR) {
                        app.updateSettings { it.copy(imageBlur = Settings.IMAGE_BLUR_SIDEBAR) }
                    }
                    Spacer(Modifier.width(6.dp))
                    FilterChip(t("不模糊"), s.imageBlur == Settings.IMAGE_BLUR_NONE) {
                        app.updateSettings { it.copy(imageBlur = Settings.IMAGE_BLUR_NONE) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(8.dp))
                HScroll {
                    Text(t("模糊"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = s.imageBlurRadius.toFloat(),
                        onValueChange = { v -> app.updateSettings { it.copy(imageBlurRadius = v.toInt()) } },
                        valueRange = 0f..60f,
                        modifier = Modifier.width(200.dp),
                        colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${s.imageBlurRadius}", color = tokens.text, fontSize = 11.sp)
                    Spacer(Modifier.width(18.dp))
                    Text(t("暗化"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(50.dp))
                    Slider(
                        value = s.imageDim,
                        onValueChange = { v -> app.updateSettings { it.copy(imageDim = v) } },
                        valueRange = 0f..0.7f,
                        modifier = Modifier.width(180.dp),
                        colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${(s.imageDim * 100).toInt()}%", color = tokens.text, fontSize = 11.sp)
                }

                HScroll {
                    Text(t("拉伸模式"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    FilterChip(t("填充裁切"), s.imageScale == Settings.SCALE_CROP) {
                        app.updateSettings { it.copy(imageScale = Settings.SCALE_CROP) }
                    }
                    Spacer(Modifier.width(6.dp))
                    FilterChip(t("完整显示"), s.imageScale == Settings.SCALE_FIT) {
                        app.updateSettings { it.copy(imageScale = Settings.SCALE_FIT) }
                    }
                    Spacer(Modifier.width(6.dp))
                    FilterChip(t("拉伸铺满"), s.imageScale == Settings.SCALE_STRETCH) {
                        app.updateSettings { it.copy(imageScale = Settings.SCALE_STRETCH) }
                    }
                    Spacer(Modifier.width(6.dp))
                    FilterChip(t("平铺"), s.imageScale == Settings.SCALE_TILE) {
                        app.updateSettings { it.copy(imageScale = Settings.SCALE_TILE) }
                    }
                }
            }
            if (s.backdropMode == Settings.BACKDROP_LIVE || s.backdropMode == Settings.BACKDROP_PATTERN) {
                Text(t("主题"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                val dark = tokens.dark
                val thumbs = remember(s.liveTheme, dark) {
                    com.mclauncher.core.Patterns.all.associate { preset ->
                        preset.id to com.mclauncher.core.Patterns.render(preset.id, 240, 120, dark).toImageBitmap()
                    }
                }
                HScroll {
                    // "无" = 纯毛玻璃
                    val noneSelected = s.liveTheme == "none"
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(104.dp, 58.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { app.updateSettings { it.copy(liveTheme = "none") } }
                                .border(
                                    if (noneSelected) 2.dp else 1.dp,
                                    if (noneSelected) Color.White else tokens.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .background(tokens.field),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("无", color = tokens.subtext, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("纯毛玻璃", color = if (noneSelected) tokens.accent else tokens.subtext, fontSize = 10.sp)
                    }
                    com.mclauncher.core.Patterns.all.forEach { preset ->
                        val selected = s.liveTheme == preset.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(104.dp, 58.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable { app.updateSettings { it.copy(liveTheme = preset.id) } }
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color.White else tokens.border,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                thumbs[preset.id]?.let { bmp ->
                                    androidx.compose.foundation.Image(
                                        bmp, null,
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                preset.name,
                                color = if (selected) tokens.accent else tokens.subtext,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
            if (s.backdropMode == Settings.BACKDROP_GRADIENT) {
                Text(t("渐变预设"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                HScroll {
                    s.gradients.forEach { preset ->
                        val selected = s.activeGradient == preset.id
                        val isCustom = preset.id.startsWith("custom-")
                        val hoverSource = remember { MutableInteractionSource() }
                        val hovered by hoverSource.collectIsHoveredAsState()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(12.dp))
                                    .hoverable(hoverSource)
                                    .clickable {
                                        app.updateSettings { it.copy(activeGradient = preset.id) }
                                    }
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Color.White else tokens.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(Color.Black)
                            ) {
                                GradientFill(preset, 1f, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                                // 仅"自己新建的渐变"在鼠标悬浮时显示删除按钮
                                if (isCustom && hovered && !selected) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                                        com.mclauncher.ui.components.CircleIconButton(
                                            androidx.compose.material.icons.Icons.Filled.Delete,
                                            size = 22.dp,
                                            tint = tokens.danger,
                                            onClick = { app.deletingGradient = preset }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                preset.name,
                                color = if (selected) tokens.accent else tokens.subtext,
                                fontSize = 10.sp,
                                modifier = Modifier.clickable {
                                    app.updateSettings { it.copy(activeGradient = preset.id) }
                                }
                            )
                            if (selected) {
                                Text(t("使用中"), color = tokens.success, fontSize = 9.sp)
                            }
                        }
                    }
                    GhostButton(t("新建渐变")) {
                        app.editingGradient = GradientMath.randomPreset(s.gradients.size)
                    }
                }
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("透明度"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = s.gradientAlpha,
                        onValueChange = { v -> app.updateSettings { it.copy(gradientAlpha = v) } },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.width(220.dp),
                        colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                    )
                    GhostButton(t("编辑当前渐变")) {
                        app.editingGradient = s.gradient()
                    }
                }
            }
        }

        SettingsGroup(tr("settings.appearance", "外观")) {
            HScroll {
                Text(t("配色"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                AC_PALETTES.forEach { palette ->
                    val selected = s.palette == palette.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(palette.accent, palette.accent2)))
                                .border(
                                    if (selected) 2.dp else 0.dp,
                                    if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { app.updateSettings { it.copy(palette = palette.id) } }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(palette.label, color = tokens.subtext, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("明暗"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                FilterChip(t("跟随系统"), s.mode == "auto") { app.updateSettings { it.copy(mode = "auto") } }
                Spacer(Modifier.width(6.dp))
                FilterChip(t("浅色"), s.mode == "light") { app.updateSettings { it.copy(mode = "light") } }
                Spacer(Modifier.width(6.dp))
                FilterChip(t("深色"), s.mode == "dark") { app.updateSettings { it.copy(mode = "dark") } }
            }
        }

        SettingsGroup(tr("settings.download", "下载与网络")) {
            HScroll {
                Text(t("下载源"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                FilterChip(t("自动"), s.mirror == Mirror.AUTO) { app.updateSettings { it.copy(mirror = Mirror.AUTO) } }
                Spacer(Modifier.width(6.dp))
                FilterChip(t("官方"), s.mirror == Mirror.OFFICIAL) { app.updateSettings { it.copy(mirror = Mirror.OFFICIAL) } }
                Spacer(Modifier.width(6.dp))
                FilterChip(t("BMCLAPI 镜像"), s.mirror == Mirror.BMCL) { app.updateSettings { it.copy(mirror = Mirror.BMCL) } }
                Spacer(Modifier.width(12.dp))
                Text("并发 ${s.threads}", color = tokens.text, fontSize = 11.sp)
                Slider(
                    value = s.threads.toFloat(),
                    onValueChange = { v -> app.updateSettings { it.copy(threads = v.toInt()) } },
                    valueRange = 2f..24f,
                    modifier = Modifier.width(150.dp),
                    colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                )
            }
            Column(Modifier.padding(top = 10.dp)) {
                Text(t("游戏根目录（存放 versions / libraries / assets）"), color = tokens.subtext, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(
                        value = s.minecraftRoot,
                        onValueChange = { value -> app.updateSettings { it.copy(minecraftRoot = value) } },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("选择")) {
                        app.chooseDirectory("选择游戏根目录")?.let { dir ->
                            app.updateSettings { it.copy(minecraftRoot = dir) }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("打开")) { Fs.openFolder(java.nio.file.Paths.get(s.minecraftRoot)) }
                }
            }
        }

        SettingsGroup(tr("settings.language", "语言与翻译")) {
            HScroll {
                Text(t("语言"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                com.mclauncher.core.Lang.builtinCodes().forEach { code ->
                    FilterChip(
                        com.mclauncher.core.Lang.displayName(code),
                        app.settings.language == code
                    ) { app.setLanguage(code) }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr("settings.languageHint", "语言包是 JSON 文件，改完点重新加载即可生效"),
                color = tokens.subtext.copy(alpha = 0.75f), fontSize = 10.sp,
                modifier = Modifier.padding(start = 70.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 70.dp)) {
                GhostButton(tr("settings.openLangDir", "打开语言目录")) {
                    com.mclauncher.core.Fs.openFolder(com.mclauncher.core.Lang.langDir())
                }
                GhostButton(tr("settings.exportTemplate", "导出模板")) {
                    val path = com.mclauncher.core.Lang.exportTemplate(app.settings.language)
                    app.toast("已导出：${path.fileName}")
                }
                GhostButton(tr("settings.reloadLang", "重新加载")) { app.reloadLanguage() }
            }
        }
        SettingsGroup("运行日志") {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            Box(Modifier.fillMaxWidth().height(220.dp)) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 14.dp)
                ) {
                    items(app.logs.size) { index ->
                        Text(
                            app.logs[index],
                            color = if (app.logs[index].contains("ERROR")) tokens.danger else tokens.subtext,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 2
                        )
                    }
                }
                com.mclauncher.ui.components.ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
        SettingsGroup(tr("settings.misc", "杂项")) {
            ToggleRow("启动游戏后隐藏启动器", "隐藏后可以从托盘图标叫回来", s.hideAfterLaunch) { enabled ->
                app.updateSettings { it.copy(hideAfterLaunch = enabled) }
            }
            ToggleRow("窗口置顶", "启动器始终显示在其他窗口之上", s.alwaysOnTop) { enabled ->
                app.updateSettings { it.copy(alwaysOnTop = enabled) }
            }
            ToggleRow("侧边栏自动折叠", "鼠标移开自动收起，只留图标；关掉则始终展开", s.sidebarAutoCollapse) { enabled ->
                app.updateSettings { it.copy(sidebarAutoCollapse = enabled) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("配置目录"), color = tokens.text, fontSize = 12.sp)
                    Text(AppPaths.root.toString(), color = tokens.subtext, fontSize = 10.sp, maxLines = 1)
                }
                GhostButton(t("打开")) { Fs.openFolder(AppPaths.root) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "NGML · Next-Gen Minecraft Launcher 1.0\n" +
                    "游戏资源来自 Mojang 官方元数据，MOD 数据来自 Modrinth 与 CurseForge。",
                color = tokens.subtext, fontSize = 10.sp, lineHeight = 16.sp
            )
        }

        // 最底部：红色"千万别点"
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().padding(bottom = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(tokens.danger.copy(alpha = 0.12f))
                    .border(1.dp, tokens.danger.copy(alpha = 0.85f), RoundedCornerShape(50))
                    .clickable { app.showRoach = true }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠", color = tokens.danger, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(t("千万别点"), color = tokens.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            }
        }
        com.mclauncher.ui.components.ColumnScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
    }

}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = tokens.text, fontSize = 12.sp)
            Text(hint, color = tokens.subtext.copy(alpha = 0.7f), fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = tokens.accent)
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalTokens.current
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/* ------------------------------ 渐变编辑器 ------------------------------ */

private val SWATCHES = listOf(
    0xFF0B1026L, 0xFF13243F, 0xFF1B3A6B, 0xFF2FA8A0, 0xFF8AD6C8, 0xFFE8FFF4,
    0xFF2A1220L, 0xFF8A3B5A, 0xFFD98AA0, 0xFFF6D9C9, 0xFF42275A, 0xFFB06AB3,
    0xFF07160F, 0xFF14432E, 0xFF2E7D5B, 0xFFB6E3C4, 0xFFC85A6A, 0xFFF2B26B,
    0xFF101114L, 0xFF2A2C31, 0xFF55585F, 0xFF9BA0A8, 0xFFFC466B, 0xFF3F5EFB
)

@Composable
private fun GradientEditorSheet(app: AppState, initial: GradientPreset, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    var name by remember { mutableStateOf(initial.name) }
    val colors = remember { mutableStateListOf<Long>().also { it.addAll(initial.colors) } }
    var mode by remember { mutableStateOf(initial.mode) }
    var alpha by remember { mutableStateOf(initial.alpha) }
    var smooth by remember { mutableStateOf(initial.smooth) }
    var angle by remember { mutableStateOf(initial.angle) }
    var activeIndex by remember { mutableStateOf(0) }

    val preview = GradientPreset(
        id = initial.id, name = name, colors = colors.toList(),
        mode = mode, angle = angle, smooth = smooth, alpha = alpha
    )

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = "渐变编辑器",
        subtitle = "改颜色、换形状，起个名字存下来",
        width = 720.dp,
        maxHeight = 720.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Box(
                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)
            ) {
                GradientFill(preview, 1f, Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("名称"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                GlassTextField(name, { name = it }, modifier = Modifier.weight(1f), placeholder = t("给这个渐变起个名字"))
            }

            Spacer(Modifier.height(12.dp))
            Text(t("颜色（点色块修改选中的那一层）"), color = tokens.subtext, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEachIndexed { index, color ->
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                            .background(Color(color.toInt()))
                            .border(
                                if (index == activeIndex) 2.dp else 1.dp,
                                if (index == activeIndex) Color.White else tokens.border,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { activeIndex = index }
                    )
                }
                GhostButton("+") {
                    if (colors.size < 6) {
                        colors.add(SWATCHES.random())
                        activeIndex = colors.lastIndex
                    }
                }
                GhostButton("-") {
                    if (colors.size > 2) {
                        colors.removeAt(activeIndex.coerceAtMost(colors.lastIndex))
                        activeIndex = activeIndex.coerceAtMost(colors.lastIndex)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                SWATCHES.take(12).forEach { swatch ->
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(swatch.toInt()))
                            .clickable { if (colors.isNotEmpty()) colors[activeIndex.coerceIn(0, colors.lastIndex)] = swatch }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("形状"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                GradientPreset.modes.forEach { pair ->
                    val selected = mode == pair.first
                    Box(
                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
                            .background(if (selected) tokens.accent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { mode = pair.first }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(pair.second, color = if (selected) tokens.accent else tokens.subtext, fontSize = 11.sp)
                    }
                }
            }

            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("平滑"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                Slider(
                    value = smooth, onValueChange = { smooth = it }, valueRange = 0f..1f,
                    modifier = Modifier.width(200.dp),
                    colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                )
                Text(t("角度"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp))
                Slider(
                    value = angle, onValueChange = { angle = it }, valueRange = 0f..360f,
                    modifier = Modifier.width(160.dp),
                    colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("不透明度"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                Slider(
                    value = alpha, onValueChange = { alpha = it }, valueRange = 0.1f..1f,
                    modifier = Modifier.width(200.dp),
                    colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(t("取消"), onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(t("保存渐变")) {
                    val saved = preview.copy(name = name.ifBlank { "未命名渐变" })
                    val exists = app.settings.gradients.any { it.id == saved.id }
                    app.updateSettings {
                        it.copy(
                            gradients = if (exists) it.gradients.map { g -> if (g.id == saved.id) saved else g }
                            else it.gradients + saved,
                            activeGradient = saved.id,
                            backdropMode = Settings.BACKDROP_GRADIENT
                        )
                    }
                    onDismiss()
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
@Composable
fun GradientSheetsHost(app: AppState) {
    app.editingGradient?.let { preset ->
        GradientEditorSheet(app, preset) { app.editingGradient = null }
    }

    // 删除自定义渐变（二次确认）——放在根布局渲染，遮罩才能铺满整个窗口
    app.deletingGradient?.let { target ->
        val tokens = LocalTokens.current
        Sheet(
            visible = true,
            onDismiss = { app.deletingGradient = null },
            title = t("删除渐变"),
            subtitle = target.name,
            width = 460.dp,
            maxHeight = 300.dp
        ) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text(t("确定要删除这个渐变吗？删除后不能恢复。"), color = tokens.text, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    t("只有你自己新建的渐变可以删，内置预设不受影响。"),
                    color = tokens.subtext, fontSize = 10.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton(t("取消")) { app.deletingGradient = null }
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(t("删除")) {
                        val remaining = app.settings.gradients.filterNot { it.id == target.id }
                        if (remaining.isEmpty()) {
                            app.toast("至少要保留一个渐变")
                        } else {
                            app.updateSettings {
                                it.copy(
                                    gradients = remaining,
                                    activeGradient = if (it.activeGradient == target.id) remaining.first().id
                                    else it.activeGradient
                                )
                            }
                            app.toast("已删除渐变「${target.name}」")
                        }
                        app.deletingGradient = null
                    }
                }
            }
        }
    }
}