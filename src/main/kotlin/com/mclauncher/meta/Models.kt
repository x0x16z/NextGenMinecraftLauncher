package com.mclauncher.meta

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/* ----------------------------- Mojang 元数据 ----------------------------- */

@Serializable
data class VersionManifest(
    val latest: LatestVersions = LatestVersions(),
    val versions: List<ManifestEntry> = emptyList()
)

@Serializable
data class LatestVersions(val release: String = "", val snapshot: String = "")

@Serializable
data class ManifestEntry(
    val id: String,
    val type: String = "release",
    val url: String = "",
    val time: String = "",
    val releaseTime: String = "",
    val sha1: String = "",
    val complianceLevel: Int = 0
)

@Serializable
data class VersionDetail(
    val id: String = "",
    val type: String = "release",
    val mainClass: String = "",
    val inheritsFrom: String? = null,
    val arguments: Arguments? = null,
    val minecraftArguments: String? = null,
    val assetIndex: AssetIndexRef? = null,
    val assets: String? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val javaVersion: JavaVersion? = null,
    val releaseTime: String = "",
    val time: String = "",
    val logging: JsonObject? = null,
    val complianceLevel: Int = 0
)

@Serializable
data class Arguments(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList()
)

@Serializable
data class VersionDownloads(val client: Artifact? = null, val server: Artifact? = null)

@Serializable
data class Artifact(
    val path: String = "",
    val url: String = "",
    val sha1: String = "",
    val size: Long = 0
)

@Serializable
data class AssetIndexRef(
    val id: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val totalSize: Long = 0,
    val url: String = ""
)

@Serializable
data class AssetIndexDetail(
    val objects: Map<String, AssetObject> = emptyMap(),
    val virtual: Boolean = false,
    val mapToResources: Boolean = false
)

@Serializable
data class AssetObject(val hash: String = "", val size: Long = 0)

@Serializable
data class JavaVersion(val component: String = "", val majorVersion: Int = 8)

@Serializable
data class Library(
    val name: String = "",
    val downloads: LibraryDownloads? = null,
    val natives: Map<String, String> = emptyMap(),
    val rules: List<Rule> = emptyList(),
    val extract: Extract? = null,
    val url: String = ""
)

@Serializable
data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact> = emptyMap()
)

@Serializable
data class Rule(
    val action: String = "allow",
    val os: OsRule? = null,
    val features: Map<String, Boolean> = emptyMap()
)

@Serializable
data class OsRule(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null
)

@Serializable
data class Extract(val exclude: List<String> = emptyList())

/* ----------------------------- 规则求值 ----------------------------- */

object Rules {

    fun currentOsName(): String =
        if (System.getProperty("os.name").lowercase().contains("win")) "windows"
        else if (System.getProperty("os.name").lowercase().contains("mac")) "osx"
        else "linux"

    fun currentArch(): String = when (System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        "x86", "i386" -> "x86"
        else -> System.getProperty("os.arch")
    }

    fun allows(rules: List<Rule>, features: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, features)) allowed = rule.action == "allow"
        }
        return allowed
    }

    private fun matches(rule: Rule, features: Map<String, Boolean>): Boolean {
        val os = rule.os
        if (os != null) {
            val name = os.name
            if (name != null) {
                // Mojang 老数据里 windows 也会写成 win32
                val expected = if (name == "windows") listOf("windows", "win32") else listOf(name)
                if (currentOsName() !in expected) return false
            }
            val arch = os.arch
            if (arch != null && arch != currentArch()) return false
        }
        for ((key, value) in rule.features) {
            if ((features[key] ?: false) != value) return false
        }
        return true
    }
}

/** "net.minecraft:launchwrapper:1.12" -> "net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar" */
fun mavenPath(name: String): String {
    val parts = name.split(":")
    val group = parts[0].replace('.', '/')
    val artifact = parts.getOrElse(1) { "" }
    val version = parts.getOrElse(2) { "" }
    val classifier = parts.getOrNull(3)
    val ext = parts.getOrNull(4) ?: "jar"
    val file = if (classifier.isNullOrBlank()) "$artifact-$version.$ext" else "$artifact-$version-$classifier.$ext"
    return "$group/$artifact/$version/$file"
}
