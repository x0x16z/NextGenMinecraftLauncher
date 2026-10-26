package com.mclauncher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mclauncher.auth.Account
import com.mclauncher.auth.AccountStore
import com.mclauncher.auth.DeviceCodeInfo
import com.mclauncher.auth.MicrosoftAuth
import com.mclauncher.auth.Yggdrasil
import com.mclauncher.core.AppLog
import com.mclauncher.core.AppPaths
import com.mclauncher.core.Fs
import com.mclauncher.core.Mirror
import com.mclauncher.core.Settings
import com.mclauncher.core.SettingsStore
import com.mclauncher.core.Win32
import com.mclauncher.install.Installer
import com.mclauncher.instance.Instance
import com.mclauncher.instance.InstanceStore
import com.mclauncher.java.JavaRuntime
import com.mclauncher.launch.LaunchOptions
import com.mclauncher.launch.Launcher
import com.mclauncher.meta.Loaders
import com.mclauncher.meta.Mojang
import com.mclauncher.meta.VersionManifest
import com.mclauncher.mods.ModProject
import com.mclauncher.mods.ModVersionInfo
import com.mclauncher.mods.ModsApi
import com.mclauncher.ui.theme.AC_PALETTES
import com.mclauncher.ui.theme.AccentPalette
import com.mclauncher.ui.theme.paletteOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class Screen(val label: String) {
    HOME("首页"),
    INSTANCES("游戏版本"),
    MODS("MOD 工坊"),
    ACCOUNTS("账号"),
    TOOLS("工具箱"),
    SETTINGS("设置")
}

/** 下载中心里的一条任务（版本 / MOD / 整合包） */
data class DownloadTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val kind: String,                 // version | mod | modpack
    val title: String,
    val subtitle: String = "",
    val iconUrl: String? = null,
    var total: Long = 0,
    var done: Long = 0,
    var status: String = "running",   // running | done | failed | canceled
    var message: String = "",
    var detail: List<Pair<String, String>> = emptyList(),
    var session: DownloadSession? = null,
    val startedAt: Long = System.currentTimeMillis(),
    var finishedAt: Long = 0
) {
    val ratio: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val active: Boolean get() = status == "running"
}

data class PendingDelete(
    val title: String,
    val subtitle: String,
    val onConfirm: () -> Unit
)

data class TaskState(
    val title: String,
    val detail: String = "",
    val progress: Float = -1f,
    val running: Boolean = true
)

/**
 * 全局状态与业务动作。UI 只读这里的状态、调用这里的方法。
 */
class AppState {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var settings by mutableStateOf(SettingsStore.load())
        private set
    var instances by mutableStateOf(InstanceStore.load())
        private set
    var accounts by mutableStateOf(AccountStore.load())
        private set

    var activeInstanceId by mutableStateOf("")
    var activeAccountId by mutableStateOf("")
    var screen by mutableStateOf(Screen.HOME)
    var task by mutableStateOf<TaskState?>(null)
    var cancelRequested by mutableStateOf(false)
    var downloadSession by mutableStateOf<DownloadSession?>(null)
    var showDownloadPanel by mutableStateOf(false)

    /* ------------------------------ 下载中心 ------------------------------ */
    var downloadTasks by mutableStateOf<List<DownloadTask>>(emptyList())
        private set
    var showDownloads by mutableStateOf(false)
    var downloadsDetailId by mutableStateOf<String?>(null)
    /** 每加一条任务就 +1，侧边栏据此播一下闪烁动画 */
    var downloadsFlash by mutableStateOf(0)
    /** 右下角失败弹窗 */
    var errorToast by mutableStateOf<String?>(null)

    val runningDownloads: Int get() = downloadTasks.count { it.active }

    fun addDownloadTask(
        kind: String,
        title: String,
        subtitle: String = "",
        iconUrl: String? = null,
        total: Long = 0
    ): String {
        val task = DownloadTask(
            kind = kind, title = title, subtitle = subtitle, iconUrl = iconUrl, total = total
        )
        downloadTasks = downloadTasks + task
        downloadsFlash++
        return task.id
    }

    fun patchDownloadTask(id: String, block: (DownloadTask) -> Unit) {
        downloadTasks = downloadTasks.map {
            if (it.id == id) it.apply(block) else it
        }
    }

    fun finishDownloadTask(id: String, status: String, message: String = "") {
        patchDownloadTask(id) {
            it.status = status
            it.message = message
            it.finishedAt = System.currentTimeMillis()
            if (status == "done") { it.done = it.total }
        }
        downloadsFlash++
        if (status == "failed") errorToast = message.ifBlank { "下载失败" }
    }

    private val canceledTasks = java.util.Collections.synchronizedSet(HashSet<String>())

    fun cancelDownloadTask(id: String) {
        canceledTasks.add(id)
        patchDownloadTask(id) {
            if (it.active) { it.status = "canceled"; it.message = "已中断"; it.finishedAt = System.currentTimeMillis() }
        }
        downloadsFlash++
    }

    fun isTaskCanceled(id: String): Boolean = id in canceledTasks

    fun removeFinishedDownloads() {
        downloadTasks = downloadTasks.filter { it.active }
    }
    /** MOD 下载向导（在根布局渲染，保证遮住整个窗口） */
    var modWizard: ModWizardRequest? by mutableStateOf(null)
    var modpackSheet: com.mclauncher.mods.ModProject? by mutableStateOf(null)

    /** 渐变编辑/删除弹窗（放根布局渲染，遮罩才能铺满整窗） */
    var editingGradient: com.mclauncher.core.GradientPreset? by mutableStateOf(null)
    var deletingGradient: com.mclauncher.core.GradientPreset? by mutableStateOf(null)

    /** 内容管理（版本资源）弹窗，放根布局渲染 */
    var contentOf: Instance? by mutableStateOf(null)
    var pendingDelete by mutableStateOf<PendingDelete?>(null)

    fun confirmDelete(title: String, subtitle: String, onConfirm: () -> Unit) {
        pendingDelete = PendingDelete(title, subtitle, onConfirm)
    }

    fun performPendingDelete() {
        val task = pendingDelete ?: return
        pendingDelete = null
        runCatching { task.onConfirm() }.onFailure { toast("删除失败：${it.message}") }
    }

    /** 关于 NGML 的二级窗口 */
    var showAbout by mutableStateOf(false)

    /** 千万别点 —— 小强彩蛋 */
    var showRoach by mutableStateOf(false)
    var strings by mutableStateOf(com.mclauncher.core.Lang.load(settings.language))
    var onHideWindow: (() -> Unit)? = null
    var onShowWindow: (() -> Unit)? = null
    var message by mutableStateOf<String?>(null)
    var logs by mutableStateOf<List<String>>(emptyList())
    var manifest by mutableStateOf<VersionManifest?>(null)
    var runningPids by mutableStateOf<Map<String, Long>>(emptyMap())
    var systemDark by mutableStateOf(Win32.isSystemDark())
    var openCreateInstance by mutableStateOf(false)

    val activeInstance: Instance?
        get() = instances.firstOrNull { it.id == activeInstanceId } ?: instances.firstOrNull()

    val activeAccount: Account?
        get() = accounts.firstOrNull { it.id == activeAccountId } ?: accounts.firstOrNull()

    val isDark: Boolean
        get() = when (settings.mode) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }

    val palette: AccentPalette get() = paletteOf(settings.palette)

    val minecraftRoot: Path get() = Paths.get(settings.minecraftRoot)

    private var saveJob: Job? = null
    private val logLock = Any()

    init {
        instances = mergeDiskVersions(instances).let { merged ->
            if (merged.size != instances.size) {
                runCatching { InstanceStore.save(merged) }
            }
            merged
        }
        activeInstanceId = instances.firstOrNull { it.id == settings.lastInstance }?.id
            ?: instances.firstOrNull()?.id ?: ""
        activeAccountId = accounts.firstOrNull()?.id ?: ""
        Mirror.mode = settings.mirror
        com.mclauncher.core.Bandwidth.setLimitKbps(settings.downloadLimitKbps)

        AppLog.addListener { line -> pushLog(line) }
        scope.launch {
            runCatching { manifest = Mojang.manifest() }
        }
        scope.launch {
            while (true) {
                delay(3000)
                val dark = Win32.isSystemDark()
                if (dark != systemDark) systemDark = dark
            }
        }
    }

    /* ------------------------------ 基础 ------------------------------ */

    fun updateSettings(block: (Settings) -> Settings) {
        val next = block(settings)
        // 现代模式依赖系统原生亚克力，系统不支持时拒绝切换
        settings = if (next.backdropMode == Settings.BACKDROP_LIVE &&
            !com.mclauncher.core.Win32.supportsNativeAcrylic()
        ) {
            toast("原生亚克力需要 Windows 11 22H2 及以上，无法切换到现代模式")
            next.copy(backdropMode = settings.backdropMode)
        } else next
        Mirror.mode = settings.mirror
        com.mclauncher.core.Bandwidth.setLimitKbps(settings.downloadLimitKbps)
        com.mclauncher.core.Bandwidth.setLimitKbps(settings.downloadLimitKbps)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(450)
            SettingsStore.save(settings)
        }
    }

    fun toast(text: String) {
        message = text
        scope.launch {
            delay(2800)
            if (message == text) message = null
        }
    }

    fun setLanguage(code: String) {
        updateSettings { it.copy(language = code) }
        strings = com.mclauncher.core.Lang.reload(code)
    }

    /** 把当前账号正在用的皮肤 PNG 存到本地 */
    fun downloadSkin(account: Account) {
        scope.launch {
            val url = account.skinUrl.ifBlank { null }
            if (url == null) {
                toast("这个账号没有可下载的皮肤（离线账号默认皮肤）")
                return@launch
            }
            val bytes = com.mclauncher.ui.components.Skins.fetchBytes(url)
            if (bytes == null || bytes.isEmpty()) {
                toast("皮肤下载失败，检查网络后重试")
                return@launch
            }
            val target = com.mclauncher.ui.components.saveSkinFile("${account.name}-skin.png")
            if (target == null) return@launch
            runCatching { target.writeBytes(bytes) }
                .onSuccess { toast("皮肤已保存：${target.name}") }
                .onFailure { toast("保存失败：${it.message}") }
        }
    }

    fun reloadLanguage() {
        strings = com.mclauncher.core.Lang.reload(settings.language)
        toast("语言已重新加载")
    }

    fun saveInstances() = InstanceStore.save(instances)

    /** 离线账号可选的官方皮肤（启动后从客户端 jar 抽取一次） */
    var skinChoices by mutableStateOf<List<com.mclauncher.core.SkinDefaults.Entry>>(emptyList())
        private set

    init {
        scope.launch {
            skinChoices = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.mclauncher.core.SkinDefaults.ensure()
            }
        }
    }

    /**
     * 给离线账号挑一个官方外观：把抽出来的皮肤复制成该账号专属副本，
     * skinUrl 用 file:// 指过去，头像/预览都会跟着变。
     */
    fun setOfflineSkin(account: Account, entry: com.mclauncher.core.SkinDefaults.Entry) {
        runCatching {
            val dir = com.mclauncher.core.AppPaths.root.resolve("skins")
            com.mclauncher.core.Fs.ensureDir(dir)
            val target = dir.resolve("offline-${account.id}-${entry.id}.png")
            java.nio.file.Files.copy(
                entry.file, target,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            account.skinUrl = target.toUri().toString()
            account.skinModel = if (entry.slim) "slim" else "classic"
            saveAccounts()
            accounts = accounts.toList()   // 触发重组
            toast("已切换到 ${entry.label}")
        }.onFailure { toast("切换失败：${it.message}") }
    }

    fun saveAccounts() = AccountStore.save(accounts)

    fun updateInstance(instance: Instance) {
        instances = instances.map { if (it.id == instance.id) instance else it }
        saveInstances()
    }

    /** 把 .minecraft/versions 下手动加进来的版本也纳入管理 */
    private fun mergeDiskVersions(base: List<Instance>): List<Instance> {
        val found = runCatching { InstanceStore.scanDiskVersions(minecraftRoot, base) }.getOrDefault(emptyList())
        if (found.isEmpty()) return base
        found.forEach { AppLog.log("发现本地版本：${it.name}（${it.displayVersion}）") }
        return base + found
    }

    /** 从磁盘重新读实例列表（顺手清掉目录已经不存在的僵尸条目） */
    fun reloadInstances() {
        scope.launch {
            val disk = withContext(Dispatchers.IO) { InstanceStore.load() }
            val alive = withContext(Dispatchers.IO) {
                val existing = disk.filter { it.gameDir.isBlank() || java.nio.file.Files.exists(it.gameDirPath()) }
                // 同一个文件夹只能对应一个实例，防止历史遗留的重复条目
                val seen = HashSet<String>()
                existing.filter { instance ->
                    val key = instance.gameDirPath().toAbsolutePath().normalize().toString().lowercase()
                    seen.add(key)
                }
            }
            val merged = mergeDiskVersions(alive)
            if (merged != disk) {
                InstanceStore.save(merged)
            }
            if (merged.map { it.id } != instances.map { it.id } || merged != instances) {
                instances = merged
            }
            if (instances.none { it.id == activeInstanceId }) {
                activeInstanceId = instances.firstOrNull()?.id ?: ""
            }
        }
    }

    fun removeInstance(id: String) {
        instances = instances.filterNot { it.id == id }
        if (activeInstanceId == id) activeInstanceId = instances.firstOrNull()?.id ?: ""
        saveInstances()
    }

    private fun pushLog(line: String) {
        synchronized(logLock) {
            logs = (logs + line).takeLast(500)
        }
    }

    /* ------------------------------ 账号 ------------------------------ */

    fun addAccount(account: Account) {
        val existing = accounts.indexOfFirst { it.uuid.isNotBlank() && it.uuid == account.uuid && it.type == account.type }
        accounts = if (existing >= 0) {
            accounts.toMutableList().also { it[existing] = account.copy(id = accounts[existing].id) }
        } else accounts + account
        activeAccountId = accounts.first { it.uuid == account.uuid && it.type == account.type }.id
        saveAccounts()
    }

    fun removeAccount(id: String) {
        accounts = accounts.filterNot { it.id == id }
        if (activeAccountId == id) activeAccountId = accounts.firstOrNull()?.id ?: ""
        saveAccounts()
    }

    fun loginOffline(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            toast("请输入玩家名")
            return
        }
        if (!trimmed.matches(Regex("[A-Za-z0-9_]{3,16}"))) {
            toast("离线用户名需为 3-16 位字母、数字或下划线")
            return
        }
        addAccount(Account.offline(trimmed))
        toast("已添加离线账号 $trimmed")
    }

    fun loginMicrosoft(onCode: (DeviceCodeInfo) -> Unit, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            // 内置公共客户端 ID：用户不需要自己注册 Azure 应用
            val clientId = Settings.MSA_CLIENT_ID
            cancelRequested = false
            try {
                task = TaskState("等待微软登录", "浏览器中完成授权（可点右下角 × 取消）")
                val info = MicrosoftAuth.requestDeviceCode(clientId)
                onCode(info)
                val token = MicrosoftAuth.pollToken(clientId, info, isCancelled = { cancelRequested })
                val account = MicrosoftAuth.loginWithXbox(token)
                addAccount(account)
                toast("欢迎回来，${account.name}")
                onDone(true)
            } catch (t: kotlinx.coroutines.CancellationException) {
                toast("已取消登录")
                onDone(false)
            } catch (t: Throwable) {
                AppLog.error("微软登录失败", t)
                toast(t.message ?: "微软登录失败")
                onDone(false)
            } finally {
                task = null
                cancelRequested = false
            }
        }
    }

    fun loginYggdrasil(server: String, username: String, password: String, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            try {
                task = TaskState("登录外置账号", server)
                val session = Yggdrasil.login(server, username, password)
                val skin = Yggdrasil.skinUrl(server, session.uuid)
                val account = Account(
                    type = Account.TYPE_YGGDRASIL,
                    name = session.name,
                    uuid = session.uuid,
                    accessToken = session.accessToken,
                    refreshToken = session.clientToken,
                    server = server,
                    skinUrl = skin?.first ?: "",
                    skinModel = skin?.second ?: "classic"
                )
                addAccount(account)
                toast("已登录 ${session.name}")
                onDone(true)
            } catch (t: Throwable) {
                toast(t.message ?: "外置登录失败")
                onDone(false)
            } finally {
                task = null
            }
        }
    }

    fun refreshActiveAccount() {
        val account = activeAccount ?: return
        scope.launch {
            try {
                when (account.type) {
                    Account.TYPE_MICROSOFT -> {
                        val renewed = MicrosoftAuth.ensureValid(account, Settings.MSA_CLIENT_ID)
                        addAccount(renewed)
                        toast("登录状态正常")
                    }
                    Account.TYPE_YGGDRASIL -> {
                        val session = Yggdrasil.refresh(account.server, account.accessToken, account.refreshToken)
                        addAccount(account.copy(accessToken = session.accessToken, name = session.name))
                        toast("登录状态正常")
                    }
                    else -> toast("离线账号无需刷新")
                }
            } catch (t: Throwable) {
                toast(t.message ?: "刷新失败")
            }
        }
    }

    fun uploadSkin(account: Account, png: ByteArray, slim: Boolean) {
        scope.launch {
            task = TaskState("上传皮肤")
            try {
                when (account.type) {
                    Account.TYPE_MICROSOFT -> {
                        val boundary = "----MLB" + System.nanoTime()
                        val body = Yggdrasil.buildMultipart(
                            boundary,
                            mapOf("variant" to if (slim) "slim" else "classic"),
                            "file", "skin.png", "image/png", png
                        )
                        val response = com.mclauncher.core.Http.put(
                            "https://api.minecraftservices.com/minecraft/profile/skins",
                            body,
                            "multipart/form-data; boundary=$boundary",
                            mapOf("Authorization" to "Bearer ${account.accessToken}")
                        )
                        if (response.statusCode() !in 200..299) error("上传失败 HTTP ${response.statusCode()}")
                        val profile = MicrosoftAuth.fetchProfile(account.accessToken)
                        val skin = (profile["skins"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()
                            ?.let { it as kotlinx.serialization.json.JsonObject }
                        val url = (skin?.get("url") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        addAccount(account.copy(skinUrl = url ?: account.skinUrl, skinModel = if (slim) "slim" else "classic"))
                    }
                    Account.TYPE_YGGDRASIL -> {
                        Yggdrasil.uploadSkin(account.server, account.accessToken, png, slim).getOrThrow()
                        val skin = Yggdrasil.skinUrl(account.server, account.uuid)
                        addAccount(account.copy(skinUrl = skin?.first ?: account.skinUrl, skinModel = skin?.second ?: "classic"))
                    }
                    else -> error("离线账号不能上传皮肤")
                }
                toast("皮肤已更新")
            } catch (t: Throwable) {
                toast(t.message ?: "皮肤上传失败")
            } finally {
                task = null
            }
        }
    }

    /* ------------------------------ 实例 ------------------------------ */

    fun createInstance(
        name: String,
        mcVersion: String,
        loader: String,
        loaderVersion: String,
        memoryMb: Int,
        optifinePick: String = "",
        onStage: (String, Float) -> Unit = { _, _ -> },
        onDone: (Instance?) -> Unit = {}
    ) {
        scope.launch {
            var dlId = ""
            var downloadFailed: String? = null
            try {
                cancelRequested = false
                task = TaskState("创建版本", "准备目录")
                val root = minecraftRoot
                Fs.ensureDir(root)
                val displayName = name.ifBlank { mcVersion }
                if (instances.any { it.name.equals(displayName, ignoreCase = true) }) {
                    toast("已有同名版本「$displayName」，换个名字吧")
                    onDone(null)
                    return@launch
                }

                dlId = addDownloadTask("version", displayName, "游戏版本 · $mcVersion", total = 0)

                var versionId = mcVersion
                when (loader) {
                    "fabric" -> {
                        task = TaskState("准备 Fabric", loaderVersion)
                        versionId = Loaders.installFabric(mcVersion, loaderVersion, root)
                    }
                    "forge", "forge_optifine" -> {
                        task = TaskState("安装 Forge", "需要 Java 17+")
                        val java = JavaRuntime.ensure(17) { onStage(it, -1f) }
                        versionId = Loaders.installForge(java.exe, mcVersion, loaderVersion, root) { line ->
                            onStage(line, -1f)
                        }
                        if (loader == "forge_optifine") {
                            task = TaskState("安装 OptiFine", mcVersion)
                            onStage("下载 OptiFine", -1f)
                            runCatching {
                                val parts = optifinePick.split("/")
                                val ofType = parts.getOrNull(0) ?: ""
                                val ofPatch = parts.getOrNull(1) ?: ""
                                Loaders.installOptifine(
                                    Fs.ensureDir(root.resolve("versions").resolve(versionId)),
                                    mcVersion, ofType, ofPatch
                                ) { line -> onStage(line, -1f) }
                            }.onFailure { AppLog.error("OptiFine 安装失败", it) }
                        }
                    }
                    "neoforge" -> {
                        task = TaskState("安装 NeoForge", "需要 Java 21")
                        val java = JavaRuntime.ensure(21) { onStage(it, -1f) }
                        versionId = Loaders.installNeoForge(java.exe, mcVersion, loaderVersion, root) { line ->
                            onStage(line, -1f)
                        }
                    }
                }

                // 版本完全隔离：游戏目录就是 versions/<versionId>，mods/资源包/光影都放里面
                val gameDir = Fs.ensureDir(root.resolve("versions").resolve(versionId))
                InstanceStore.ensureGameDirs(gameDir)
                // 合并成单版本后，父版本目录没人用就删掉，做到一个版本一个文件夹
                runCatching {
                    val keep = instances.map { it.versionId.ifBlank { it.mcVersion } }.toSet() +
                        versionId
                    com.mclauncher.instance.InstanceStore.deleteUnusedBaseDir(root, mcVersion, keep)
                }.onFailure { AppLog.error("清理父版本目录失败", it) }
                // 版本文件也放进来：把客户端 jar 拷一份到版本目录，和 json 同处
                runCatching {
                    val baseJar = root.resolve("versions").resolve(mcVersion).resolve("$mcVersion.jar")
                    val ownJar = gameDir.resolve("$versionId.jar")
                    if (java.nio.file.Files.exists(baseJar) && !java.nio.file.Files.exists(ownJar)) {
                        java.nio.file.Files.copy(baseJar, ownJar)
                    }
                }.onFailure { AppLog.error("复制客户端 jar 失败", it) }

                task = TaskState("下载游戏文件", versionId)
                val installer = Installer(root, settings.threads) { cancelRequested }
                val installed = installer.install(versionId) { stage ->
                    task = TaskState(stage.title, stage.progress?.message ?: "", stage.progress?.byteRatio ?: -1f)
                    onStage(stage.title, stage.progress?.byteRatio ?: -1f)
                    patchDownloadTask(dlId) {
                        it.message = stage.title
                        stage.progress?.let { pr ->
                            if (pr.totalBytes > 0) it.total = pr.totalBytes
                            if (pr.bytes > 0) it.done = pr.bytes
                        }
                    }
                }

                val instance = Instance(
                    name = displayName,
                    mcVersion = mcVersion,
                    loader = loader,
                    loaderVersion = loaderVersion,
                    versionId = versionId,
                    gameDir = gameDir.toString(),
                    memoryMb = memoryMb,
                    javaMajor = installed.resolved.javaVersion.majorVersion
                )
                instances = instances + instance
                activeInstanceId = instance.id
                saveInstances()
                toast("实例 ${instance.name} 已就绪")
                onDone(instance)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException || t is java.util.concurrent.CancellationException) {
                    toast("已中断下载")
                } else {
                    AppLog.error("创建版本失败", t)
                    toast(t.message ?: "创建版本失败")
                    downloadFailed = t.message ?: "创建版本失败"
                }
                onDone(null)
            } finally {
                val failed = downloadFailed
                if (failed != null) {
                    finishDownloadTask(dlId, "failed", failed)
                } else if (cancelRequested) {
                    finishDownloadTask(dlId, "canceled", "已中断")
                } else {
                    finishDownloadTask(dlId, "done", "已完成")
                }
                task = null
                cancelRequested = false
            }
        }
    }

    fun launch(instance: Instance, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                cancelRequested = false
                task = TaskState("准备启动", "检查游戏文件")
                val root = minecraftRoot
                val installer = Installer(root, settings.threads) { cancelRequested }
                val versionId = instance.versionId.ifBlank { instance.mcVersion }
                if (!installer.isReady(versionId)) {
                    installer.install(versionId) { stage ->
                        task = TaskState(stage.title, stage.progress?.message ?: "", stage.progress?.byteRatio ?: -1f)
                    }
                }
                val resolved = installer.resolve(versionId)

                var account = activeAccount ?: Account.offline("Player")
                if (account.type == Account.TYPE_MICROSOFT) {
                    account = MicrosoftAuth.ensureValid(account, Settings.MSA_CLIENT_ID)
                }

                val major = if (instance.javaMajor > 0) instance.javaMajor else resolved.javaVersion.majorVersion
                task = TaskState("准备 Java $major", "检测本地运行时")
                val javaExe = if (instance.javaPath.isNotBlank()) {
                    Paths.get(instance.javaPath)
                } else {
                    JavaRuntime.ensure(major) { msg -> task = TaskState(msg) }.exe
                }

                task = TaskState("生成启动参数")
                val plan = Launcher.build(
                    java = javaExe,
                    resolved = resolved,
                    minecraftRoot = root,
                    options = LaunchOptions(
                        versionId = versionId,
                        gameDir = instance.gameDirPath(),
                        account = account,
                        memoryMb = instance.memoryMb,
                        width = instance.width,
                        height = instance.height,
                        jvmExtra = instance.jvmArgs,
                        javaMajor = major
                    )
                )
                task = TaskState("启动中", "正在拉起游戏进程")
                val process = Launcher.start(plan) { line -> pushLog(line) }
                runningPids = runningPids + (instance.id to process.pid())
                activeInstanceId = instance.id
                updateInstance(instance.copy(lastPlayed = System.currentTimeMillis()))
                updateSettings { it.copy(lastInstance = instance.id) }
                toast("已启动 ${instance.name}")
                if (settings.hideAfterLaunch) onHideWindow?.invoke()
                task = null
                process.onExit().thenAccept { p ->
                    runningPids = runningPids - instance.id
                    pushLog("游戏退出，退出码 ${p.exitValue()}")
                }
                onDone()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException || t is java.util.concurrent.CancellationException) {
                    toast("已中断")
                } else {
                    AppLog.error("启动失败", t)
                    toast(t.message ?: "启动失败")
                }
                task = null
                cancelRequested = false
            }
        }
    }

    fun exportLaunchScript(instance: Instance) {
        scope.launch {
            try {
                val root = minecraftRoot
                val installer = Installer(root, settings.threads)
                val versionId = instance.versionId.ifBlank { instance.mcVersion }
                val resolved = installer.resolve(versionId)
                val major = if (instance.javaMajor > 0) instance.javaMajor else resolved.javaVersion.majorVersion
                val java = JavaRuntime.ensure(major).exe
                val account = activeAccount ?: Account.offline("Player")
                val plan = Launcher.build(
                    java, resolved, root,
                    LaunchOptions(
                        versionId, instance.gameDirPath(), account,
                        instance.memoryMb, instance.width, instance.height, jvmExtra = instance.jvmArgs
                    )
                )
                val target = AppPaths.root.resolve("launch-${instance.name.replace(Regex("[^\\w\\u4e00-\\u9fa5]"), "_")}.bat")
                Launcher.exportScript(plan, target, instance.name)
                Fs.openFolder(AppPaths.root)
                toast("启动脚本已导出：${target.fileName}")
            } catch (t: Throwable) {
                toast(t.message ?: "导出失败")
            }
        }
    }

    fun openGameDir(instance: Instance) {
        Fs.openFolder(instance.gameDirPath())
    }

    fun refreshManifest() {
        scope.launch {
            runCatching { manifest = Mojang.manifest(force = true) }
                .onFailure { toast("获取版本列表失败") }
        }
    }

    /** 退出前把状态全部落盘 */
    fun requestExit() {
        runCatching { SettingsStore.save(settings) }
        runCatching { InstanceStore.save(instances) }
        runCatching { AccountStore.save(accounts) }
        kotlin.system.exitProcess(0)
    }

    /* ------------------------------ 中断 / 内容管理 ------------------------------ */

    fun cancelTask() {
        if (task?.running == true) {
            cancelRequested = true
            toast("正在中断…")
        }
    }

    /* ------------------------------ MOD 更新 / 去重 ------------------------------ */
    var modUpdates by mutableStateOf<List<com.mclauncher.core.ModUpdates.Update>>(emptyList())
        private set
    var modUpdateBusy by mutableStateOf(false)
        private set
    var modUpdateMessage by mutableStateOf("")
        private set

    fun checkModUpdates(instance: Instance) {
        if (modUpdateBusy) return
        scope.launch {
            modUpdateBusy = true
            modUpdateMessage = "正在检测更新…"
            try {
                val dir = instance.gameDirPath().resolve("mods")
                modUpdates = com.mclauncher.core.ModUpdates.check(dir, instance.mcVersion, instance.loader)
                modUpdateMessage = if (modUpdates.isEmpty()) "所有 MOD 都是最新版" else "${modUpdates.size} 个 MOD 有新版本"
                toast(modUpdateMessage)
            } catch (t: Throwable) {
                AppLog.error("检测 MOD 更新失败", t)
                modUpdateMessage = "检测失败：${t.message}"
                toast(modUpdateMessage)
            } finally {
                modUpdateBusy = false
            }
        }
    }

    fun updateMods(instance: Instance, updates: List<com.mclauncher.core.ModUpdates.Update>) {
        if (updates.isEmpty()) return
        scope.launch {
            val dlId = addDownloadTask("mod", "更新 ${updates.size} 个 MOD", "MOD 更新", total = updates.size.toLong())
            var ok = 0
            var failed = 0
            for (u in updates) {
                try {
                    val bytes = com.mclauncher.core.Http.bytes(u.url)
                    val dir = instance.gameDirPath().resolve("mods")
                    Fs.ensureDir(dir)
                    val target = dir.resolve(u.fileName)
                    java.nio.file.Files.write(target, bytes)
                    runCatching { java.nio.file.Files.deleteIfExists(u.file) }
                    ok++
                } catch (t: Throwable) {
                    failed++
                    AppLog.error("更新 ${u.projectTitle} 失败", t)
                    errorToast = "${u.projectTitle} 更新失败：${t.message ?: "未知错误"}"
                }
                patchDownloadTask(dlId) { it.done = (ok + failed).toLong(); it.message = u.projectTitle }
            }
            finishDownloadTask(
                dlId,
                if (failed == 0) "done" else "failed",
                if (failed == 0) "已更新 $ok 个" else "$failed 个更新失败"
            )
            modUpdates = emptyList()
            toast("MOD 更新完成：$ok 成功 / $failed 失败")
        }
    }

    fun removeDuplicateMods(instance: Instance): Int {
        val dir = instance.gameDirPath().resolve("mods")
        val dups = com.mclauncher.core.ModUpdates.duplicates(dir)
        var n = 0
        dups.forEach { pair ->
            runCatching {
                java.nio.file.Files.deleteIfExists(pair.second)
                n++
            }
        }
        toast(if (n == 0) "没有发现重复 MOD" else "已清理 $n 个重复 MOD")
        return n
    }

    fun listContent(instance: Instance, kind: String): List<ContentItem> {
        val dir = instance.gameDirPath().resolve(kind)
        if (!Files.isDirectory(dir)) return emptyList()
        return runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .map { path ->
                        val raw = path.fileName.toString()
                        val enabled = !raw.endsWith(".disabled")
                        val display = if (enabled) raw else raw.removeSuffix(".disabled")
                        ContentItem(display, path, enabled, runCatching { Files.size(path) }.getOrDefault(0))
                    }
                    .filter { it.name.endsWith(".jar") || it.name.endsWith(".zip") }
                    .sorted(compareBy { it.name.lowercase() })
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    fun toggleContent(item: ContentItem) {
        runCatching {
            val target = if (item.enabled) {
                item.path.resolveSibling(item.name + ".disabled")
            } else {
                item.path.resolveSibling(item.name.removeSuffix(".disabled"))
            }
            Files.move(item.path, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            toast(if (item.enabled) "已禁用 ${item.name}" else "已启用 ${item.name}")
        }.onFailure { toast("操作失败：${it.message}") }
    }

    fun deleteContent(item: ContentItem) {
        runCatching {
            Files.deleteIfExists(item.path)
            toast("已删除 ${item.name}")
        }.onFailure { toast("删除失败：${it.message}") }
    }

    fun importContent(instance: Instance, kind: String) {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            dialogTitle = "选择要导入的文件"
        }
        if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return
        val dir = instance.gameDirPath().resolve(kind)
        runCatching {
            Fs.ensureDir(dir)
            chooser.selectedFiles.forEach { file ->
                Files.copy(
                    file.toPath(),
                    dir.resolve(file.name),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            toast("已导入 ${chooser.selectedFiles.size} 个文件")
        }.onFailure { toast("导入失败：${it.message}") }
    }

    fun hasIris(instance: Instance): Boolean =
        listContent(instance, "mods").any { it.name.contains("iris", true) }

    fun chooseImageFile(): String? {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
            dialogTitle = "选择背景图片"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("图片 (png/jpg/webp)", "png", "jpg", "jpeg", "webp")
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else null
    }

    fun chooseDirectory(title: String): String? {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else null
    }

    fun applyVersionSettings(updated: Instance) {
        updateInstance(updated)
        toast("版本设置已保存")
    }

    /* ------------------------------ 下载会话 ------------------------------ */

    fun dismissDownloadSession() {
        downloadSession = null
    }

    /**
     * 按计划下载：逐个文件下，实时汇总速度 / 平均速度 / 剩余时间。
     */
    fun startDownloadPlan(title: String, items: List<com.mclauncher.mods.ModPlanItem>, target: Instance) {
        if (items.isEmpty()) return
        scope.launch {
            val started = System.currentTimeMillis()
            var entries = items.map {
                DownloadEntry(it.projectTitle, it.source, it.version.file?.size ?: 0)
            }
            var doneBytes = 0L
            val total = entries.sumOf { it.size }
            val samples = ArrayDeque<Float>()
            var lastSampleTime = started
            var lastSampleBytes = 0L
            var lastPublish = 0L
            // 先登记到下载中心，publish 里实时同步进度
            val dlId = addDownloadTask("mod", title, "MOD 下载", total = total)
            downloadsFlash++

            fun publish(force: Boolean = false) {
                val now = System.currentTimeMillis()
                if (!force && now - lastPublish < 160) return
                lastPublish = now
                val dt = (now - lastSampleTime).coerceAtLeast(1)
                var speed = 0.0
                if (now - lastSampleTime >= 500) {
                    speed = (doneBytes - lastSampleBytes) * 1000.0 / dt
                    samples.addLast((speed / 1024.0).toFloat())
                    while (samples.size > 90) samples.removeFirst()
                    lastSampleTime = now
                    lastSampleBytes = doneBytes
                } else {
                    speed = downloadSession?.speed ?: 0.0
                }
                val elapsed = (now - started).coerceAtLeast(1)
                val avg = doneBytes * 1000.0 / elapsed
                val eta = if (avg > 1024) ((total - doneBytes) / avg).toLong() else -1L
                downloadSession = DownloadSession(
                    title = title,
                    entries = entries,
                    startedAt = started,
                    doneBytes = doneBytes,
                    totalBytes = total,
                    speed = speed,
                    avgSpeed = avg,
                    etaSeconds = eta,
                    samples = samples.toList(),
                    finished = false
                )
                patchDownloadTask(dlId) {
                    it.done = doneBytes
                    it.total = total
                    it.session = downloadSession
                    it.message = entries.firstOrNull { e -> e.status.contains("下载") }?.name ?: it.message
                }
            }

            downloadSession = DownloadSession(title, entries, started, 0, total, 0.0, 0.0, -1, emptyList())
            // 不再弹详细面板：进度都写进下载中心
            patchDownloadTask(dlId) { it.session = downloadSession }
            cancelRequested = false
            for ((index, item) in items.withIndex()) {
                if (cancelRequested || isTaskCanceled(dlId)) {
                    val list = entries.toMutableList()
                    list[index] = list[index].copy(status = "已中断")
                    entries = list
                    downloadSession = DownloadSession(
                        title, entries, started, doneBytes, total,
                        downloadSession?.speed ?: 0.0, downloadSession?.avgSpeed ?: 0.0,
                        -1, samples.toList(), finished = true
                    )
                    task = null
                    finishDownloadTask(dlId, "canceled", "已中断")
                    toast("已中断下载")
                    return@launch
                }
                val current = entries.toMutableList()
                current[index] = current[index].copy(status = "下载中")
                entries = current
                publish(true)
                try {
                    ModsApi.download(
                        item.version, target,
                        onProgress = {},
                        onBytes = { done, _ ->
                            if (cancelRequested) throw kotlinx.coroutines.CancellationException("已中断下载")
                            val list = entries.toMutableList()
                            list[index] = list[index].copy(done = done)
                            entries = list
                            doneBytes = entries.sumOf { it.done }
                            publish()
                        }
                    )
                    val list = entries.toMutableList()
                    list[index] = list[index].copy(done = list[index].size, status = "完成")
                    entries = list
                    doneBytes = entries.sumOf { it.done }
                } catch (t: Throwable) {
                    AppLog.error("下载 ${item.projectTitle} 失败", t)
                    val list = entries.toMutableList()
                    list[index] = list[index].copy(status = "失败：" + (t.message ?: "未知错误"))
                    entries = list
                    errorToast = "${item.projectTitle} 下载失败：${t.message ?: "未知错误"}"
                }
                publish(true)
            }
            downloadSession = downloadSession?.copy(entries = entries, doneBytes = doneBytes, finished = true, etaSeconds = 0)
            patchDownloadTask(dlId) { it.session = downloadSession }
            val ok = entries.count { it.status == "完成" }
            if (ok == entries.size) {
                finishDownloadTask(dlId, "done", "已完成")
                toast("下载完成：$ok / ${entries.size}")
            } else {
                finishDownloadTask(dlId, "failed", "${entries.size - ok} 个文件下载失败")
                toast("部分文件下载失败：$ok / ${entries.size}")
            }
        }
    }

    /* ------------------------------ 整合包 ------------------------------ */

    fun installModpack(project: ModProject, version: ModVersionInfo, onDone: (Instance?) -> Unit = {}) {
        scope.launch {
            try {
                task = TaskState("下载整合包", version.file?.fileName ?: project.title)
                val file = version.file ?: error("这个整合包没有可下载文件")
                val tmp = AppPaths.cache.resolve("pack-${version.id}.mrpack")
                Fs.writeAtomic(tmp, com.mclauncher.core.Http.bytes(file.url))
                val dir = AppPaths.cache.resolve("pack-${version.id}")
                Fs.deleteRecursively(dir)
                Fs.ensureDir(dir)
                task = TaskState("解压整合包", file.fileName)
                Fs.unzip(tmp, dir)
                val indexFile = dir.resolve("modrinth.index.json")
                if (!Files.exists(indexFile)) error("不是有效的 .mrpack（缺少 modrinth.index.json）")
                val index = com.mclauncher.mods.ModpackParser.parse(Files.readString(indexFile))
                if (index.mcVersion.isBlank()) error("整合包没有声明游戏版本")

                task = TaskState("创建整合包版本", index.mcVersion)
                val instance = createVersionNow(
                    name = project.title,
                    mcVersion = index.mcVersion,
                    loader = index.loader,
                    loaderVersion = index.loaderVersion,
                    memoryMb = com.mclauncher.core.SysInfo.defaultMemoryMb,
                    onStage = { s, p -> task = TaskState(s, "${(p * 100).toInt()}%", p) }
                )

                task = TaskState("下载整合包内容", "${index.files.size} 个文件")
                val tasks = index.files.map { f ->
                    com.mclauncher.core.DownloadTask(
                        url = f.url,
                        dest = instance.gameDirPath().resolve(f.path),
                        sha1 = f.sha1,
                        size = f.size,
                        label = f.path.substringAfterLast('/')
                    )
                }
                val downloader = com.mclauncher.core.Downloader(settings.threads) { cancelRequested }
                downloader.run(tasks) { progress ->
                    task = TaskState("下载整合包内容", "${progress.done}/${progress.total}", progress.byteRatio)
                }

                task = TaskState("应用覆盖文件")
                listOf("overrides", "client-overrides").forEach { folder ->
                    val src = dir.resolve(folder)
                    if (Files.isDirectory(src)) {
                        for (path in Fs.walk(src)) {
                            val rel = src.relativize(path)
                            runCatching { Fs.copy(path, instance.gameDirPath().resolve(rel.toString())) }
                        }
                    }
                }
                Fs.deleteRecursively(dir)
                runCatching { Files.deleteIfExists(tmp) }

                updateInstance(instance.copy(icon = "crafting"))
                toast("整合包 ${project.title} 安装完成")
                onDone(instance)
            } catch (t: Throwable) {
                AppLog.error("整合包安装失败", t)
                toast(t.message ?: "整合包安装失败")
                onDone(null)
            } finally {
                task = null
            }
        }
    }

    fun importModpackFromFile(onDone: (Instance?) -> Unit = {}) {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
            dialogTitle = "选择 .mrpack / .zip 整合包"
        }
        if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile
        scope.launch {
            try {
                task = TaskState("导入整合包", file.name)
                val dir = AppPaths.cache.resolve("import-${System.currentTimeMillis()}")
                Fs.ensureDir(dir)
                Fs.unzip(file.toPath(), dir)
                val indexFile = dir.resolve("modrinth.index.json")
                if (!Files.exists(indexFile)) {
                    // 不是 mrpack：当成普通压缩包，直接解压进一个新版本目录
                    error("这不是 .mrpack 整合包（缺少 modrinth.index.json）。普通压缩包请用「内容管理 → 导入文件」。")
                }
                val index = com.mclauncher.mods.ModpackParser.parse(Files.readString(indexFile))
                val instance = createVersionNow(
                    name = file.nameWithoutExtension,
                    mcVersion = index.mcVersion,
                    loader = index.loader,
                    loaderVersion = index.loaderVersion,
                    memoryMb = com.mclauncher.core.SysInfo.defaultMemoryMb,
                    onStage = { s, p -> task = TaskState(s, "${(p * 100).toInt()}%", p) }
                )
                task = TaskState("下载整合包内容", "${index.files.size} 个文件")
                val tasks = index.files.map { f ->
                    com.mclauncher.core.DownloadTask(f.url, instance.gameDirPath().resolve(f.path), f.sha1, f.size, f.path.substringAfterLast('/'))
                }
                com.mclauncher.core.Downloader(settings.threads) { cancelRequested }.run(tasks) { p ->
                    task = TaskState("下载整合包内容", "${p.done}/${p.total}", p.byteRatio)
                }
                listOf("overrides", "client-overrides").forEach { folder ->
                    val src = dir.resolve(folder)
                    if (Files.isDirectory(src)) {
                        for (path in Fs.walk(src)) {
                            runCatching { Fs.copy(path, instance.gameDirPath().resolve(src.relativize(path).toString())) }
                        }
                    }
                }
                Fs.deleteRecursively(dir)
                toast("导入完成：${instance.name}")
                onDone(instance)
            } catch (t: Throwable) {
                AppLog.error("导入整合包失败", t)
                toast(t.message ?: "导入失败")
                onDone(null)
            } finally {
                task = null
            }
        }
    }

    /** 删版本：先把游戏目录丢进回收站，再从列表移除 */
    fun deleteVersion(instance: Instance) {
        // 先把它从列表里拿掉，界面上立刻消失；文件搬到回收站再慢慢做
        removeInstance(instance.id)
        scope.launch {
            val dir = instance.gameDirPath()
            val moved = runCatching { com.mclauncher.core.RecycleBin.delete(dir) }.getOrDefault(false)
            if (!moved) {
                toast("回收站不可用，已从列表移除（文件保留在 $dir）")
            } else {
                toast("已移到回收站：${instance.name}")
            }
            reloadInstances()
        }
    }

    /* ------------------------------ 内部：创建版本 ------------------------------ */

    private suspend fun createVersionNow(
        name: String,
        mcVersion: String,
        loader: String,
        loaderVersion: String,
        memoryMb: Int,
        onStage: (String, Float) -> Unit
    ): Instance {
        val root = minecraftRoot
        Fs.ensureDir(root)
        val displayName = name.ifBlank { mcVersion }

        var versionId = mcVersion
        when (loader) {
            "fabric" -> {
                val lv = loaderVersion.ifBlank { Loaders.fabricLoaders(mcVersion).firstOrNull() ?: "" }
                if (lv.isBlank()) error("Fabric 没有适用于 $mcVersion 的加载器版本")
                versionId = Loaders.installFabric(mcVersion, lv, root)
            }
            "forge" -> {
                val lv = loaderVersion.ifBlank { Loaders.forgeVersions()[mcVersion]?.firstOrNull() ?: "" }
                if (lv.isBlank()) error("Forge 没有适用于 $mcVersion 的版本")
                val java = JavaRuntime.ensure(17) { onStage(it, -1f) }
                versionId = Loaders.installForge(java.exe, mcVersion, lv, root) { line -> onStage(line, -1f) }
            }
            "neoforge" -> {
                val lv = loaderVersion.ifBlank { Loaders.neoForgeVersions()[mcVersion]?.firstOrNull() ?: "" }
                if (lv.isBlank()) error("NeoForge 没有适用于 $mcVersion 的版本")
                val java = JavaRuntime.ensure(21) { onStage(it, -1f) }
                versionId = Loaders.installNeoForge(java.exe, mcVersion, lv, root) { line -> onStage(line, -1f) }
            }
        }

        // 版本隔离：游戏目录 = versions/<versionId>
        val gameDir = Fs.ensureDir(root.resolve("versions").resolve(versionId))
        InstanceStore.ensureGameDirs(gameDir)
        val installer = Installer(root, settings.threads) { cancelRequested }
        val installed = installer.install(versionId) { stage ->
            onStage(stage.title, stage.progress?.byteRatio ?: -1f)
        }

        val instance = Instance(
            name = displayName,
            mcVersion = mcVersion,
            loader = loader,
            loaderVersion = loaderVersion,
            versionId = versionId,
            gameDir = gameDir.toString(),
            memoryMb = memoryMb,
            javaMajor = installed.resolved.javaVersion.majorVersion
        )
        instances = instances + instance
        activeInstanceId = instance.id
        saveInstances()
        return instance
    }

    companion object {
        val palettes: List<AccentPalette> get() = AC_PALETTES
    }
}

data class ContentItem(
    val name: String,
    val path: Path,
    val enabled: Boolean,
    val size: Long
)
data class DownloadEntry(
    val name: String,
    val source: String,
    val size: Long,
    val done: Long = 0,
    val status: String = "等待"
)

data class DownloadSession(
    val title: String,
    val entries: List<DownloadEntry>,
    val startedAt: Long = System.currentTimeMillis(),
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    val speed: Double = 0.0,
    val avgSpeed: Double = 0.0,
    val etaSeconds: Long = -1,
    val samples: List<Float> = emptyList(),
    val finished: Boolean = false
) {
    val ratio: Float get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}
/** 打开 MOD 下载向导需要的参数 */
data class ModWizardRequest(
    val project: com.mclauncher.mods.ModProject,
    val gameVersion: String,
    val loader: String
)