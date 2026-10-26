package com.mclauncher.ui.screens

import com.mclauncher.ui.t

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.core.formatBytes
import com.mclauncher.mods.ModProject
import com.mclauncher.mods.ModVersionInfo
import com.mclauncher.mods.ModpackApi
import com.mclauncher.ui.AppState
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.ListScrollbar
import com.mclauncher.ui.components.LoadingDots
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.components.Tag
import com.mclauncher.ui.theme.LocalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 整合包安装：选一个 .mrpack 版本 → 下整合包 → 自动建版本、下所有文件、覆盖 overrides。
 */
@Composable
fun ModpackSheet(app: AppState, project: ModProject, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    var versions by remember { mutableStateOf<List<ModVersionInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<ModVersionInfo?>(null) }
    var installing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(project.id) {
        loading = true
        versions = withContext(Dispatchers.IO) { ModpackApi.versions(project.id) }
        selected = versions.firstOrNull()
        loading = false
    }

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = project.title,
        subtitle = "整合包 · ${project.author} · ${project.downloads} 次下载",
        width = 780.dp,
        maxHeight = 700.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text(project.summary, color = tokens.text.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                project.categories.take(4).forEach { Tag(it, tokens.accent2) }
            }

            Spacer(Modifier.height(12.dp))
            Text(t("选择整合包版本"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            GlassPanel(Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(14.dp)) {
                when {
                    loading -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingDots()
                        Spacer(Modifier.height(8.dp))
                        Text(t("正在获取整合包版本…"), color = tokens.subtext, fontSize = 11.sp)
                    }
                    versions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(t("这个整合包暂时没有可下载的 .mrpack"), color = tokens.subtext, fontSize = 12.sp)
                    }
                    else -> Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 20.dp)
                        ) {
                            items(versions.size) { index ->
                                val version = versions[index]
                                val chosen = selected?.id == version.id
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (chosen) tokens.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(version.versionNumber, color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            version.gameVersions.take(3).joinToString(" ") +
                                                version.loaders.take(2).joinToString(" ").let { if (it.isBlank()) "" else " · $it" },
                                            color = tokens.subtext, fontSize = 10.sp, maxLines = 1
                                        )
                                    }
                                    Text(
                                        formatBytes(version.file?.size ?: 0),
                                        color = tokens.subtext, fontSize = 10.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    PrimaryButton(
                                        text = if (chosen) "选中" else "选择",
                                        height = 30.dp,
                                        onClick = { selected = version }
                                    )
                                }
                            }
                        }
                        ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "安装会自动创建版本、装对应加载器、下载全部 MOD 和配置文件",
                    color = tokens.subtext, fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = if (installing) "正在安装…" else "安装整合包",
                    enabled = selected != null && !installing,
                    onClick = {
                        val version = selected ?: return@PrimaryButton
                        installing = true
                        app.installModpack(project, version) { instance ->
                            installing = false
                            if (instance != null) onDismiss()
                        }
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
