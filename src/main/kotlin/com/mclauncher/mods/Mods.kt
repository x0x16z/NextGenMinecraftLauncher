package com.mclauncher.mods

import com.mclauncher.core.AppJson
import com.mclauncher.core.AppLog
import com.mclauncher.core.Http
import com.mclauncher.core.str
import com.mclauncher.core.text
import com.mclauncher.instance.Instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path

object ModSource {
    const val MODRINTH = "modrinth"
    const val CURSEFORGE = "curseforge"
}

data class ModProject(
    val source: String,
    val id: String,
    val slug: String,
    val title: String,
    val summary: String,
    val author: String,
    val iconUrl: String?,
    val downloads: Long,
    val followers: Long,
    val categories: List<String>,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val updated: String
)

data class ModFile(
    val fileName: String,
    val url: String,
    val size: Long,
    val sha1: String?,
    val primary: Boolean
)

data class ModVersionInfo(
    val id: String,
    val name: String,
    val versionNumber: String,
    val releaseType: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val published: String,
    val downloads: Long,
    val file: ModFile?,
    val dependencies: List<ModDependency> = emptyList()
)

/** 分类在 UI 上显示的名字 -> Modrinth 的 category slug */
val MOD_CATEGORIES = listOf(
    "全部" to emptyList<String>(),
    "优化" to listOf("optimization"),
    "辅助" to listOf("utility"),
    "科技" to listOf("technology"),
    "魔法" to listOf("magic"),
    "冒险" to listOf("adventure"),
    "建筑" to listOf("building"),
    "装饰" to listOf("decoration"),
    "世界" to listOf("worldgen"),
    "库" to listOf("library")
)

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

private fun stripHtml(input: String): String =
    input.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&quot;", "\"").trim()

object ModsApi {

    /* ------------------------------ Modrinth ------------------------------ */

    suspend fun searchModrinth(
        query: String,
        gameVersion: String?,
        loader: String?,
        categories: List<String>,
        sort: String,
        offset: Int,
        limit: Int = 20
    ): List<ModProject> = withContext(Dispatchers.IO) {
        val facets = mutableListOf<String>()
        facets += """["project_type:mod"]"""
        if (!gameVersion.isNullOrBlank()) facets += """["versions:$gameVersion"]"""
        if (!loader.isNullOrBlank() && loader != "vanilla") facets += """["categories:$loader"]"""
        if (categories.isNotEmpty()) {
            facets += categories.joinToString(",", "[", "]") { "\"categories:$it\"" }
        }
        val url = buildString {
            append("https://api.modrinth.com/v2/search?query=").append(encode(query))
            append("&facets=").append(encode(facets.joinToString(",", "[", "]")))
            append("&limit=").append(limit)
            append("&offset=").append(offset)
            append("&index=").append(sort)
        }
        runCatching {
            val json = AppJson.parseToJsonElement(Http.text(url)).jsonObject
            (json["hits"] as? JsonArray ?: return@runCatching emptyList()).map { hit ->
                val o = hit.jsonObject
                ModProject(
                    source = ModSource.MODRINTH,
                    id = o.str("project_id") ?: "",
                    slug = o.str("slug") ?: "",
                    title = o.str("title") ?: "",
                    summary = o.str("description") ?: "",
                    author = o.str("author") ?: "",
                    iconUrl = o.str("icon_url"),
                    downloads = o.str("downloads")?.toLongOrNull() ?: 0,
                    followers = o.str("follows")?.toLongOrNull() ?: 0,
                    categories = (o["categories"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                    gameVersions = (o["versions"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                    loaders = (o["loaders"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                    updated = o.str("date_modified") ?: ""
                )
            }
        }.getOrElse {
            AppLog.error("Modrinth 搜索失败", it)
            emptyList()
        }
    }

    suspend fun versionsModrinth(projectId: String, gameVersion: String?, loader: String?): List<ModVersionInfo> =
        withContext(Dispatchers.IO) {
            val query = buildList {
                if (!gameVersion.isNullOrBlank()) add("game_versions=" + encode("""["$gameVersion"]"""))
                if (!loader.isNullOrBlank() && loader != "vanilla") add("loaders=" + encode("""["$loader"]"""))
            }.joinToString("&")
            val url = "https://api.modrinth.com/v2/project/$projectId/version" + if (query.isBlank()) "" else "?$query"
            runCatching {
                val array = AppJson.parseToJsonElement(Http.text(url)).jsonArray
                array.map { item ->
                    val o = item.jsonObject
                    val files = (o["files"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
                    val primary = files.firstOrNull { it.str("primary") == "true" } ?: files.firstOrNull()
                    ModVersionInfo(
                        id = o.str("id") ?: "",
                        name = o.str("name") ?: "",
                        versionNumber = o.str("version_number") ?: "",
                        releaseType = o.str("version_type") ?: "release",
                        gameVersions = (o["game_versions"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                        loaders = (o["loaders"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                        published = o.str("date_published") ?: "",
                        downloads = o.str("downloads")?.toLongOrNull() ?: 0,
                        file = primary?.let {
                            ModFile(
                                fileName = it.str("filename") ?: "mod.jar",
                                url = it.str("url") ?: "",
                                size = it.str("size")?.toLongOrNull() ?: 0,
                                sha1 = (it["hashes"]?.jsonObject)?.str("sha1"),
                                primary = true
                            )
                        },
                        dependencies = (o["dependencies"] as? JsonArray)?.mapNotNull { dep ->
                            val d = dep.jsonObject
                            ModDependency(
                                projectId = d.str("project_id"),
                                versionId = d.str("version_id"),
                                kind = d.str("dependency_type") ?: "required"
                            )
                        } ?: emptyList()
                    )
                }
            }.getOrElse {
                AppLog.error("获取 Modrinth 版本失败", it)
                emptyList()
            }
        }

    /* ------------------------------ CurseForge ------------------------------ */

    /**
     * 没填 API Key 时走 MCIM 镜像（社区维护的 CurseForge 代理，不需要 key），
     * 填了就用官方接口。参考 PCL 社区版的做法。
     */
    private fun curseforgeBase(): String = "https://mod.mcimirror.top/curseforge/v1"

    private fun curseforgeHeaders(): Map<String, String> = mapOf("Accept" to "application/json")

    private fun loaderType(loader: String?): Int? = when (loader) {
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> null
    }

    fun searchCurseforge(
        query: String,
        gameVersion: String?,
        loader: String?,
        sort: String,
        offset: Int,
        limit: Int = 20
    ): List<ModProject> {
        // 没有 key 时走 MCIM 镜像，所以不再直接返回空
        val sortField = when (sort) {
            "downloads" -> 6
            "newest" -> 11
            "updated" -> 3
            else -> 2
        }
        val url = buildString {
            append(curseforgeBase()).append("/mods/search?gameId=432&classId=6")
            append("&searchFilter=").append(encode(query))
            append("&sortField=").append(sortField).append("&sortOrder=desc")
            append("&pageSize=").append(limit).append("&index=").append(offset)
            if (!gameVersion.isNullOrBlank()) append("&gameVersion=").append(encode(gameVersion))
            loaderType(loader)?.let { append("&modLoaderType=").append(it) }
        }
        return runCatching {
            val json = AppJson.parseToJsonElement(
                Http.text(url, curseforgeHeaders())
            ).jsonObject
            (json["data"] as? JsonArray ?: return@runCatching emptyList()).map { item ->
                val o = item.jsonObject
                ModProject(
                    source = ModSource.CURSEFORGE,
                    id = o.str("id") ?: "",
                    slug = o.str("slug") ?: "",
                    title = o.str("name") ?: "",
                    summary = stripHtml(o.str("summary") ?: ""),
                    author = (o["authors"] as? JsonArray)?.firstOrNull()?.jsonObject?.str("name") ?: "",
                    iconUrl = (o["logo"]?.jsonObject)?.str("thumbnailUrl"),
                    downloads = o.str("downloadCount")?.toLongOrNull() ?: 0,
                    followers = o.str("thumbsUpCount")?.toLongOrNull() ?: 0,
                    categories = (o["categories"] as? JsonArray)?.mapNotNull { it.jsonObject.str("name") } ?: emptyList(),
                    gameVersions = (o["latestFilesIndexes"] as? JsonArray)
                        ?.mapNotNull { it.jsonObject.str("gameVersion") }?.distinct() ?: emptyList(),
                    loaders = (o["latestFilesIndexes"] as? JsonArray)
                        ?.mapNotNull { it.jsonObject.str("modLoader")?.lowercase() }
                        ?.distinct() ?: emptyList(),
                    updated = o.str("dateModified") ?: ""
                )
            }
        }.getOrElse {
            AppLog.error("CurseForge 搜索失败", it)
            emptyList()
        }
    }

    fun versionsCurseforge(modId: String, gameVersion: String?, loader: String?): List<ModVersionInfo> {
        val url = buildString {
            append(curseforgeBase()).append("/mods/$modId/files?pageSize=50")
            if (!gameVersion.isNullOrBlank()) append("&gameVersion=").append(encode(gameVersion))
            loaderType(loader)?.let { append("&modLoaderType=").append(it) }
        }
        return runCatching {
            val json = AppJson.parseToJsonElement(
                Http.text(url, curseforgeHeaders())
            ).jsonObject
            (json["data"] as? JsonArray ?: return@runCatching emptyList()).map { item ->
                val o = item.jsonObject
                ModVersionInfo(
                    id = o.str("id") ?: "",
                    name = o.str("displayName") ?: "",
                    versionNumber = o.str("displayName") ?: "",
                    releaseType = when (o.str("releaseType")) {
                        "1" -> "release"
                        "2" -> "beta"
                        else -> "alpha"
                    },
                    gameVersions = (o["gameVersions"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                    loaders = emptyList(),
                    published = o.str("fileDate") ?: "",
                    downloads = o.str("downloadCount")?.toLongOrNull() ?: 0,
                    file = ModFile(
                        fileName = o.str("fileName") ?: "mod.jar",
                        url = o.str("downloadUrl") ?: "",
                        size = o.str("fileLength")?.toLongOrNull() ?: 0,
                        sha1 = (o["hashes"] as? JsonArray)
                            ?.map { it.jsonObject }
                            ?.firstOrNull { it.str("algo") == "1" }?.str("value"),
                        primary = true
                    )
                )
            }
        }.getOrElse {
            AppLog.error("CurseForge 文件列表失败", it)
            emptyList()
        }
    }

    private fun loaderNameOf(type: Int?): String? = when (type) {
        1 -> "forge"
        4 -> "fabric"
        5 -> "quilt"
        6 -> "neoforge"
        else -> null
    }

    /* ------------------------------ 下载 ------------------------------ */

    /** 下载到实例的 mods 目录，返回落盘路径 */
    suspend fun download(
        version: ModVersionInfo,
        instance: Instance,
        onProgress: (Float) -> Unit = {},
        onBytes: (Long, Long) -> Unit = { _, _ -> }
    ): Path = withContext(Dispatchers.IO) {
        val file = version.file ?: throw IllegalStateException("该版本没有可下载文件")
        require(file.url.isNotBlank()) { "作者禁止第三方下载" }
        val modsDir = instance.gameDirPath().resolve("mods").also { Files.createDirectories(it) }
        val target = modsDir.resolve(file.fileName)
        if (Files.exists(target) && file.size > 0 && Files.size(target) == file.size) return@withContext target
        val tmp = modsDir.resolve(file.fileName + ".part")
        // 带进度的流式下载
        val request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(file.url))
            .timeout(java.time.Duration.ofMinutes(5)).GET().build()
        val response = Http.client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) error("下载失败 HTTP ${response.statusCode()}")
        var written = 0L
        response.body().use { input ->
            Files.newOutputStream(tmp).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    written += n
                    com.mclauncher.core.Bandwidth.throttle(n)
                    if (file.size > 0) onProgress(written.toFloat() / file.size)
                    onBytes(written, file.size)
                }
            }
        }
        val sha1 = file.sha1
        if (!sha1.isNullOrBlank()) {
            val actual = com.mclauncher.core.Hashing.sha1(tmp)
            if (!actual.equals(sha1, true)) {
                Files.deleteIfExists(tmp)
                error("文件校验失败")
            }
        }
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        AppLog.log("已安装 MOD: ${file.fileName} -> ${instance.name}")
        target
    }
}
/* ------------------------------ 依赖解析 / 推荐 / 整合包 ------------------------------ */

object ModPlanner {

    /** 把某个 MOD 版本标成"可直接下载"的计划项 */
    fun selfItem(project: ModProject, version: ModVersionInfo, source: String): ModPlanItem =
        ModPlanItem(
            role = "self",
            projectId = project.id,
            projectTitle = project.title,
            version = version,
            source = source,
            selected = true
        )

    /**
     * 解析必装依赖：沿着 required 依赖一层层找，能在当前游戏版本/加载器下找到版本就带上。
     * 找不到的跳过并在 note 里说明，绝不阻塞主流程。
     */
    suspend fun resolveRequired(
        root: ModVersionInfo,
        gameVersion: String?,
        loader: String?,
        fetchProjectTitle: suspend (String) -> String?
    ): List<ModPlanItem> {
        // 没有明确的游戏版本就不猜依赖：之前用空版本查，会顺着别人的依赖链
        // 把一堆完全不相关的冷门 MOD 也拉进来（iris -> 乱七八糟的库）
        if (gameVersion.isNullOrBlank()) return emptyList()

        val result = mutableListOf<ModPlanItem>()
        val visited = HashSet<String>()
        var queue = root.dependencies.filter { it.kind == "required" }

        var guard = 0
        while (queue.isNotEmpty() && guard++ < 20) {
            val next = mutableListOf<ModDependency>()
            for (dep in queue) {
                val pid = dep.projectId ?: continue
                if (!visited.add(pid)) continue
                val versions = ModsApi.versionsModrinth(pid, gameVersion, loader)
                    // 再自己筛一遍：只认当前游戏版本、且加载器匹配的版本
                    .filter { version ->
                        version.file != null &&
                            version.gameVersions.contains(gameVersion) &&
                            (loader.isNullOrBlank() || version.loaders.isEmpty() ||
                                version.loaders.any { it.equals(loader, ignoreCase = true) })
                    }
                val picked = dep.versionId?.let { vid -> versions.firstOrNull { it.id == vid } }
                    ?: versions.firstOrNull()
                if (picked == null || picked.file == null) continue
                val title = fetchProjectTitle(pid) ?: pid
                result += ModPlanItem(
                    role = "required",
                    projectId = pid,
                    projectTitle = title,
                    version = picked,
                    source = ModSource.MODRINTH,
                    selected = true,
                    note = "必装依赖"
                )
                next += picked.dependencies.filter { it.kind == "required" }
            }
            queue = next
        }
        return result
    }

    /**
     * "推荐"怎么算：同分类 + 同游戏版本 + 同加载器里下载量最高的几个，排除本体和已选。
     * 这是很粗暴但足够好用的启发式。
     */
    suspend fun recommend(
        project: ModProject,
        gameVersion: String?,
        loader: String?,
        exclude: Set<String>,
        limit: Int = 3
    ): List<ModPlanItem> {
        val category = project.categories.firstOrNull { cat ->
            !cat.equals("fabric", true) && !cat.equals("forge", true) && !cat.equals("neoforge", true) &&
                !cat.equals("quilt", true) && cat != "library"
        } ?: return emptyList()

        val hits = ModsApi.searchModrinth("", gameVersion, loader, listOf(category), "downloads", 0, 12)
        val out = mutableListOf<ModPlanItem>()
        for (hit in hits) {
            if (hit.id in exclude || hit.id == project.id) continue
            val versions = ModsApi.versionsModrinth(hit.id, gameVersion, loader)
            val version = versions.firstOrNull { it.file != null } ?: continue
            out += ModPlanItem(
                role = "recommended",
                projectId = hit.id,
                projectTitle = hit.title,
                version = version,
                source = ModSource.MODRINTH,
                selected = false,
                note = "同类型热门"
            )
            if (out.size >= limit) break
        }
        return out
    }
}
/* ------------------------------ 整合包接口 ------------------------------ */

object ModpackApi {

    suspend fun search(query: String, gameVersion: String? = null, offset: Int = 0, limit: Int = 20): List<ModProject> =
        withContext(Dispatchers.IO) {
            val facets = mutableListOf("""["project_type:modpack"]""")
            if (!gameVersion.isNullOrBlank()) facets += """["versions:$gameVersion"]"""
            val url = buildString {
                append("https://api.modrinth.com/v2/search?query=")
                append(java.net.URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20"))
                append("&facets=").append(java.net.URLEncoder.encode(facets.joinToString(",", "[", "]"), Charsets.UTF_8).replace("+", "%20"))
                append("&limit=").append(limit).append("&offset=").append(offset).append("&index=relevance")
            }
            runCatching {
                val json = AppJson.parseToJsonElement(com.mclauncher.core.Http.text(url)).jsonObject
                (json["hits"] as? JsonArray)?.map { hit ->
                    val o = hit.jsonObject
                    ModProject(
                        source = ModSource.MODRINTH,
                        id = o.str("project_id") ?: "",
                        slug = o.str("slug") ?: "",
                        title = o.str("title") ?: "",
                        summary = o.str("description") ?: "",
                        author = o.str("author") ?: "",
                        iconUrl = o.str("icon_url"),
                        downloads = o.str("downloads")?.toLongOrNull() ?: 0,
                        followers = o.str("follows")?.toLongOrNull() ?: 0,
                        categories = (o["categories"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                        gameVersions = (o["versions"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                        loaders = (o["loaders"] as? JsonArray)?.mapNotNull { it.text() } ?: emptyList(),
                        updated = o.str("date_modified") ?: ""
                    )
                } ?: emptyList()
            }.getOrElse {
                com.mclauncher.core.AppLog.error("整合包搜索失败", it)
                emptyList()
            }
        }

    /** 整合包版本（只保留 .mrpack 文件） */
    suspend fun versions(projectId: String): List<ModVersionInfo> =
        ModsApi.versionsModrinth(projectId, null, null).filter {
            it.file?.fileName?.endsWith(".mrpack") == true
        }
}