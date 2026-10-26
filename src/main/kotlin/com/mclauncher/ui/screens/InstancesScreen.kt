package com.mclauncher.ui.screens

import com.mclauncher.ui.t
import java.nio.file.Path

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.mclauncher.core.humanTime
import com.mclauncher.instance.Instance
import com.mclauncher.meta.Loaders
import com.mclauncher.ui.AppState
import com.mclauncher.ui.ContentItem
import com.mclauncher.ui.components.CircleIconButton
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GhostButton
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.GlassTextField
import com.mclauncher.ui.components.LoadingDots
import com.mclauncher.ui.components.McIcons
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.ProgressBar
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.components.Tag
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.mclauncher.ui.theme.toImageBitmap
import com.mclauncher.ui.theme.LocalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun InstancesScreen(app: AppState) {
    val tokens = LocalTokens.current
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Instance?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("recent") }
    var grid by remember { mutableStateOf(true) }
    var deleting by remember { mutableStateOf<Instance?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // 进入页面先同步一次磁盘，之后每 5 秒自动刷新（别的地方删了版本也能及时消失）
    LaunchedEffect(Unit) {
        app.reloadInstances()
        while (true) {
            kotlinx.coroutines.delay(5000)
            app.reloadInstances()
        }
    }

    LaunchedEffect(app.openCreateInstance) {
        if (app.openCreateInstance) {
            showCreate = true
            app.openCreateInstance = false
        }
    }

    val versions = remember(app.instances, query, sort) {
        app.instances
            .filter { query.isBlank() || it.name.contains(query, true) || it.mcVersion.contains(query, true) }
            .sortedWith(
                when (sort) {
                    "name" -> compareBy { it.name.lowercase() }
                    "version" -> compareByDescending { it.mcVersion }
                    else -> compareByDescending { it.lastPlayed }
                }
            )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("游戏版本"), color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(t("每个版本是独立的游戏目录，MOD / 光影 / 资源包互不干扰"), color = tokens.subtext, fontSize = 12.sp)
            }
            com.mclauncher.ui.components.RefreshButton(spinning = app.task != null, onClick = {
                app.refreshManifest()
                app.reloadInstances()
            })
            Spacer(Modifier.width(8.dp))
            PrimaryButton(t("新建版本"), icon = Icons.Filled.Add, onClick = { showCreate = true })
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = t("搜索版本名 / 游戏版本"),
                modifier = Modifier.width(250.dp),
                height = 38.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(t("排序"), color = tokens.subtext, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            FilterChip(t("最近启动"), sort == "recent") { sort = "recent" }
            Spacer(Modifier.width(5.dp))
            FilterChip(t("名称"), sort == "name") { sort = "name" }
            Spacer(Modifier.width(5.dp))
            FilterChip(t("游戏版本"), sort == "version") { sort = "version" }
            Spacer(Modifier.weight(1f))
            Text("${versions.size} 个版本", color = tokens.subtext, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            ViewToggle(grid) { grid = it }
        }

        if (versions.isEmpty()) {
            EmptyVersions { showCreate = true }
        } else {
            Box(Modifier.fillMaxSize()) {
                if (grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(250.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                    ) {
                        items(versions, key = { it.id }) { version ->
                            VersionGridCard(
                                app, version,
                                onPlay = { app.launch(version) },
                                onContent = { app.contentOf = version },
                                onEdit = { editing = version },
                                onDelete = { deleting = version }
                            )
                        }
                    }
                    com.mclauncher.ui.components.GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                    ) {
                        items(versions.size) { index ->
                            val version = versions[index]
                            VersionRowCard(
                                app, version,
                                onPlay = { app.launch(version) },
                                onContent = { app.contentOf = version },
                                onEdit = { editing = version },
                                onDelete = { deleting = version }
                            )
                        }
                    }
                    com.mclauncher.ui.components.ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }

    if (showCreate) {
        Sheet(
            visible = true,
            onDismiss = { showCreate = false },
            title = "新建游戏版本",
            subtitle = "选择游戏版本与加载器，其余交给启动器",
            width = 820.dp,
            maxHeight = 640.dp
        ) {
            CreateVersionForm(app) { showCreate = false }
        }
    }

    editing?.let { version ->
        VersionSettingsSheet(app, version, onDismiss = { editing = null })
    }


    deleting?.let { version ->
        Sheet(
            visible = true,
            onDismiss = { deleting = null },
            title = "删除游戏版本",
            subtitle = "这个操作不可撤销",
            width = 520.dp,
            maxHeight = 340.dp
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 18.dp)) {
                Text("确定要删除「${version.name}」吗？", color = LocalTokens.current.text, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "会把这个版本的整个游戏目录移到 Windows 回收站，可以从回收站还原。",
                    color = LocalTokens.current.subtext, fontSize = 11.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton(t("取消")) { deleting = null }
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(t("移到回收站")) { app.deleteVersion(version); deleting = null }
                }
            }
        }
    }
}

/* ------------------------------ 卡片 ------------------------------ */

@Composable
private fun VersionIcon(version: Instance, size: Int) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.4f).dp)).background(tokens.field),
        contentAlignment = Alignment.Center
    ) {
        McIcons.McIconView(version.icon.ifBlank { "grass" }, (size * 0.78f).dp)
    }
}

@Composable
private fun VersionRowCard(
    app: AppState,
    version: Instance,
    onPlay: () -> Unit,
    onContent: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalTokens.current
    val running = app.runningPids.containsKey(version.id)
    GlassPanel(Modifier.fillMaxWidth().height(84.dp), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            VersionIcon(version, 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(version.name, color = tokens.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Tag(version.loaderLabel, tokens.accent2)
                    if (running) {
                        Spacer(Modifier.width(6.dp))
                        Tag("运行中", tokens.success)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${version.mcVersion} · ${version.memoryMb} MB · 上次启动 ${humanTime(version.lastPlayed)}",
                    color = tokens.subtext, fontSize = 11.sp
                )
            }
            GhostButton(t("内容"), icon = Icons.AutoMirrored.Filled.List, onClick = onContent)
            Spacer(Modifier.width(6.dp))
            GhostButton(t("设置"), icon = Icons.Filled.Settings, onClick = onEdit)
            Spacer(Modifier.width(6.dp))
            PrimaryButton(
                text = if (running) "再次启动" else "启动",
                icon = Icons.Filled.PlayArrow,
                height = 36.dp,
                onClick = onPlay
            )
            Spacer(Modifier.width(4.dp))
            CircleIconButton(Icons.Filled.Folder, size = 34.dp, onClick = { app.openGameDir(version) })
            CircleIconButton(Icons.Filled.Delete, size = 34.dp, tint = tokens.danger, onClick = onDelete)
        }
    }
}

@Composable
private fun VersionGridCard(
    app: AppState,
    version: Instance,
    onPlay: () -> Unit,
    onContent: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalTokens.current
    val running = app.runningPids.containsKey(version.id)
    GlassPanel(Modifier.height(190.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VersionIcon(version, 52)
                Spacer(Modifier.weight(1f))
                if (running) Tag("运行中", tokens.success)
                Spacer(Modifier.width(6.dp))
                // 和列表视图同一个删除入口：确认后调用 app.deleteVersion
                CircleIconButton(
                    Icons.Filled.Delete,
                    size = 30.dp,
                    tint = tokens.danger,
                    onClick = onDelete
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(version.name, color = tokens.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${version.mcVersion} · ${version.loaderLabel}", color = tokens.subtext, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(t("启动"), icon = Icons.Filled.PlayArrow, height = 34.dp, modifier = Modifier.weight(1f), onClick = onPlay)
                CircleIconButton(Icons.AutoMirrored.Filled.List, size = 34.dp, onClick = onContent)
                CircleIconButton(Icons.Filled.Settings, size = 34.dp, onClick = onEdit)
            }
        }
    }
}

@Composable
private fun ViewToggle(grid: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(tokens.field).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(false to "列表", true to "网格").forEach { pair ->
            val isGrid = pair.first
            val label = pair.second
            val selected = grid == isGrid
            val bg by animateColorAsState(
                if (selected) tokens.accent.copy(alpha = 0.25f) else Color.Transparent,
                tween(150), label = "vt"
            )
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable { onChange(isGrid) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(label, color = if (selected) tokens.accent else tokens.subtext, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EmptyVersions(onCreate: () -> Unit) {
    val tokens = LocalTokens.current
    GlassPanel(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            McIcons.McIconView("grass", 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(t("还没有任何版本"), color = tokens.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(t("从原版到 Forge、Fabric、NeoForge，一个入口全部搞定"), color = tokens.subtext, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            PrimaryButton(t("新建版本"), icon = Icons.Filled.Add, onClick = onCreate)
        }
    }
}

/* ------------------------------ 版本设置 ------------------------------ */

@Composable
private fun VersionSettingsSheet(app: AppState, version: Instance, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    var name by remember(version.id) { mutableStateOf(version.name) }
    var icon by remember(version.id) { mutableStateOf(version.icon) }
    var memory by remember(version.id) { mutableStateOf(com.mclauncher.core.SysInfo.clampMemory(version.memoryMb).toFloat()) }
    var javaMode by remember(version.id) { mutableStateOf(if (version.javaPath.isBlank()) "auto" else "custom") }
    var javaPath by remember(version.id) { mutableStateOf(version.javaPath) }
    var javaMajor by remember(version.id) { mutableStateOf(version.javaMajor) }
    var jvmArgs by remember(version.id) { mutableStateOf(version.jvmArgs) }
    var width by remember(version.id) { mutableStateOf(version.width.toString()) }
    var height by remember(version.id) { mutableStateOf(version.height.toString()) }
    var gameDir by remember(version.id) { mutableStateOf(version.gameDir) }
    val settingsState = androidx.compose.foundation.lazy.rememberLazyListState()

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = "版本设置 · ${version.name}",
        subtitle = "${version.mcVersion} · ${version.loaderLabel}",
        width = 760.dp,
        maxHeight = 720.dp
    ) {
        Box(Modifier.fillMaxWidth()) {
        LazyColumn(state = settingsState, modifier = Modifier.padding(start = 22.dp, end = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("名称"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    GlassTextField(name, { name = it }, modifier = Modifier.weight(1f))
                }
            }
            item {
                Text(t("图标"), color = tokens.subtext, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McIcons.all.forEach { item ->
                        val selected = icon == item.id
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) tokens.accent.copy(alpha = 0.25f) else tokens.field)
                                .border(1.dp, if (selected) tokens.accent else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { icon = item.id },
                            contentAlignment = Alignment.Center
                        ) {
                            McIcons.McIconView(item.id, 28.dp)
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("内存"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = memory,
                        onValueChange = { memory = it },
                        valueRange = 1024f..com.mclauncher.core.SysInfo.maxMemoryMb.toFloat(),
                        steps = com.mclauncher.core.SysInfo.stepsForMax(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
                    )
                    Column {
                        Text("${memory.toInt()} MB", color = tokens.text, fontSize = 11.sp)
                        Text(
                            t("上限") + " ${com.mclauncher.core.SysInfo.maxMemoryMb} MB · " +
                                t("本机") + " ${com.mclauncher.core.SysInfo.totalRamText()}",
                            color = tokens.subtext.copy(alpha = 0.7f), fontSize = 9.sp
                        )
                    }
                }
            }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Java", color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                        FilterChip(t("自动匹配"), javaMode == "auto") { javaMode = "auto" }
                        Spacer(Modifier.width(6.dp))
                        FilterChip(t("指定路径"), javaMode == "custom") { javaMode = "custom" }
                        if (javaMode == "auto") {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (javaMajor > 0) "按版本需求自动选择（Java $javaMajor）" else "按版本需求自动选择",
                                color = tokens.subtext, fontSize = 10.sp
                            )
                        }
                    }
                    if (javaMode == "custom") {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlassTextField(javaPath, { javaPath = it }, placeholder = t("java.exe 路径"), modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            GhostButton(t("浏览")) {
                                val dialog = FileDialog(null as Frame?, "选择 java.exe", FileDialog.LOAD)
                                dialog.isVisible = true
                                val f = dialog.file
                                if (f != null) {
                                    javaPath = File(dialog.directory, f).absolutePath
                                    javaMajor = com.mclauncher.java.JavaRuntime.probe(java.nio.file.Paths.get(javaPath))?.major ?: javaMajor
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    Text(t("JVM 参数"), color = tokens.subtext, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    GlassTextField(jvmArgs, { jvmArgs = it }, placeholder = t("例如 -XX:+UseZGC"), modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("窗口"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                    GlassTextField(width, { width = it.filter(Char::isDigit) }, modifier = Modifier.width(90.dp))
                    Text(" × ", color = tokens.subtext)
                    GlassTextField(height, { height = it.filter(Char::isDigit) }, modifier = Modifier.width(90.dp))
                }
            }
            item {
                Column {
                    Text(t("游戏目录"), color = tokens.subtext, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassTextField(gameDir, { gameDir = it }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GhostButton(t("选择")) {
                            app.chooseDirectory("选择游戏目录")?.let { gameDir = it }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GhostButton(t("打开目录")) { app.openGameDir(version) }
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("导出启动脚本")) { app.exportLaunchScript(version) }
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(t("保存")) {
                        app.applyVersionSettings(
                            version.copy(
                                name = name.ifBlank { version.name },
                                icon = icon,
                                memoryMb = memory.toInt(),
                                javaPath = if (javaMode == "custom") javaPath else "",
                                javaMajor = javaMajor,
                                jvmArgs = jvmArgs,
                                width = width.toIntOrNull() ?: version.width,
                                height = height.toIntOrNull() ?: version.height,
                                gameDir = gameDir
                            )
                        )
                        onDismiss()
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
            com.mclauncher.ui.components.ListScrollbar(settingsState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/* ------------------------------ 内容管理 ------------------------------ */

@Composable
fun ContentSheet(app: AppState, version: Instance, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    val iris = app.hasIris(version)
    var tab by remember { mutableStateOf(System.getenv("NGML_CONTENT_TAB") ?: "mods") }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var saves by remember { mutableStateOf<List<SaveEntry>>(emptyList()) }
    var shots by remember { mutableStateOf<List<Path>>(emptyList()) }
    val contentState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun reload() {
        items = app.listContent(version, tab)
        if (tab == "saves") saves = loadSaves(version)
        if (tab == "screenshots") shots = loadShots(version)
    }

    LaunchedEffect(tab, version.id) {
        withContext(Dispatchers.IO) { reload() }
    }

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = "内容管理 · ${version.name}",
        subtitle = "MOD / 光影 / 资源包 / 存档 / 截图",
        width = 860.dp,
        maxHeight = 720.dp,
        fillHeight = true
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            GlowTabRow(
                tabs = listOf(
                    "mods" to "模组",
                    "shaderpacks" to "光影",
                    "resourcepacks" to "资源包",
                    "saves" to "存档",
                    "screenshots" to "截图"
                ),
                selected = tab,
                onSelect = { tab = it }
            )
            Spacer(Modifier.height(12.dp))

            if (tab == "shaderpacks" && !iris) {
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "这个版本还没装 Iris，光影不会生效。可以先去 MOD 工坊装 Iris Shaders。",
                        color = tokens.danger, fontSize = 11.sp, modifier = Modifier.padding(12.dp)
                    )
                }
            }

            val folder = when (tab) {
                "saves" -> "saves"
                "screenshots" -> "screenshots"
                else -> tab
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (tab) {
                        "saves" -> "${saves.size} 个存档"
                        "screenshots" -> "${shots.size} 张截图"
                        else -> "${items.size} 个文件"
                    },
                    color = tokens.subtext, fontSize = 11.sp
                )
                if (tab == "mods") {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (app.modUpdateBusy) app.modUpdateMessage
                        else app.modUpdateMessage.ifBlank { "" },
                        color = tokens.accent, fontSize = 10.sp, maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                if (tab == "mods") {
                    GhostButton(if (app.modUpdateBusy) "检测中…" else "检测更新") {
                        app.checkModUpdates(version)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (app.modUpdates.isNotEmpty()) {
                        PrimaryButton("一键更新（${app.modUpdates.size}）") {
                            app.updateMods(version, app.modUpdates)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    GhostButton("清除重复") {
                        app.removeDuplicateMods(version)
                        reload()
                    }
                    Spacer(Modifier.width(8.dp))
                }
                GhostButton(t("打开文件夹")) {
                    com.mclauncher.core.Fs.openFolder(version.gameDirPath().resolve(folder))
                }
                if (tab != "saves" && tab != "screenshots") {
                    Spacer(Modifier.width(8.dp))
                    GhostButton(t("导入文件")) { app.importContent(version, tab); reload() }
                }
            }

            GlassPanel(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(14.dp)) {
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        "saves" -> SaveList(app, saves, onChanged = { reload() })
                        "screenshots" -> ShotGrid(app, shots, onChanged = { reload() })
                        else -> ContentList(app, version, tab, items, onChanged = { reload() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ContentList(
    app: AppState,
    version: Instance,
    tab: String,
    items: List<ContentItem>,
    onChanged: () -> Unit
) {
    val tokens = LocalTokens.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t("这里还是空的"), color = tokens.subtext, fontSize = 12.sp)
        }
        return
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val updates = app.modUpdates.associateBy { it.file }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(8.dp)) {
        items(items.size) { index ->
            val item = items[index]
            val update = if (tab == "mods") updates[item.path] else null
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        color = if (item.enabled) tokens.text else tokens.subtext,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Text(
                        com.mclauncher.core.formatBytes(item.size) + (if (item.enabled) "" else " · 已禁用"),
                        color = tokens.subtext.copy(alpha = 0.7f), fontSize = 10.sp
                    )
                    if (update != null) {
                        Text(
                            "↑ ${update.installed} → ${update.latest}",
                            color = tokens.accent, fontSize = 10.sp
                        )
                    }
                }
                if (update != null) {
                    GhostButton("更新") { app.updateMods(version, listOf(update)) }
                    Spacer(Modifier.width(6.dp))
                }
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { app.toggleContent(item); onChanged() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = tokens.accent
                    )
                )
                Spacer(Modifier.width(6.dp))
                CircleIconButton(
                    Icons.Filled.Delete,
                    size = 30.dp,
                    tint = tokens.danger,
                    onClick = {
                        app.confirmDelete(
                            "删除 ${item.name}？",
                            "会从当前版本目录里永久删除，不能恢复。"
                        ) {
                            app.deleteContent(item)
                            onChanged()
                        }
                    }
                )
            }
        }
    }
}

data class SaveEntry(val dir: Path, val name: String, val size: Long, val modified: Long)

private fun loadSaves(version: Instance): List<SaveEntry> {
    val dir = version.gameDirPath().resolve("saves")
    if (!java.nio.file.Files.isDirectory(dir)) return emptyList()
    return runCatching {
        val dirs: List<Path> = java.nio.file.Files.list(dir).use { s -> s.collect(java.util.stream.Collectors.toList()) }
        dirs.filter { java.nio.file.Files.isDirectory(it) }
            .map { p ->
                SaveEntry(
                    dir = p,
                    name = p.fileName.toString(),
                    size = folderSize(p),
                    modified = runCatching {
                        java.nio.file.Files.getLastModifiedTime(p).toMillis()
                    }.getOrDefault(0L)
                )
            }
            .sortedByDescending { it.modified }
    }.getOrDefault(emptyList())
}

private fun folderSize(dir: Path): Long = runCatching {
    val files: List<Path> = java.nio.file.Files.walk(dir).use { s -> s.collect(java.util.stream.Collectors.toList()) }
    files.filter { java.nio.file.Files.isRegularFile(it) }
        .sumOf { p -> runCatching { java.nio.file.Files.size(p) }.getOrDefault(0L) }
}.getOrDefault(0L)

@Composable
private fun SaveList(app: AppState, saves: List<SaveEntry>, onChanged: () -> Unit) {
    val tokens = LocalTokens.current
    if (saves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有存档", color = tokens.subtext, fontSize = 12.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        items(saves) { save ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(save.name, color = tokens.text, fontSize = 12.sp, maxLines = 1)
                    Text(
                        com.mclauncher.core.formatBytes(save.size) + " · " +
                            java.time.Instant.ofEpochMilli(save.modified)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        color = tokens.subtext, fontSize = 10.sp
                    )
                }
                GhostButton("打开") { com.mclauncher.core.Fs.openFolder(save.dir) }
                Spacer(Modifier.width(6.dp))
                CircleIconButton(Icons.Filled.Delete, size = 30.dp, tint = tokens.danger) {
                    app.confirmDelete("删除存档 ${save.name}？", "整个存档文件夹会被删除，不能恢复。") {
                        runCatching { com.mclauncher.core.Fs.deleteRecursively(save.dir) }
                        app.toast("已删除存档 ${save.name}")
                        onChanged()
                    }
                }
            }
        }
    }
}

private fun loadShots(version: Instance): List<Path> {
    val dir = version.gameDirPath().resolve("screenshots")
    if (!java.nio.file.Files.isDirectory(dir)) return emptyList()
    return runCatching {
        val all: List<Path> = java.nio.file.Files.list(dir).use { s -> s.collect(java.util.stream.Collectors.toList()) }
        all.filter {
            java.nio.file.Files.isRegularFile(it) &&
                (it.toString().endsWith(".png", true) || it.toString().endsWith(".jpg", true))
        }.sortedByDescending { p ->
            runCatching { java.nio.file.Files.getLastModifiedTime(p).toMillis() }.getOrDefault(0L)
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun ShotGrid(app: AppState, shots: List<Path>, onChanged: () -> Unit) {
    val tokens = LocalTokens.current
    if (shots.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有截图", color = tokens.subtext, fontSize = 12.sp)
        }
        return
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(shots.size) { index ->
            val path = shots[index]
            val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null, path
            ) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        java.io.FileInputStream(path.toFile()).use {
                            javax.imageio.ImageIO.read(it).toImageBitmap()
                        }
                    }.getOrNull()
                }
            }
            Column {
                Box(
                    Modifier.fillMaxWidth().height(96.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.field)
                        .clickable {
                            runCatching { java.awt.Desktop.getDesktop().open(path.toFile()) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(bitmap!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text("加载中…", color = tokens.subtext, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(path.fileName.toString(), color = tokens.subtext, fontSize = 9.sp, maxLines = 1,
                        modifier = Modifier.weight(1f))
                    CircleIconButton(Icons.Filled.Delete, size = 22.dp, tint = tokens.danger) {
                        app.confirmDelete("删除截图 ${path.fileName}？", "文件会从磁盘删除，不能恢复。") {
                            runCatching { java.nio.file.Files.deleteIfExists(path) }
                            app.toast("已删除截图")
                            onChanged()
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun GlowTabRow(tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val tokens = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { pair ->
            val id = pair.first
            val label = pair.second
            val active = id == selected
            val bg by animateColorAsState(
                if (active) tokens.accent.copy(alpha = 0.18f) else Color.Transparent,
                tween(180), label = "tab"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    color = if (active) tokens.accent else tokens.subtext,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(22.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, tokens.accent, Color.Transparent)))
                    )
                }
            }
        }
    }
}

/* ------------------------------ 新建版本 ------------------------------ */

@Composable
private fun CreateVersionForm(app: AppState, onDone: () -> Unit) {
    val tokens = LocalTokens.current
    val manifest = app.manifest

    var name by remember { mutableStateOf("") }
    var mcVersion by remember { mutableStateOf(manifest?.latest?.release ?: "1.21.1") }
    var showSnapshots by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var loader by remember { mutableStateOf("vanilla") }
    val displayName = name.ifBlank { mcVersion }
    val nameTaken = app.instances.any { it.name.equals(displayName, ignoreCase = true) }
    var optifineBuilds by remember { mutableStateOf<List<com.mclauncher.meta.Loaders.OptifineBuild>>(emptyList()) }
    var optifineLoading by remember { mutableStateOf(false) }
    var optifinePick by remember { mutableStateOf("") }
    var loaderVersion by remember { mutableStateOf("") }
    var loaderVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingLoaders by remember { mutableStateOf(false) }
    var loaderError by remember { mutableStateOf("") }
    var memory by remember { mutableStateOf(com.mclauncher.core.SysInfo.defaultMemoryMb.toFloat()) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(-1f) }
    val mcListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val loaderListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(loader, mcVersion) {
        if (loader == "forge_optifine" && mcVersion.isNotBlank()) {
            optifineLoading = true
            optifineBuilds = com.mclauncher.meta.Loaders.optifineBuilds(mcVersion)
            if (optifinePick.isBlank() && optifineBuilds.isNotEmpty()) {
                optifinePick = "${optifineBuilds.first().type}/${optifineBuilds.first().patch}"
            }
            optifineLoading = false
        }

        if (loader == "vanilla") {
            loaderVersions = emptyList()
            loaderVersion = ""
            loaderError = ""
            return@LaunchedEffect
        }
        loadingLoaders = true
        loaderError = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                when (loader) {
                    "fabric" -> Loaders.fabricLoaders(mcVersion)
                    "forge" -> Loaders.forgeVersions()[mcVersion] ?: emptyList()
                    "neoforge" -> Loaders.neoForgeVersions()[mcVersion] ?: emptyList()
                    "forge_optifine" -> Loaders.forgeVersions()[mcVersion] ?: emptyList()
                    else -> emptyList()
                }
            }
        }
        loaderVersions = result.getOrDefault(emptyList())
        if (loaderVersions.isEmpty()) {
            loaderError = result.exceptionOrNull()?.message ?: "没有找到该加载器在这个游戏版本下的可用版本"
        }
        loaderVersion = loaderVersion.takeIf { it in loaderVersions } ?: loaderVersions.firstOrNull() ?: ""
        loadingLoaders = false
    }

    val versions = remember(manifest, showSnapshots, query) {
        manifest?.versions
            ?.filter { showSnapshots || it.type == "release" }
            ?.filter { query.isBlank() || it.id.contains(query, true) }
            ?.take(120)
            ?: emptyList()
    }

    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = t("版本名称（留空用游戏版本号）"),
                modifier = Modifier.weight(1f)
            )
            Text(
                "内存 ${memory.toInt()} MB / 上限 ${com.mclauncher.core.SysInfo.maxMemoryMb} MB",
                color = tokens.subtext, fontSize = 11.sp
            )
            Slider(
                value = memory,
                onValueChange = { memory = it },
                valueRange = 1024f..com.mclauncher.core.SysInfo.maxMemoryMb.toFloat(),
                steps = com.mclauncher.core.SysInfo.stepsForMax(),
                modifier = Modifier.width(160.dp),
                colors = SliderDefaults.colors(thumbColor = tokens.accent, activeTrackColor = tokens.accent)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1.15f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("游戏版本"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    FilterChip(t("快照"), showSnapshots) { showSnapshots = !showSnapshots }
                }
                Spacer(Modifier.height(8.dp))
                GlassTextField(query, { query = it }, placeholder = t("搜索版本，如 1.20 / 24w"), height = 38.dp)
                Spacer(Modifier.height(8.dp))
                GlassPanel(Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(14.dp)) {
                    if (versions.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(t("正在获取版本列表…"), color = tokens.subtext, fontSize = 12.sp)
                        }
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = mcListState,
                                modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 16.dp)
                            ) {
                                items(versions.size) { index ->
                                    val entry = versions[index]
                                    VersionPickRow(
                                        id = entry.id,
                                        type = entry.type,
                                        selected = entry.id == mcVersion
                                    ) { mcVersion = entry.id }
                                }
                            }
                            com.mclauncher.ui.components.ListScrollbar(mcListState, Modifier.align(Alignment.CenterEnd))
                        }
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                Text(t("加载器"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                com.mclauncher.ui.components.HScroll {
                    FilterChip(t("原版"), loader == "vanilla") { loader = "vanilla" }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("Fabric", loader == "fabric") { loader = "fabric" }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("Forge", loader == "forge") { loader = "forge" }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("NeoForge", loader == "neoforge") { loader = "neoforge" }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("Forge + OptiFine", loader == "forge_optifine") { loader = "forge_optifine" }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loader == "vanilla" -> {
                        GlassPanel(Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                                Text(t("原版游戏"), color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text(t("不安装加载器。之后也可以再新建一个 Fabric / Forge 版本。"), color = tokens.subtext, fontSize = 11.sp)
                            }
                        }
                    }
                    loadingLoaders -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingDots()
                            Spacer(Modifier.width(8.dp))
                            Text(t("获取加载器版本…"), color = tokens.subtext, fontSize = 11.sp)
                        }
                    }
                    loaderVersions.isEmpty() -> {
                        GlassPanel(Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
                                Text(t("没有可用版本"), color = tokens.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text(loaderError, color = tokens.subtext, fontSize = 10.sp)
                            }
                        }
                    }
                    else -> {
                        GlassPanel(Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(14.dp)) {
                            Box(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = loaderListState,
                                    modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 16.dp)
                                ) {
                                    items(loaderVersions.size) { index ->
                                        val v = loaderVersions[index]
                                        VersionPickRow(id = v, type = "", selected = v == loaderVersion) { loaderVersion = v }
                                    }
                                }
                                com.mclauncher.ui.components.ListScrollbar(loaderListState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                    }
                }

                if (loader == "forge_optifine") {
                    Spacer(Modifier.height(10.dp))
                    Text(t("OptiFine 版本"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (optifineLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingDots()
                            Spacer(Modifier.width(8.dp))
                            Text(t("正在获取 OptiFine 版本…"), color = tokens.subtext, fontSize = 11.sp)
                        }
                    } else if (optifineBuilds.isEmpty()) {
                        Text(t("这个游戏版本没有找到 OptiFine"), color = tokens.danger, fontSize = 11.sp)
                    } else {
                        com.mclauncher.ui.components.HScroll {
                            optifineBuilds.take(12).forEach { build ->
                                FilterChip(
                                    "${build.type} ${build.patch}",
                                    optifinePick == "${build.type}/${build.patch}"
                                ) { optifinePick = "${build.type}/${build.patch}" }
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                }
            }
        }

        if (nameTaken) {
            Spacer(Modifier.height(10.dp))
            Text(
                t("已经有同名版本了，换个名字再创建"),
                color = tokens.danger, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        if (busy) {
            Text(stage.ifBlank { "准备中…" }, color = tokens.subtext, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            ProgressBar(if (progress < 0f) 0f else progress)
            Spacer(Modifier.height(10.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            GhostButton(t("取消"), enabled = !busy, onClick = { onDone() })
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                text = if (busy) "正在安装…" else "创建并下载",
                enabled = !busy && mcVersion.isNotBlank() && !nameTaken &&
                    (loader == "vanilla" || loaderVersion.isNotBlank()),
                onClick = {
                    busy = true
                    stage = "创建版本"
                    progress = -1f
                    app.createInstance(
                        name = name,
                        mcVersion = mcVersion,
                        loader = loader,
                        loaderVersion = loaderVersion,
                        optifinePick = optifinePick,
                        memoryMb = memory.toInt(),
                        onStage = { s, p -> stage = s; if (p >= 0f) progress = p }
                    ) { instance ->
                        busy = false
                        if (instance != null) onDone()
                    }
                }
            )
        }
    }
}

@Composable
private fun VersionPickRow(id: String, type: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) tokens.accent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            id,
            color = if (selected) tokens.accent else tokens.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.weight(1f))
        if (type.isNotEmpty() && type != "release") {
            Tag(
                when (type) {
                    "snapshot" -> "快照"
                    "old_beta" -> "旧 Beta"
                    "old_alpha" -> "旧 Alpha"
                    else -> type
                },
                tokens.subtext
            )
        }
    }
}