package com.mclauncher.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** 全部本地目录统一在这里定义，方便移植。 */
object AppPaths {
    private val home: String = System.getProperty("user.home")
    private val appData: String = System.getenv("APPDATA") ?: home

    val root: Path = Paths.get(appData, "MCLauncher")
    val logs: Path = root.resolve("logs")
    val cache: Path = root.resolve("cache")
    val cacheVersions: Path = cache.resolve("versions")
    val cacheSkins: Path = cache.resolve("skins")
    val javaDir: Path = root.resolve("java")
    val langDir: Path = root.resolve("lang")
    val instancesDir: Path = root.resolve("instances")
    val configFile: Path = root.resolve("config.json")
    val accountsFile: Path = root.resolve("accounts.json")
    val instancesFile: Path = root.resolve("instances.json")

    /** 素材库 / versions / assets 的公共根目录，默认复用官方 .minecraft */
    val defaultMinecraftRoot: Path = Paths.get(appData, ".minecraft")

    fun init() {
        listOf(root, logs, cache, cacheVersions, cacheSkins, javaDir, instancesDir, langDir).forEach {
            runCatching { Files.createDirectories(it) }
        }
    }
}
