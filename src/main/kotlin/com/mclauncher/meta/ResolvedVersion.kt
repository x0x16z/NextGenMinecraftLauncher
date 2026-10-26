package com.mclauncher.meta

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 一个版本（原版 / Fabric / Forge）合并 inheritsFrom 链之后的结果。
 */
data class ResolvedVersion(
    val id: String,
    val baseId: String,
    val mainClass: String,
    val type: String,
    val libraries: List<Library>,
    val gameArguments: List<JsonElement>,
    val jvmArguments: List<JsonElement>,
    val minecraftArguments: String?,
    val assetIndex: AssetIndexRef?,
    val assets: String?,
    val javaVersion: JavaVersion,
    val downloads: VersionDownloads?,
    val logging: JsonObject?,
    val releaseTime: String,
    val chain: List<VersionDetail>
) {
    companion object {
        /** chain：子版本在前，父版本在后 */
        fun merge(chain: List<VersionDetail>): ResolvedVersion {
            require(chain.isNotEmpty()) { "版本链为空" }
            val child = chain.first()
            val base = chain.last { it.downloads?.client != null }.let {
                if (it.downloads?.client != null) it else chain.last()
            }

            // 库：子在前，父在后；同名只保留子版本
            val seen = HashSet<String>()
            val libraries = buildList {
                for (version in chain) {
                    for (library in version.libraries) {
                        if (seen.add(libraryKey(library.name))) add(library)
                    }
                }
            }

            val gameArgs = chain.flatMap { it.arguments?.game ?: emptyList() }
            val jvmArgs = chain.flatMap { it.arguments?.jvm ?: emptyList() }
            val legacyArgs = chain.firstNotNullOfOrNull { it.minecraftArguments?.ifBlank { null } }

            return ResolvedVersion(
                id = child.id,
                baseId = base.id,
                mainClass = chain.firstNotNullOfOrNull { it.mainClass.ifBlank { null } } ?: "",
                type = child.type,
                libraries = libraries,
                gameArguments = gameArgs,
                jvmArguments = jvmArgs,
                minecraftArguments = legacyArgs,
                assetIndex = chain.firstNotNullOfOrNull { it.assetIndex },
                assets = chain.firstNotNullOfOrNull { it.assets?.ifBlank { null } },
                javaVersion = chain.firstNotNullOfOrNull { it.javaVersion } ?: JavaVersion("jre-legacy", 8),
                downloads = base.downloads,
                logging = chain.firstNotNullOfOrNull { it.logging },
                releaseTime = child.releaseTime,
                chain = chain
            )
        }

        /**
         * 库去重键必须带分类器。
         * 之前只取 group:artifact:version，导致 org.lwjgl:lwjgl:3.3.3:natives-windows
         * 被 natives-linux 顶掉，natives 目录一个 dll 都没有，游戏启动直接崩
         * （Sodium: Failed to locate library: lwjgl.dll）。
         */
        private fun libraryKey(name: String): String = name
    }
}
