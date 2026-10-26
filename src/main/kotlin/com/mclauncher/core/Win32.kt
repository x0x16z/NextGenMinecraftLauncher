package com.mclauncher.core

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinDef

/** 少量 Windows 原生能力：全局鼠标位置（无漂移拖动窗口）、系统深色模式。 */
object Win32 {

    val isWindows: Boolean = Fs.isWindows()

    private val point = WinDef.POINT()

    fun cursorPos(): Pair<Int, Int>? {
        if (!isWindows) return null
        return runCatching {
            if (com.sun.jna.platform.win32.User32.INSTANCE.GetCursorPos(point)) {
                point.x to point.y
            } else null
        }.getOrNull()
    }

    private const val PERSONALIZE =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"

    /** Windows 构建号（从注册表读，比 os.version 靠谱） */
    val winBuild: Int by lazy {
        runCatching {
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "CurrentBuildNumber"
            ).toIntOrNull() ?: 0
        }.getOrDefault(0)
    }

    /** Windows 11 22H2（build 22621）起才有 DWMWA_SYSTEMBACKDROP_TYPE */
    fun supportsNativeAcrylic(): Boolean = isWindows && winBuild >= 22621 && Acrylic.isSupported()

    fun isSystemDark(): Boolean {
        if (!isWindows) return false
        return runCatching {
            Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, PERSONALIZE, "AppsUseLightTheme") == 0
        }.getOrDefault(false)
    }
}
