package com.mclauncher.core

import java.lang.management.ManagementFactory

/** 设备信息：用来把"运行内存"的上限跟着机器走。 */
object SysInfo {

    private const val MB = 1024L * 1024L

    /** 物理内存（MB） */
    val totalRamMb: Long by lazy {
        runCatching {
            val bean = ManagementFactory.getOperatingSystemMXBean()
            if (bean is com.sun.management.OperatingSystemMXBean) bean.totalMemorySize / MB else 0L
        }.getOrDefault(0L).takeIf { it > 0 } ?: 8192L
    }

    /**
     * 给游戏分配的上限：物理内存的 75%，按 512MB 对齐。
     * 下限 4096，上限 32768。
     */
    val maxMemoryMb: Int by lazy {
        val usable = (totalRamMb * 3 / 4).toInt()
        // 32 GB 封顶：再往上 MC 也用不满，反而容易让 GC 变慢
        (usable / 512 * 512).coerceIn(4096, 32768)
    }

    /** 默认值：物理内存的一半，但夹在 [2048, 8192]，MV 原版/普通整合包够用 */
    val defaultMemoryMb: Int by lazy {
        val half = (totalRamMb / 2 * 5 / 8).toInt()
        (half / 512 * 512).coerceIn(2048, minOf(maxMemoryMb, 8192))
    }

    /** 滑块步长：512MB 一档 */
    const val MEMORY_STEP_MB = 512

    fun stepsForMax(): Int {
        val span = maxMemoryMb - 1024
        return (span / MEMORY_STEP_MB - 1).coerceAtLeast(0)
    }

    fun clampMemory(value: Int): Int = value.coerceIn(1024, maxMemoryMb)

    /** 人类可读：总内存 32 GB */
    fun totalRamText(): String {
        val gb = totalRamMb / 1024.0
        return if (gb >= 10) "${gb.toInt()} GB" else String.format("%.1f GB", gb)
    }
}
