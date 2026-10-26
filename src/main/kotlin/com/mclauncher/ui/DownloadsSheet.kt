package com.mclauncher.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.core.formatBytes
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GhostButton
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.ProgressBar
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.theme.LocalTokens

/** 下载中心：网格 / 列表两种视图 + 任务详情 */
@Composable
fun DownloadsSheet(app: AppState, onClose: () -> Unit) {
    val tokens = LocalTokens.current
    var grid by remember { mutableStateOf(true) }
    val detailTask = app.downloadsDetailId?.let { id -> app.downloadTasks.firstOrNull { it.id == id } }

    Sheet(
        visible = true,
        onDismiss = onClose,
        title = if (detailTask != null) "下载详情" else "下载",
        subtitle = if (detailTask != null) detailTask.title
        else "${app.runningDownloads} 项进行中 · 共 ${app.downloadTasks.size} 项",
        width = 880.dp,
        maxHeight = 640.dp,
        fillHeight = true
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            if (detailTask != null) {
                DownloadDetail(app, detailTask) { app.downloadsDetailId = null }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip("网格", grid) { grid = true }
                    Spacer(Modifier.width(6.dp))
                    FilterChip("列表", !grid) { grid = false }
                    Spacer(Modifier.weight(1f))
                    GhostButton("清空已完成") { app.removeFinishedDownloads() }
                }
                Spacer(Modifier.height(12.dp))
                if (app.downloadTasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text("还没有下载任务", color = tokens.subtext, fontSize = 12.sp)
                    }
                } else if (grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(app.downloadTasks, key = { it.id }) { task ->
                            DownloadCard(app, task) { app.downloadsDetailId = task.id }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(app.downloadTasks, key = { it.id }) { task ->
                            DownloadRow(app, task) { app.downloadsDetailId = task.id }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskIcon(task: DownloadTask, size: Int = 40) {
    val tokens = LocalTokens.current
    val a = when (task.kind) {
        "version" -> tokens.accent
        "mod" -> tokens.accent2
        else -> tokens.success
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(a.copy(alpha = 0.85f), a.copy(alpha = 0.35f)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            task.title.take(1).uppercase(),
            color = Color.White, fontSize = (size / 2.4f).sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TaskStatus(task: DownloadTask): String = when (task.status) {
    "done" -> "已完成"
    "failed" -> "失败：" + task.message
    "canceled" -> "已中断"
    else -> task.message.ifBlank { "下载中" }
}

@Composable
private fun DownloadCard(app: AppState, task: DownloadTask, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.field)
            .border(1.dp, tokens.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskIcon(task)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task.subtitle, color = tokens.subtext, fontSize = 9.sp, maxLines = 1)
            }
            if (task.active) {
                GhostButton("中止") { app.cancelDownloadTask(task.id) }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(progress = if (task.status == "done") 1f else task.ratio, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${formatBytes(task.done)} / ${if (task.total > 0) formatBytes(task.total) else "?"}",
                color = tokens.subtext, fontSize = 9.sp
            )
            Spacer(Modifier.weight(1f))
            Text(TaskStatus(task), color = when (task.status) {
                "failed" -> tokens.danger
                "done" -> tokens.success
                "canceled" -> tokens.subtext
                else -> tokens.accent
            }, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DownloadRow(app: AppState, task: DownloadTask, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.field)
            .border(1.dp, tokens.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskIcon(task, 34)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, color = tokens.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            ProgressBar(progress = if (task.status == "done") 1f else task.ratio, modifier = Modifier.fillMaxWidth(0.62f))
            Spacer(Modifier.height(3.dp))
            Text(
                "${formatBytes(task.done)} / ${if (task.total > 0) formatBytes(task.total) else "?"} · ${TaskStatus(task)}",
                color = tokens.subtext, fontSize = 9.sp, maxLines = 1
            )
        }
        if (task.active) GhostButton("中止") { app.cancelDownloadTask(task.id) }
    }
}

@Composable
private fun DownloadDetail(app: AppState, task: DownloadTask, onBack: () -> Unit) {
    val tokens = LocalTokens.current
    val session = task.session
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskIcon(task, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = tokens.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(task.subtitle, color = tokens.subtext, fontSize = 10.sp)
            }
            if (task.active) PrimaryButton("中止下载") { app.cancelDownloadTask(task.id) }
        }
        Spacer(Modifier.height(14.dp))
        ProgressBar(progress = if (task.status == "done") 1f else task.ratio, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        InfoRow("状态", TaskStatus(task))
        InfoRow("大小", "${formatBytes(task.done)} / ${if (task.total > 0) formatBytes(task.total) else "未知"}")
        if (session != null) {
            InfoRow("速度", formatBytes(session.speed.toLong()) + "/s")
            InfoRow("平均速度", formatBytes(session.avgSpeed.toLong()) + "/s")
            InfoRow("剩余时间", if (session.etaSeconds > 0) "${session.etaSeconds}s" else "—")
        }
        if (task.detail.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            task.detail.forEach { (k, v) -> InfoRow(k, v) }
        }
        if (session != null && session.entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("文件", color = tokens.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().height(200.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(session.entries) { entry ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(tokens.field).padding(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = tokens.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.source, color = tokens.subtext, fontSize = 9.sp, maxLines = 1)
                        }
                        Text(
                            entry.status + " · " + formatBytes(entry.done) + "/" + formatBytes(entry.size),
                            color = tokens.subtext, fontSize = 9.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GhostButton("返回列表") { onBack() }
            Spacer(Modifier.width(8.dp))
            GhostButton("关闭") { app.showDownloads = false }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = tokens.subtext, fontSize = 10.sp, modifier = Modifier.width(90.dp))
        Text(value, color = tokens.text, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}