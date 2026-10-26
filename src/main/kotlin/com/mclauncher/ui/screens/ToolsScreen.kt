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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.core.AppPaths
import com.mclauncher.core.Fs
import com.mclauncher.core.Maintenance
import com.mclauncher.core.NetTools
import com.mclauncher.core.formatBytes
import com.mclauncher.ui.AppState
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GhostButton
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.ListScrollbar
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.Tag
import com.mclauncher.ui.theme.LocalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolsScreen(app: AppState) {
    val tokens = LocalTokens.current
    val listState = rememberLazyListState()
    val root = app.minecraftRoot
    val active = app.activeInstance

    var cacheSize by remember { mutableStateOf(0L) }
    var logSize by remember { mutableStateOf(0L) }
    var ip by remember { mutableStateOf<String?>(null) }
    var portOpen by remember { mutableStateOf(false) }
    var upnp by remember { mutableStateOf<Boolean?>(null) }
    var checkingNetwork by remember { mutableStateOf(false) }
    var diagnoses by remember { mutableStateOf<List<Maintenance.Diagnosis>>(emptyList()) }
    var analyzing by remember { mutableStateOf(false) }
    var lanWorlds by remember { mutableStateOf<List<NetTools.LanWorld>>(emptyList()) }
    var scanningLan by remember { mutableStateOf(false) }

    suspend fun refreshSizes() {
        cacheSize = withContext(Dispatchers.IO) { Maintenance.cacheSize() }
        logSize = withContext(Dispatchers.IO) { Maintenance.logSize() }
    }

    LaunchedEffect(Unit) {
        refreshSizes()
        // 进页面就自动分析一次日志
        analyzing = true
        diagnoses = withContext(Dispatchers.IO) { Maintenance.analyzeLatestLog(root) }
        analyzing = false
        ip = withContext(Dispatchers.IO) { NetTools.localIPv4() }
        portOpen = withContext(Dispatchers.IO) { NetTools.isPortListening(25565) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(start = 22.dp, end = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.padding(top = 6.dp)) {
                    Text(t("工具箱"), color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(t("限速、网络、清理、崩溃分析这些杂事都放这儿"), color = tokens.subtext, fontSize = 12.sp)
                }
            }

            /* 下载限速 */
            item {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(t("下载限速"), color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "同时下载游戏文件时限制带宽，避免把家里网占满（对 MOD/整合包同样生效）",
                            color = tokens.subtext, fontSize = 10.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(
                                "不限速" to 0,
                                "256 KB/s" to 256,
                                "512 KB/s" to 512,
                                "1 MB/s" to 1024,
                                "2 MB/s" to 2048,
                                "5 MB/s" to 5120
                            ).forEach { pair ->
                                FilterChip(pair.first, app.settings.downloadLimitKbps == pair.second) {
                                    app.updateSettings { it.copy(downloadLimitKbps = pair.second) }
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Tag(
                                if (app.settings.downloadLimitKbps == 0) "当前不限速" else "当前 ${app.settings.downloadLimitKbps} KB/s",
                                tokens.accent
                            )
                        }
                    }
                }
            }

            /* 网络诊断 */
            item {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(t("联机与端口"), color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("本机地址"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                            Text(ip ?: "未检测到", color = tokens.text, fontSize = 12.sp)
                            Spacer(Modifier.height(0.dp))
                            if (ip != null) {
                                Spacer(Modifier.width(8.dp))
                                GhostButton(t("复制")) {
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(java.awt.datatransfer.StringSelection("$ip:25565"), null)
                                    app.toast("已复制 $ip:25565")
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t("25565 端口"), color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                            Tag(if (portOpen) "本机已在监听" else "本机没有服务在监听", if (portOpen) tokens.success else tokens.subtext)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (portOpen) "局域网里的朋友可以直接用上面的地址加入" else "开一个存档并「对局域网开放」后再看这里",
                                color = tokens.subtext.copy(alpha = 0.75f), fontSize = 10.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("UPnP", color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                            when (upnp) {
                                null -> Text(t("未检测"), color = tokens.subtext, fontSize = 11.sp)
                                true -> Tag("路由器支持自动端口映射", tokens.success)
                                false -> Tag("没检测到 UPnP 网关", tokens.danger)
                            }
                            Spacer(Modifier.width(8.dp))
                            GhostButton(if (checkingNetwork) "检测中…" else "重新检测") {
                                checkingNetwork = true
                                app.scope.launch {
                                    ip = withContext(Dispatchers.IO) { NetTools.localIPv4() }
                                    portOpen = withContext(Dispatchers.IO) { NetTools.isPortListening(25565) }
                                    upnp = withContext(Dispatchers.IO) { NetTools.upnpAvailable() }
                                    checkingNetwork = false
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("局域网世界", color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                            if (lanWorlds.isEmpty()) {
                                Text("未发现", color = tokens.subtext, fontSize = 11.sp)
                            } else {
                                lanWorlds.take(2).forEach { world ->
                                    Tag("${world.motd} · ${world.address}:${world.port}", tokens.success)
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                            GhostButton(if (scanningLan) "扫描中…" else "扫描") {
                                scanningLan = true
                                app.scope.launch {
                                    lanWorlds = withContext(Dispatchers.IO) { NetTools.discoverLanWorlds(1800) }
                                    scanningLan = false
                                    if (lanWorlds.isEmpty()) app.toast("没听到局域网广播，确认已经「对局域网开放」")
                                }
                            }
                            if (lanWorlds.isNotEmpty()) {
                                GhostButton("复制地址") {
                                    val world = lanWorlds.first()
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(java.awt.datatransfer.StringSelection("${world.address}:${world.port}"), null)
                                    app.toast("已复制 ${world.address}:${world.port}")
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "内网穿透（frp / sakura frp 之类）需要在外部服务上建隧道，启动器这边能确认的是：本机端口是否开着、路由器支不支持 UPnP。",
                            color = tokens.subtext.copy(alpha = 0.7f), fontSize = 10.sp
                        )
                    }
                }
            }

            /* 清理 */
            item {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t("缓存清理"), color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "缓存 ${formatBytes(cacheSize)} · 日志 ${formatBytes(logSize)}",
                                color = tokens.subtext, fontSize = 11.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(t("会清掉版本元数据缓存和日志，不会动游戏文件和存档"), color = tokens.subtext.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                        GhostButton(t("刷新")) { app.scope.launch { refreshSizes() } }
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(t("清理")) {
                            app.scope.launch {
                                val freed = withContext(Dispatchers.IO) { Maintenance.clearCaches() }
                                refreshSizes()
                                app.toast("已清理 ${formatBytes(freed)}")
                            }
                        }
                    }
                }
            }

            /* 崩溃分析 */
            item {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t("崩溃日志分析"), color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(t("扫一遍最新日志，把常见的报错翻译成人话"), color = tokens.subtext, fontSize = 10.sp)
                            }
                            GhostButton(if (analyzing) "分析中…" else "分析") {
                                analyzing = true
                                app.scope.launch {
                                    diagnoses = withContext(Dispatchers.IO) { Maintenance.analyzeLatestLog(root) }
                                    analyzing = false
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            GhostButton(t("打开日志目录")) { Fs.openFolder(root.resolve("logs")) }
                        }
                        if (diagnoses.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            diagnoses.forEach { d ->
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                    Tag(
                                        when (d.severity) {
                                            "error" -> "错误"
                                            "warn" -> "警告"
                                            "ok" -> "正常"
                                            else -> "信息"
                                        },
                                        when (d.severity) {
                                            "error" -> tokens.danger
                                            "warn" -> tokens.accent
                                            "ok" -> tokens.success
                                            else -> tokens.subtext
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(d.title, color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(d.detail, color = tokens.subtext, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* 目录快开 */
            item {
                GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(t("快速打开目录"), color = tokens.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        val dirs = buildList {
                            if (active != null) {
                                add("当前版本 mods" to active.gameDirPath().resolve("mods"))
                                add("当前版本 saves" to active.gameDirPath().resolve("saves"))
                                add("当前版本截图" to active.gameDirPath().resolve("screenshots"))
                                add("当前版本日志" to active.gameDirPath().resolve("logs"))
                            }
                            add("versions" to root.resolve("versions"))
                            add("assets" to root.resolve("assets"))
                            add("启动器目录" to AppPaths.root)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            dirs.take(4).forEach { pair ->
                                GhostButton(pair.first) { Fs.openFolder(pair.second) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            dirs.drop(4).forEach { pair ->
                                GhostButton(pair.first) { Fs.openFolder(pair.second) }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
        ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
    }
}
