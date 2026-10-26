package com.mclauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.NgmlLogo
import com.mclauncher.ui.components.Sheet
import com.mclauncher.ui.t
import com.mclauncher.ui.theme.LocalTokens

/** 点侧边栏左下角 NGML 弹出来的"关于"窗口 */
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val tokens = LocalTokens.current

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = "NGML",
        subtitle = t("关于这个启动器"),
        width = 560.dp,
        maxHeight = 620.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(tokens.accent.copy(alpha = 0.35f), tokens.accent2.copy(alpha = 0.25f))
                            )
                        )
                        .border(1.dp, tokens.border, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    NgmlLogo(size = 44.dp, accent = tokens.accent, accent2 = tokens.accent2)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("NGML", color = tokens.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Next-Gen Minecraft Launcher",
                        color = tokens.subtext, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t("新一代的 · 简洁的 · 高效的 · 可靠的 Minecraft 启动器"),
                        color = tokens.accent, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            InfoRow(t("名称全称"), "Next-Gen Minecraft Launcher")
            InfoRow(t("制作者"), "_0x16z  ·  DeepSeek-V4.1-Flash-1017")
            InfoRow(t("定位"), t("新一代的简洁的高效的可靠的 Minecraft 启动器"))
            InfoRow(t("版本"), "v1.0.0")
            InfoRow(
                t("技术栈"),
                "Kotlin · Compose Multiplatform · Skia · JNA"
            )

            Spacer(Modifier.height(14.dp))

            Spacer(Modifier.height(12.dp))
            Text(
                t("图标与部分资源版权归 Mojang / 各自模组作者所有，本启动器为非官方项目。"),
                color = tokens.subtext.copy(alpha = 0.6f), fontSize = 9.sp
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = tokens.subtext, fontSize = 11.sp, modifier = Modifier.width(70.dp))
        Text(value, color = tokens.text, fontSize = 12.sp)
    }
}

/** 备用：没有 Sheet 时也能点开（内部用不到，保留给以后复用） */
@Composable
fun AboutEntry(onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("NGML", color = Color.White, fontSize = 12.sp)
        Text(tokens.accent2.toString(), color = Color.Transparent, fontSize = 1.sp)
    }
}
