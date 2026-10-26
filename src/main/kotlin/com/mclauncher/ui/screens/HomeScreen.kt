package com.mclauncher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mclauncher.ui.AppState
import com.mclauncher.ui.Screen
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.rememberSkinHead
import com.mclauncher.ui.t
import com.mclauncher.ui.theme.LocalTokens

/**
 * 主页只放最核心的东西：头像 → 名字 → 启动按钮 → 版本选择 → 设置入口。
 * 居中一列，不做花哨卡片。
 */
@Composable
fun HomeScreen(app: AppState) {
    val tokens = LocalTokens.current
    val account = app.activeAccount
    val instance = app.activeInstance
    val head = rememberSkinHead(account?.skinUrl?.ifBlank { null })
    var showVersions by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 620.dp).padding(24.dp)
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(tokens.accent.copy(alpha = 0.55f), tokens.accent2.copy(alpha = 0.45f))
                        )
                    )
                    .border(1.dp, tokens.border, RoundedCornerShape(28.dp))
                    .clickable { app.screen = Screen.ACCOUNTS },
                contentAlignment = Alignment.Center
            ) {
                if (head != null) {
                    Image(head, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Text(
                        (account?.name ?: "?").take(1).uppercase(),
                        color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                account?.name ?: t("未登录"),
                color = tokens.text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                account?.badge ?: t("点击头像登录"),
                color = tokens.subtext,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(26.dp))

            val running = instance != null && app.runningPids.containsKey(instance.id)
            Box(
                Modifier
                    .width(260.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            if (instance == null) listOf(tokens.field, tokens.field)
                            else listOf(tokens.accent, tokens.accent2)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable {
                        if (instance == null) {
                            app.screen = Screen.INSTANCES
                            app.openCreateInstance = true
                        } else {
                            app.launch(instance)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        instance == null -> t("创建第一个版本")
                        running -> t("再次启动")
                        else -> t("启动游戏")
                    },
                    color = if (instance == null) tokens.subtext else Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(18.dp))

            GlassPanel(Modifier.width(360.dp), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showVersions = !showVersions },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("游戏版本"), color = tokens.subtext, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            instance?.name ?: t("还没有版本"),
                            color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    if (showVersions && app.instances.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        app.instances.take(6).forEach { version ->
                            val selected = version.id == instance?.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selected) tokens.accent.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable { app.activeInstanceId = version.id; showVersions = false }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    version.name,
                                    color = if (selected) tokens.accent else tokens.text,
                                    fontSize = 11.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Text(version.mcVersion, color = tokens.subtext, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { app.screen = Screen.SETTINGS }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Settings, null, tint = tokens.subtext, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("设置"), color = tokens.subtext, fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Text("→", color = tokens.subtext, fontSize = 11.sp)
            }
        }
    }
}
