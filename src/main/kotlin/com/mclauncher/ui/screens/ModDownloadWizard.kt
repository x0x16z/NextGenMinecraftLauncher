package com.mclauncher.ui.screens

import com.mclauncher.ui.t

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.core.formatBytes
import com.mclauncher.mods.ModPlanItem
import com.mclauncher.mods.ModProject
import com.mclauncher.mods.ModSource
import com.mclauncher.mods.ModVersionInfo
import com.mclauncher.mods.ModsApi
import com.mclauncher.mods.ModPlanner
import com.mclauncher.ui.AppState
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.ListScrollbar
import com.mclauncher.ui.components.LoadingDots
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.components.Tag
import com.mclauncher.ui.components.rememberRemoteImage
import com.mclauncher.ui.theme.LocalTokens
import kotlinx.coroutines.Dispatchers
import com.mclauncher.ui.components.GhostButton
import kotlinx.coroutines.withContext

/**
 * MOD 下载向导（参考 CKAN）：
 *  ① 选游戏版本
 *  ② 选这个版本下的 MOD 版本
 *  ③ 确认依赖 + 推荐，逐项选下载源
 *  ④ 开始下载，进度进「下载面板」
 */
@Composable
fun ModDownloadWizard(
    app: AppState,
    project: ModProject,
    initialGameVersion: String,
    initialLoader: String,
    onDismiss: () -> Unit
) {
    val tokens = LocalTokens.current
    val icon = rememberRemoteImage(project.iconUrl)

    var step by remember {
        val forced = System.getenv("NGML_WIZARD_STEP")?.toIntOrNull() ?: 0
        mutableStateOf(forced)
    }
    var loading by remember { mutableStateOf(true) }
    var allVersions by remember { mutableStateOf<List<ModVersionInfo>>(emptyList()) }
    var gameVersion by remember { mutableStateOf(initialGameVersion) }
    var selected by remember { mutableStateOf<ModVersionInfo?>(null) }
    var plan by remember { mutableStateOf<List<ModPlanItem>>(emptyList()) }
    var resolving by remember { mutableStateOf(false) }
    var targetInstanceId by remember { mutableStateOf(app.activeInstanceId) }
    val gvState = rememberLazyListState()
    val versionState = rememberLazyListState()

    LaunchedEffect(project.id) {
        loading = true
        allVersions = withContext(Dispatchers.IO) {
            if (project.source == ModSource.CURSEFORGE) {
                ModsApi.versionsCurseforge(project.id, null, initialLoader.ifBlank { null })
            } else {
                ModsApi.versionsModrinth(project.id, null, initialLoader.ifBlank { null })
            }
        }
        loading = false
    }

    val gameVersions = remember(allVersions) {
        allVersions.flatMap { it.gameVersions }.distinct().sortedDescending().take(60)
    }
    val filtered = remember(allVersions, gameVersion) {
        if (gameVersion.isBlank()) allVersions else allVersions.filter { it.gameVersions.contains(gameVersion) }
    }
    LaunchedEffect(filtered) {
        if (selected == null || filtered.none { it.id == selected?.id }) selected = filtered.firstOrNull()
    }

    // 进入第 3 步时解析依赖
    LaunchedEffect(step) {
        if (step == 3 && plan.isEmpty() && selected != null) {
            resolving = true
            val target = app.instances.firstOrNull { it.id == targetInstanceId } ?: app.activeInstance
            val gv = target?.mcVersion ?: gameVersion
            val ld = target?.loader ?: initialLoader
            val self = ModPlanner.selfItem(project, selected!!, project.source)
            val required = withContext(Dispatchers.IO) {
                runCatching { ModPlanner.resolveRequired(selected!!, gv, ld.ifBlank { null }) { _ -> null } }.getOrDefault(emptyList())
            }
            val recommended = withContext(Dispatchers.IO) {
                runCatching {
                    ModPlanner.recommend(project, gv.ifBlank { null }, ld.ifBlank { null }, setOf(project.id) + required.map { it.projectId })
                }.getOrDefault(emptyList())
            }
            plan = listOf(self) + required + recommended
            resolving = false
        }
    }

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = project.title,
        subtitle = when (step) {
            0 -> t("第 1 步 · 选择游戏版本")
            1 -> t("第 2 步 · 选择 MOD 版本")
            2 -> t("第 3 步 · 装到哪个游戏版本")
            else -> t("第 4 步 · 确认依赖与下载源")
        },
        width = 900.dp,
        maxHeight = 780.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            // 顶部项目信息
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(13.dp)).background(tokens.field),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text(project.title.take(1), color = tokens.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(project.summary, color = tokens.text.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        project.categories.take(3).forEach { Tag(it, tokens.accent2) }
                    }
                }
                // 步骤指示
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { i ->
                        Box(
                            Modifier.size(8.dp).clip(RoundedCornerShape(50))
                                .background(if (i <= step) tokens.accent else tokens.field)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (step) {
                0 -> {
                    Text(t("游戏版本（可滚动查看更老的版本）"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(150.dp)) {
                        LazyColumn(
                            state = gvState,
                            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item { GameVersionRow("全部版本", gameVersion.isBlank()) { gameVersion = "" } }
                            items(gameVersions.size) { i ->
                                val v = gameVersions[i]
                                GameVersionRow(v, gameVersion == v) { gameVersion = v }
                            }
                        }
                        ListScrollbar(gvState, Modifier.align(Alignment.CenterEnd))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilterChip(t("下一步"), true) { step = 1 }
                    }
                }

                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                t("选择这个 MOD 的版本"),
                                color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                buildString {
                                    append(t("共")).append(" ").append(filtered.size).append(" ").append(t("个版本"))
                                    if (gameVersion.isNotBlank()) append(" · ").append(gameVersion)
                                    if (initialLoader.isNotBlank()) append(" · ").append(initialLoader)
                                },
                                color = tokens.subtext, fontSize = 10.sp
                            )
                        }
                        selected?.let { Tag(t("已选") + " " + it.versionNumber, tokens.accent) }
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassPanel(Modifier.fillMaxWidth().height(318.dp), shape = RoundedCornerShape(14.dp)) {
                        when {
                            loading -> Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LoadingDots()
                                Spacer(Modifier.height(8.dp))
                                Text(t("正在获取版本列表…"), color = tokens.subtext, fontSize = 11.sp)
                            }
                            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(t("这个游戏版本下没有可用文件"), color = tokens.subtext, fontSize = 12.sp)
                            }
                            else -> Box(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = versionState,
                                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filtered.size) { index ->
                                        val version = filtered[index]
                                        val chosen = selected?.id == version.id
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (chosen) tokens.accent.copy(alpha = 0.14f) else Color.Transparent)
                                                .clickable { selected = version }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    version.versionNumber,
                                                    color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Tag(
                                                    version.releaseType,
                                                    when (version.releaseType) {
                                                        "release" -> tokens.success
                                                        "beta" -> tokens.accent
                                                        else -> tokens.danger
                                                    }
                                                )
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    version.published.take(10),
                                                    color = tokens.subtext.copy(alpha = 0.7f), fontSize = 9.sp
                                                )
                                                if (chosen) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Filled.Check, null, tint = tokens.accent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            if (version.name.isNotBlank()) {
                                                Text(
                                                    version.name,
                                                    color = tokens.subtext, fontSize = 10.sp, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                version.gameVersions.take(4).forEach { gv -> Tag(gv, tokens.accent2) }
                                                version.loaders.take(2).forEach { ld -> Tag(ld, tokens.subtext) }
                                            }
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                buildString {
                                                    version.file?.let { f ->
                                                        if (f.fileName.isNotBlank()) append(f.fileName).append(" · ")
                                                        if (f.size > 0) {
                                                            append(com.mclauncher.core.formatBytes(f.size)).append(" · ")
                                                        }
                                                    }
                                                    append(compactCount(version.downloads)).append(" ").append(t("次下载"))
                                                    if (version.dependencies.isNotEmpty()) {
                                                        append(" · ").append(version.dependencies.size).append(" ").append(t("个依赖"))
                                                    }
                                                },
                                                color = tokens.subtext.copy(alpha = 0.65f), fontSize = 9.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                ListScrollbar(versionState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GhostButton(t("上一步")) { step = 0 }
                        Spacer(Modifier.weight(1f))
                        Text(
                            selected?.let { t("已选") + " " + it.versionNumber } ?: t("请选择一个版本"),
                            color = if (selected != null) tokens.accent else tokens.subtext,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        PrimaryButton(t("下一步"), enabled = selected != null) {
                            plan = emptyList()
                            step = 2
                        }
                    }
                }
                2 -> {
                    Text(
                        t("下载到哪个游戏版本"),
                        color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        t("每个版本是独立的游戏目录（版本隔离），MOD 只会装进你选中的这个版本"),
                        color = tokens.subtext, fontSize = 10.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassPanel(Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(14.dp)) {
                        if (app.instances.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(t("还没有任何版本，先去「游戏版本」新建一个"), color = tokens.subtext, fontSize = 12.sp)
                            }
                        } else {
                            val targetState = rememberLazyListState()
                            Box(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = targetState,
                                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(app.instances.size) { index ->
                                        val version = app.instances[index]
                                        val chosen = version.id == targetInstanceId
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (chosen) tokens.accent.copy(alpha = 0.14f) else Color.Transparent)
                                                .clickable { targetInstanceId = version.id }
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    version.name,
                                                    color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    "${version.mcVersion} · ${version.loaderLabel}" +
                                                        (if (version.gameDir.isBlank()) " · " + t("共享游戏目录") else " · " + t("独立目录")),
                                                    color = tokens.subtext, fontSize = 10.sp
                                                )
                                            }
                                            Text(
                                                version.gameDir.ifBlank { app.minecraftRoot.toString() },
                                                color = tokens.subtext.copy(alpha = 0.55f), fontSize = 9.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(240.dp)
                                            )
                                            if (chosen) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Filled.Check, null, tint = tokens.accent, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                                ListScrollbar(targetState, Modifier.align(Alignment.CenterEnd))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GhostButton(t("上一步")) { step = 1 }
                        Spacer(Modifier.weight(1f))
                        Text(
                            app.instances.firstOrNull { it.id == targetInstanceId }?.let {
                                t("装到") + " " + it.name
                            } ?: t("请选择一个版本"),
                            color = if (targetInstanceId.isNotBlank()) tokens.accent else tokens.subtext,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        PrimaryButton(t("下一步"), enabled = targetInstanceId.isNotBlank()) {
                            plan = emptyList()
                            step = 3
                        }
                    }
                }
                else -> {
                    if (resolving) {
                        Column(
                            Modifier.fillMaxWidth().height(240.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LoadingDots()
                            Spacer(Modifier.height(8.dp))
                            Text(t("正在解析依赖与推荐…"), color = tokens.subtext, fontSize = 11.sp)
                        }
                    } else {
                        Text(t("下载内容（勾选要装的项目）"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        GlassPanel(Modifier.fillMaxWidth().height(330.dp), shape = RoundedCornerShape(14.dp)) {
                            val listState = rememberLazyListState()
                            Box(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp)
                                ) {
                                    items(plan.size) { index ->
                                        val item = plan[index]
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(20.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (item.selected) tokens.accent else tokens.field)
                                                    .border(1.dp, tokens.border, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        plan = plan.toMutableList().also {
                                                            it[index] = item.copy(selected = !item.selected)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (item.selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(item.projectTitle, color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                    Spacer(Modifier.width(6.dp))
                                                    Tag(
                                                        when (item.role) {
                                                            "self" -> "本体"
                                                            "required" -> "依赖"
                                                            else -> "推荐"
                                                        },
                                                        when (item.role) {
                                                            "required" -> tokens.danger
                                                            "recommended" -> tokens.success
                                                            else -> tokens.accent
                                                        }
                                                    )
                                                }
                                                Text(
                                                    "${item.version.versionNumber} · ${formatBytes(item.version.file?.size ?: 0)}",
                                                    color = tokens.subtext, fontSize = 10.sp
                                                )
                                            }
                                            // 下载源
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                SourceChip("Modrinth", item.source == ModSource.MODRINTH) {
                                                    plan = plan.toMutableList().also { it[index] = item.copy(source = ModSource.MODRINTH) }
                                                }
                                                // 没填 key 也走 MCIM 镜像，所以永远可选
                                                val cfUsable = true
                                                SourceChip(
                                                    "CurseForge",
                                                    item.source == ModSource.CURSEFORGE,
                                                    enabled = cfUsable
                                                ) {
                                                    plan = plan.toMutableList().also { it[index] = item.copy(source = ModSource.CURSEFORGE) }
                                                }
                                            }
                                        }
                                    }
                                }
                                ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("下载到版本"), color = tokens.subtext, fontSize = 11.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    app.instances.take(4).forEach { inst ->
                                        FilterChip(inst.name, targetInstanceId == inst.id) { targetInstanceId = inst.id }
                                    }
                                    if (app.instances.isEmpty()) Text(t("先创建一个版本"), color = tokens.danger, fontSize = 11.sp)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "已选 ${plan.count { it.selected }} 项 · ${formatBytes(plan.filter { it.selected }.sumOf { it.version.file?.size ?: 0 })}",
                                    color = tokens.text, fontSize = 11.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    FilterChip(t("上一步"), false) { step = 1 }
                                    Spacer(Modifier.width(8.dp))
                                    PrimaryButton(
                                        text = "开始下载",
                                        enabled = plan.any { it.selected } && app.instances.isNotEmpty()
                                    ) {
                                        val target = app.instances.firstOrNull { it.id == targetInstanceId } ?: app.instances.first()
                                        val selectedItems = plan.filter { it.selected }
                                        app.startDownloadPlan(project.title, selectedItems, target)
                                        // 不再弹详细面板：关掉向导，让侧边栏的下载栏目闪烁
                                        app.showDownloadPanel = false
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    !enabled -> tokens.danger.copy(alpha = 0.14f)
                    selected -> tokens.accent.copy(alpha = 0.24f)
                    else -> tokens.field
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = when {
                !enabled -> tokens.danger
                selected -> tokens.accent
                else -> tokens.subtext
            },
            fontSize = 10.sp
        )
    }
}

@Composable
private fun GameVersionRow(version: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.accent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            version,
            color = if (selected) tokens.accent else tokens.text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Check, null, tint = tokens.accent, modifier = Modifier.size(12.dp))
        }
    }
}
/** 下载量简写：1.2k / 3.4M */
private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fk", value / 1_000.0)
    else -> value.toString()
}