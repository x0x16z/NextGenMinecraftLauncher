package com.mclauncher.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.mclauncher.ui.components.PrimaryButton
import com.mclauncher.ui.Avatar

import com.mclauncher.ui.t

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
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
import com.mclauncher.auth.Account
import com.mclauncher.auth.DeviceCodeInfo
import com.mclauncher.auth.Yggdrasil
import com.mclauncher.ui.AppState
import com.mclauncher.ui.components.FilterChip
import com.mclauncher.ui.components.GhostButton
import com.mclauncher.ui.components.GlassPanel
import com.mclauncher.ui.components.GlassTextField
import com.mclauncher.ui.components.Sheet
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.mclauncher.ui.components.Tag
import com.mclauncher.ui.components.rememberSkinFull
import com.mclauncher.ui.components.rememberSkinHead
import com.mclauncher.ui.theme.LocalTokens
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun AccountsScreen(app: AppState) {
    val tokens = LocalTokens.current
    var showAdd by remember { mutableStateOf(false) }
    val current = app.accounts.firstOrNull { it.id == app.activeAccountId } ?: app.accounts.firstOrNull()

    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        // 左侧：账号详情 + 皮肤管理
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("账号"), color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(t("点击右侧账号即可切换并自动登录"), color = tokens.subtext, fontSize = 12.sp)
                }
                GhostButton(t("刷新登录")) { app.refreshActiveAccount() }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(t("添加账号")) { showAdd = true }
            }

            if (current == null) {
                GlassPanel(Modifier.fillMaxWidth().height(260.dp)) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t("还没有账号"), color = tokens.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(t("可以先用离线账号进游戏"), color = tokens.subtext, fontSize = 11.sp)
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(t("添加账号")) { showAdd = true }
                    }
                }
            } else {
                AccountDetail(app, current)
            }
        }

        Spacer(Modifier.width(16.dp))

        // 右侧：竖排账号列表（AltManager 风格）
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("账号列表"), color = tokens.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${app.accounts.size}", color = tokens.subtext, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                com.mclauncher.ui.components.CircleIconButton(
                    androidx.compose.material.icons.Icons.Filled.Add,
                    size = 26.dp,
                    onClick = { showAdd = true }
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(app.accounts.size) { index ->
                    val account = app.accounts[index]
                    AccountListRow(
                        app, account,
                        selected = account.id == app.activeAccountId,
                        onClick = {
                            app.activeAccountId = account.id
                            app.refreshActiveAccount()
                        },
                        onDelete = { app.removeAccount(account.id) }
                    )
                }
            }
        }
    }

    if (showAdd) AddAccountSheet(app) { showAdd = false }
}

@Composable
private fun AccountListRow(
    app: AppState,
    account: Account,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalTokens.current
    val head = rememberSkinHead(account.skinUrl.ifBlank { null })
    GlassPanel(
        Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(14.dp),
        borderColor = if (selected) tokens.accent.copy(alpha = 0.75f) else tokens.border
    ) {
        Row(
            Modifier.fillMaxSize().clickable(onClick = onClick).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(tokens.accent.copy(alpha = 0.5f), tokens.accent2.copy(alpha = 0.4f)))),
                contentAlignment = Alignment.Center
            ) {
                if (head != null) Image(head, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(account.badge, color = tokens.subtext, fontSize = 9.sp, maxLines = 1)
            }
            com.mclauncher.ui.components.CircleIconButton(
                androidx.compose.material.icons.Icons.Filled.Delete,
                size = 26.dp,
                tint = tokens.danger,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun AccountDetail(app: AppState, account: Account) {
    val tokens = LocalTokens.current
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(app, 54.dp, name = account.name, skinUrl = account.skinUrl.ifBlank { null })
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name, color = tokens.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(account.badge, if (account.type == Account.TYPE_MICROSOFT) tokens.success else tokens.accent2)
                        Spacer(Modifier.width(6.dp))
                        Text(account.uuid.take(18), color = tokens.subtext, fontSize = 9.sp)
                    }
                }
                GhostButton(t("更换皮肤")) {
                    val bytes = choosePng()
                    if (bytes != null) app.uploadSkin(account, bytes, account.skinModel == "slim")
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(t("下载皮肤")) { app.downloadSkin(account) }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                t("更换：选一张 64×64 的 PNG 上传；下载：把当前皮肤原图存到本地"),
                color = tokens.subtext, fontSize = 10.sp
            )

            // 离线账号：可以直接挑官方角色外观
            if (account.type == Account.TYPE_OFFLINE && app.skinChoices.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(t("官方角色外观"), color = tokens.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                val skinScroll = androidx.compose.foundation.rememberScrollState()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(skinScroll),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                            app.skinChoices.forEach { entry ->
                                val picked = account.skinUrl.endsWith("offline-${account.id}-${entry.id}.png")
                                Column(
                                    Modifier.width(58.dp).clickable { app.setOfflineSkin(account, entry) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val head = rememberSkinHead(entry.file.toUri().toString())
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                            .background(tokens.field)
                                            .border(
                                                if (picked) 2.dp else 1.dp,
                                                if (picked) tokens.accent else tokens.border,
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (head != null) Image(head, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        entry.label,
                                        color = if (picked) tokens.accent else tokens.subtext,
                                        fontSize = 9.sp, maxLines = 1
                                    )
                                }
                            }
                }
            }

        }
    }
}
@Composable
private fun AddAccountSheet(app: AppState, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    var mode by remember { mutableStateOf("microsoft") }
    var offlineName by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(Yggdrasil.LITTLESKIN) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var device by remember { mutableStateOf<DeviceCodeInfo?>(null) }
    var waiting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Sheet(
        visible = true,
        onDismiss = onDismiss,
        title = "添加账号",
        subtitle = "选择一个登录方式",
        width = 620.dp,
        maxHeight = 640.dp
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(t("微软账号"), mode == "microsoft") { mode = "microsoft" }
                FilterChip(t("离线"), mode == "offline") { mode = "offline" }
                FilterChip(t("外置登录"), mode == "yggdrasil") { mode = "yggdrasil" }
            }
            Spacer(Modifier.height(18.dp))

            when (mode) {
                "microsoft" -> {
                    if (device == null) {
                        Text(t("点击开始后，会在浏览器里打开微软登录页，输入一次代码即可。"), color = tokens.subtext, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        com.mclauncher.ui.components.PrimaryButton(
                            text = if (waiting) "等待授权…" else "开始设备码登录",
                            enabled = !waiting
                        ) {
                            waiting = true
                            app.loginMicrosoft(
                                onCode = { info -> device = info },
                                onDone = { ok ->
                                    waiting = false
                                    if (ok) onDismiss()
                                }
                            )
                        }
                    } else {
                        val info = device!!
                        Text(t("在浏览器输入下面的代码："), color = tokens.subtext, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tokens.field).padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(info.userCode, color = tokens.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GhostButton(t("复制代码")) { copyText(info.userCode) }
                            Spacer(Modifier.width(8.dp))
                            GhostButton(t("打开登录页")) { browser(info.verificationUri) }
                            Spacer(Modifier.width(8.dp))
                            Text(t("正在等待…"), color = tokens.subtext, fontSize = 11.sp)
                        }
                    }
                }

                "offline" -> {
                    Text(t("离线账号只能进入离线服务器和单人游戏，不能进正版服。"), color = tokens.subtext, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    GlassTextField(offlineName, { offlineName = it }, placeholder = t("玩家名（3-16 位字母数字下划线）"), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    com.mclauncher.ui.components.PrimaryButton(t("创建离线账号")) {
                        app.loginOffline(offlineName)
                        if (offlineName.trim().matches(Regex("[A-Za-z0-9_]{3,16}"))) onDismiss()
                    }
                }

                else -> {
                    Text(t("适配 LittleSkin 等 Yggdrasil 外置登录站。"), color = tokens.subtext, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    GlassTextField(server, { server = it }, placeholder = t("认证服务器地址"), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(username, { username = it }, placeholder = t("邮箱 / 用户名"), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassTextField(password, { password = it }, placeholder = t("密码"), password = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(14.dp))
                    com.mclauncher.ui.components.PrimaryButton(
                        text = if (busy) "登录中…" else "登录",
                        enabled = !busy && username.isNotBlank() && password.isNotBlank()
                    ) {
                        busy = true
                        app.loginYggdrasil(server, username, password) { ok ->
                            busy = false
                            if (ok) onDismiss()
                        }
                    }
                }
            }
        }
    }
}

private fun browser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI.create(url)) }
}

private fun choosePng(): ByteArray? {
    val dialog = FileDialog(null as Frame?, "选择皮肤 PNG（64x64）", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".png", ignoreCase = true) }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val file = File(dialog.directory, name)
    return runCatching { file.readBytes() }.getOrNull()
}
/** 复制到系统剪贴板 */
private fun copyText(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}