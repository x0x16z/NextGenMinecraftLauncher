package com.mclauncher.core

/**
 * 全局下载限速（令牌桶）。
 * limit = 0 表示不限速。下载器每读一块就调一次 throttle，超出配额就阻塞等待。
 */
object Bandwidth {

    @Volatile
    var limitBytesPerSec: Long = 0
        private set

    private val lock = Object()
    private var allowance: Double = 0.0
    private var lastNanos: Long = System.nanoTime()

    fun setLimitKbps(kbps: Int) {
        synchronized(lock) {
            limitBytesPerSec = if (kbps <= 0) 0 else kbps.toLong() * 1024
            allowance = 0.0
            lastNanos = System.nanoTime()
        }
    }

    fun throttle(bytes: Int) {
        val limit = limitBytesPerSec
        if (limit <= 0 || bytes <= 0) return
        synchronized(lock) {
            val now = System.nanoTime()
            val elapsed = (now - lastNanos) / 1_000_000_000.0
            lastNanos = now
            allowance = (allowance + elapsed * limit).coerceAtMost(limit * 2.0)
            allowance -= bytes
            if (allowance < 0) {
                val sleepMs = (-allowance / limit * 1000).toLong().coerceAtLeast(1)
                runCatching { Thread.sleep(sleepMs) }
                allowance = 0.0
            }
        }
    }
}
