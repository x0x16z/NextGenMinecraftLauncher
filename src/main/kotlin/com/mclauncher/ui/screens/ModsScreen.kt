package com.mclauncher.ui.screens

import com.mclauncher.ui.t

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import com.mclauncher.instance.Instance
import com.mclauncher.mods.MOD_CATEGORIES
import com.mclauncher.mods.ModProject
import com.mclauncher.mods.ModSource
import com.mclauncher.mods.ModVersionInfo
import com.mclauncher.mods.ModsApi
import com.mclauncher.ui.AppState
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GhostButton
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.GlassTextField
import com.mclauncher.ui.components.LoadingDots
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.ProgressBar
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.components.Tag
import com.mclauncher.ui.components.rememberRemoteImage
import com.mclauncher.ui.theme.LocalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 24

@Composable
fun ModsScreen(app: AppState) {
    val tokens = LocalTokens.current
    val active: Instance? = app.activeInstance

    var query by remember { mutableStateOf("") }
    var gameVersion by remember(active?.mcVersion) { mutableStateOf(active?.mcVersion ?: "") }
    var loader by remember(active?.loader) {
        val l = when (active?.loader) {
            "fabric" -> "fabric"
            "forge", "forge_optifine" -> "forge"
            "neoforge" -> "neoforge"
            "quilt" -> "quilt"
            else -> ""
        }
        mutableStateOf(l)
    }
    var source by remember { mutableStateOf(ModSource.MODRINTH) }
    var category by remember { mutableStateOf("全部") }
    var sort by remember { mutableStateOf("relevance") }
    var grid by remember { mutableStateOf(true) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    var results by remember { mutableStateOf<List<ModProject>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ModProject?>(null) }
    var packDetail by remember { mutableStateOf<ModProject?>(null) }
    var mode by remember { mutableStateOf("mod") }

    LaunchedEffect(query, gameVersion, loader, source, category, sort, mode) {
        page = 0
        loading = true
        delay(320)
        val cats = MOD_CATEGORIES.firstOrNull { it.first == category }?.second ?: emptyList()
        results = withContext(Dispatchers.IO) {
            if (mode == "modpack") {
                com.mclauncher.mods.ModpackApi.search(query, gameVersion.ifBlank { null })
            } else {
                ModsApi.searchModrinth(
                    query, gameVersion.ifBlank { null }, loader.ifBlank { null }, cats, sort, 0, PAGE_SIZE
                )
            }
        }
        // 旧版本 / Forge 之类的筛选经常一个都搜不到，自动落回"全部"，免得看着像坏了
        if (results.isEmpty() && mode == "mod" && (gameVersion.isNotBlank() || loader.isNotBlank())) {
            results = withContext(Dispatchers.IO) {
                ModsApi.searchModrinth(query, null, null, cats, sort, 0, PAGE_SIZE)
            }
            if (results.isNotEmpty()) {
                gameVersion = ""
                loader = ""
                category = "全部"
                app.toast("当前筛选（${active?.mcVersion ?: ""} / ${active?.loaderLabel ?: ""}）没有结果，已切到全部")
            }
        }
        loading = false
    }

    suspend fun loadPage(target: Int) {
        if (target < 0) return
        loadingMore = true
        val cats = MOD_CATEGORIES.firstOrNull { it.first == category }?.second ?: emptyList()
        val fetched = withContext(Dispatchers.IO) {
            if (mode == "modpack") {
                com.mclauncher.mods.ModpackApi.search(query, gameVersion.ifBlank { null })
            } else {
                ModsApi.searchModrinth(
                    query, gameVersion.ifBlank { null }, loader.ifBlank { null }, cats, sort,
                    target * PAGE_SIZE, PAGE_SIZE
                )
            }
        }
        if (fetched.isEmpty() && target > 0) {
            app.toast("没有更多了")
        } else {
            results = fetched
            page = target
        }
        loadingMore = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(140.dp)) {
                Text(t("MOD 工坊"), color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${results.size} 个结果 · " + t("第") + " ${page + 1} " + t("页"), color = tokens.subtext, fontSize = 11.sp)
            }
            GlassTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = t("搜索 MOD，例如 sodium / 暮色森林"),
                leadingIcon = Icons.Filled.Search,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            GhostButton(t("上一页"), enabled = page > 0 && !loadingMore) {
                app.scope.launch { loadPage(page - 1) }
            }
            Spacer(Modifier.width(6.dp))
            GhostButton(t("下一页"), enabled = results.size >= PAGE_SIZE && !loadingMore) {
                app.scope.launch { loadPage(page + 1) }
            }
            Spacer(Modifier.width(10.dp))
            ViewModeToggle(grid) { grid = it }
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip("MOD", mode == "mod") { mode = "mod" }
            FilterChip(t("整合包"), mode == "modpack") { mode = "modpack" }
            Spacer(Modifier.width(10.dp))
            GhostButton(t("导入 .mrpack"), onClick = { app.importModpackFromFile() })
            Spacer(Modifier.weight(1f))
            if (mode == "mod") {
                FilterChip(t("相关度"), sort == "relevance") { sort = "relevance" }
                FilterChip(t("下载量"), sort == "downloads") { sort = "downloads" }
                FilterChip(t("最新"), sort == "newest") { sort = "newest" }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("版本"), color = tokens.subtext, fontSize = 11.sp)
            FilterChip(t("全部"), gameVersion.isBlank()) { gameVersion = "" }
            active?.let { FilterChip(it.mcVersion, gameVersion == it.mcVersion) { gameVersion = it.mcVersion } }
            app.manifest?.latest?.release?.let { latest ->
                if (latest != active?.mcVersion) FilterChip(latest, gameVersion == latest) { gameVersion = latest }
            }
            Spacer(Modifier.width(8.dp))
            Text(t("加载器"), color = tokens.subtext, fontSize = 11.sp)
            FilterChip(t("全部"), loader.isBlank()) { loader = "" }
            listOf("fabric", "forge", "neoforge", "quilt").forEach { l ->
                FilterChip(l.replaceFirstChar { it.uppercase() }, loader == l) { loader = l }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("类型"), color = tokens.subtext, fontSize = 11.sp)
            MOD_CATEGORIES.take(10).forEach { pair ->
                FilterChip(pair.first, category == pair.first) { category = pair.first }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                loading && results.isEmpty() -> CenterHint("正在搜索…")
                results.isEmpty() -> CenterHint("没有找到匹配的 MOD")
                grid -> Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(240.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                    ) {
                        items(results, key = { it.source + it.id }) { project ->
                            ModGridCard(project) {
                                if (mode == "modpack") app.modpackSheet = project
                                else app.modWizard = com.mclauncher.ui.ModWizardRequest(project, gameVersion, loader)
                            }
                        }
                    }
                    com.mclauncher.ui.components.GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
                }
                else -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp)
                    ) {
                        items(results.size) { index ->
                            ModRow(results[index]) { if (mode == "modpack") packDetail = results[index] else detail = results[index] }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                    com.mclauncher.ui.components.ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }


}

@Composable
private fun CenterHint(text: String) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingDots()
        Spacer(Modifier.height(10.dp))
        Text(text, color = tokens.subtext, fontSize = 12.sp)
    }
}

@Composable
private fun ViewModeToggle(grid: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = LocalTokens.current
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(tokens.field).padding(3.dp)) {
        listOf(false to "列表", true to "网格").forEach { pair ->
            val selected = grid == pair.first
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (selected) tokens.accent.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onChange(pair.first) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(pair.second, color = if (selected) tokens.accent else tokens.subtext, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ModRow(project: ModProject, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val icon = rememberRemoteImage(project.iconUrl)
    GlassPanel(Modifier.fillMaxWidth().height(92.dp), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxSize().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(13.dp)).background(tokens.field),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(project.title.take(1), color = tokens.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.title, color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Tag(if (project.source == ModSource.MODRINTH) "MR" else "CF", tokens.accent2)
                }
                Spacer(Modifier.height(2.dp))
                Text(project.summary, color = tokens.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    project.categories.take(3).forEach { Tag(it, tokens.text.copy(alpha = 0.6f)) }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Download, null, tint = tokens.subtext, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(compactNumber(project.downloads), color = tokens.subtext, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = tokens.subtext, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(compactNumber(project.followers), color = tokens.subtext, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ModGridCard(project: ModProject, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val icon = rememberRemoteImage(project.iconUrl)
    GlassPanel(Modifier.height(178.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().clickable(onClick = onClick).padding(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(tokens.field),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(project.title.take(1), color = tokens.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(project.title, color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(project.summary, color = tokens.subtext, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, null, tint = tokens.subtext, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text(compactNumber(project.downloads), color = tokens.subtext, fontSize = 10.sp)
            }
        }
    }
}

/**
 * 详情：① 选游戏版本 → ② 选这个游戏版本下 MOD 自己的版本 → ③ 选目标版本下载。
 * 内容放在一个 LazyColumn 里，滚动跟手，不会两层滚动条打架。
 */
private fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fk", value / 1_000.0)
    else -> value.toString()
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
            Text("✓", color = tokens.accent, fontSize = 10.sp)
        }
    }
}