package com.mclauncher.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.core.formatBytes
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.ListScrollbar
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.components.ProgressBar
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.theme.LocalTokens

/** 下载面板：当前/平均速度、速度曲线、剩余时间、逐项进度。 */
@Composable
fun DownloadPanel(app: AppState, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    val session = app.downloadSession ?: return
    val listState = rememberLazyListState()

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = "下载 · ${session.title}",
        subtitle = if (session.finished) "已完成" else "正在下载 ${session.entries.count { it.status == "下载中" }} 项",
        width = 760.dp,
        maxHeight = 720.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            // 统计卡片
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatBox("当前速度", speedText(session.speed), Modifier.weight(1f))
                StatBox("平均速度", speedText(session.avgSpeed), Modifier.weight(1f))
                StatBox("已下载", "${formatBytes(session.doneBytes)} / ${formatBytes(session.totalBytes)}", Modifier.weight(1.3f))
                StatBox("预计剩余", etaText(session.etaSeconds), Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            ProgressBar(session.ratio, height = 8.dp)
            Spacer(Modifier.height(4.dp))
            Text("${(session.ratio * 100).toInt()}%", color = tokens.subtext, fontSize = 10.sp)

            Spacer(Modifier.height(12.dp))
            // 速度曲线
            GlassPanel(Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(14.dp)) {
                Box(Modifier.fillMaxSize().padding(8.dp)) {
                    SpeedCurve(session.samples, tokens.accent, tokens.accent2)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(t("下载内容"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            GlassPanel(Modifier.fillMaxWidth().height(220.dp), shape = RoundedCornerShape(14.dp)) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 22.dp)
                    ) {
                        items(session.entries.size) { index ->
                            val entry = session.entries[index]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, color = tokens.text, fontSize = 12.sp, maxLines = 1)
                                    Text(
                                        "${entry.source} · ${formatBytes(entry.size)}",
                                        color = tokens.subtext, fontSize = 10.sp
                                    )
                                }
                                Text(
                                    entry.status,
                                    color = when {
                                        entry.status.startsWith("失败") -> tokens.danger
                                        entry.status == "完成" -> tokens.success
                                        else -> tokens.subtext
                                    },
                                    fontSize = 10.sp
                                )
                                if (entry.size > 0 && entry.status == "下载中") {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${(entry.done * 100 / entry.size)}%",
                                        color = tokens.subtext, fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!session.finished) {
                    FilterChip(t("中断下载"), false) { app.cancelTask() }
                    Spacer(Modifier.width(8.dp))
                }
                PrimaryButton(if (session.finished) "关闭" else "后台运行") {
                    onDismiss()
                    if (!session.finished) app.toast("下载已转到后台，点右下角任务卡片可以回来")
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    GlassPanel(modifier.height(64.dp), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, color = tokens.subtext, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun SpeedCurve(samples: List<Float>, accent: Color, accent2: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val data = samples.takeLast(90)
        if (data.size < 2) return@Canvas
        val max = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = size.width / (data.size - 1).coerceAtLeast(1)
        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / max) * size.height * 0.92f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(listOf(accent, accent2)),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )
        // 面积渐变
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = size.height
            )
        )
        drawCircle(accent2, radius = 3f, center = Offset(size.width, size.height - (data.last() / max) * size.height * 0.92f))
    }
}

private fun speedText(bytesPerSec: Double): String = when {
    bytesPerSec <= 0 -> "0 KB/s"
    bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / 1024 / 1024)
    else -> String.format("%.0f KB/s", bytesPerSec / 1024)
}

private fun etaText(seconds: Long): String = when {
    seconds < 0 -> "计算中"
    seconds == 0L -> "完成"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
