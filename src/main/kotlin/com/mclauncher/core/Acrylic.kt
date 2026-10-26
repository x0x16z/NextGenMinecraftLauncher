package com.mclauncher.core

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef

/**
 * 真正的背景模糊：让 DWM 在窗口后面做毛玻璃，而不是自己画一张假的模糊图。
 *
 * Windows 11 走 DWMWA_SYSTEMBACKDROP_TYPE（Acrylic / Mica），
 * Windows 10 回落到 SetWindowCompositionAttribute 的 ACCENT_ENABLE_ACRYLICBLURBEHIND。
 * 窗口必须是无边框 + 透明背景，客户端区域留空才能透出后面的桌面。
 */
object Acrylic {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWA_SYSTEMBACKDROP_TYPE = 38

    private const val DWMSBT_AUTO = 0
    private const val DWMSBT_NONE = 1
    private const val DWMSBT_MAINWINDOW = 2      // Mica
    private const val DWMSBT_TRANSIENTWINDOW = 3 // Acrylic
    private const val DWMSBT_TABBEDWINDOW = 4

    private const val WCA_ACCENT_POLICY = 19
    private const val ACCENT_ENABLE_BLURBEHIND = 3
    private const val ACCENT_ENABLE_ACRYLICBLURBEHIND = 4

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attr: Int, value: Pointer, size: Int): Int
        fun DwmExtendFrameIntoClientArea(hwnd: Pointer, margins: MARGINS): Int
    }

    @Structure.FieldOrder("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
    class MARGINS(
        @JvmField var cxLeftWidth: Int = -1,
        @JvmField var cxRightWidth: Int = -1,
        @JvmField var cyTopHeight: Int = -1,
        @JvmField var cyBottomHeight: Int = -1
    ) : Structure()

    @Structure.FieldOrder("AccentState", "AccentFlags", "GradientColor", "AnimationId")
    class ACCENT_POLICY(
        @JvmField var AccentState: Int = 0,
        @JvmField var AccentFlags: Int = 0,
        @JvmField var GradientColor: Int = 0,
        @JvmField var AnimationId: Int = 0
    ) : Structure()

    @Structure.FieldOrder("Attribute", "Data", "SizeOfData")
    class WINDOWCOMPOSITIONATTRIBDATA(
        @JvmField var Attribute: Int = 0,
        @JvmField var Data: Pointer? = null,
        @JvmField var SizeOfData: Int = 0
    ) : Structure()

    private interface User32Ex : Library {
        fun SetWindowCompositionAttribute(hwnd: Pointer, data: WINDOWCOMPOSITIONATTRIBDATA): Int
    }

    private val dwm: DwmApi? by lazy { runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull() }
    private val user32ex: User32Ex? by lazy { runCatching { Native.load("user32", User32Ex::class.java) }.getOrNull() }

    fun isSupported(): Boolean = Win32.isWindows && dwm != null

    /**
     * @param dark 深色模式下让 DWM 用暗色边框
     * @param tint 玻璃染色（ARGB），只有 Win10 的 acrylic 路径用得到
     */
    fun apply(hwnd: Long, dark: Boolean, tint: Int = 0x33202020): Boolean {
        if (!isSupported()) {
            lastError = "当前系统不支持 DWM 毛玻璃"
            return false
        }
        val ptr = Pointer(hwnd)
        return runCatching {
            // 圆角 + 深色标题栏
            setInt(ptr, DWMWA_WINDOW_CORNER_PREFERENCE, 2)
            setInt(ptr, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)

            var ok = false
            // Windows 11 22H2+：Acrylic 背景材质
            if (setInt(ptr, DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_TRANSIENTWINDOW) == 0) ok = true
            // 让窗口客户区也铺满这种材质
            runCatching { dwm?.DwmExtendFrameIntoClientArea(ptr, MARGINS(-1, -1, -1, -1)) }

            if (!ok) {
                // Windows 10 老接口
                val policy = ACCENT_POLICY(
                    AccentState = ACCENT_ENABLE_ACRYLICBLURBEHIND,
                    AccentFlags = 2,
                    GradientColor = tint,
                    AnimationId = 0
                )
                policy.write()
                val data = WINDOWCOMPOSITIONATTRIBDATA(
                    Attribute = WCA_ACCENT_POLICY,
                    Data = policy.pointer,
                    SizeOfData = policy.size()
                )
                val r = user32ex?.SetWindowCompositionAttribute(ptr, data) ?: 0
                ok = r != 0
            }
            enabled = ok
            if (!ok) lastError = "DWM 拒绝了毛玻璃请求"
            ok
        }.getOrElse {
            lastError = it.message
            enabled = false
            false
        }
    }

    /** 关掉系统材质，退回自绘壁纸 */
    fun clear(hwnd: Long) {
        if (!isSupported()) return
        runCatching {
            val ptr = Pointer(hwnd)
            setInt(ptr, DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_NONE)
            val policy = ACCENT_POLICY(AccentState = 0)
            policy.write()
            val data = WINDOWCOMPOSITIONATTRIBDATA(WCA_ACCENT_POLICY, policy.pointer, policy.size())
            user32ex?.SetWindowCompositionAttribute(ptr, data)
            enabled = false
        }
    }

    private fun setInt(hwnd: Pointer, attr: Int, value: Int): Int {
        val buf = com.sun.jna.Memory(4)
        buf.setInt(0, value)
        return dwm?.DwmSetWindowAttribute(hwnd, attr, buf, 4) ?: -1
    }

    /**
     * 从 AWT 窗口拿 HWND。优先反射 getHWnd，失败再按窗口标题找。
     */
    fun hwndOf(window: java.awt.Window): Long {
        runCatching {
            val peer = window.javaClass.getMethod("getPeer").invoke(window) ?: return 0L
            val method = peer.javaClass.getMethod("getHWnd")
            method.isAccessible = true
            return (method.invoke(peer) as Number).toLong()
        }
        runCatching {
            val user32 = com.sun.jna.platform.win32.User32.INSTANCE
            val title = (window as? java.awt.Frame)?.title ?: return 0L
            val hwnd = user32.FindWindow(null, title)
            if (hwnd != null && hwnd.pointer != null) return Pointer.nativeValue(hwnd.pointer)
        }
        return 0L
    }

    private var cachedHwnd: WinDef.HWND? = null
}
