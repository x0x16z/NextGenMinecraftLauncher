package com.mclauncher.core

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

/** 删除到回收站（Windows Shell 的 FO_DELETE + FOF_ALLOWUNDO）。 */
object RecycleBin {

    private const val FO_DELETE = 3
    private const val FOF_SILENT = 0x0004
    private const val FOF_NOCONFIRMATION = 0x0010
    private const val FOF_ALLOWUNDO = 0x0040
    private const val FOF_NOERRORUI = 0x0400

    @Structure.FieldOrder("hwnd", "wFunc", "pFrom", "pTo", "fFlags", "fAnyOperationsAborted", "hNameMappings", "lpszProgressTitle")
    class SHFILEOPSTRUCTW : Structure() {
        @JvmField var hwnd: Pointer? = null
        @JvmField var wFunc: Int = 0
        @JvmField var pFrom: Pointer? = null
        @JvmField var pTo: Pointer? = null
        @JvmField var fFlags: Short = 0
        @JvmField var fAnyOperationsAborted: Int = 0
        @JvmField var hNameMappings: Pointer? = null
        @JvmField var lpszProgressTitle: Pointer? = null
    }

    private interface Shell32 : Library {
        fun SHFileOperationW(op: SHFILEOPSTRUCTW): Int
    }

    private val shell32: Shell32? by lazy {
        if (!Win32.isWindows) null else runCatching { Native.load("shell32", Shell32::class.java) }.getOrNull()
    }

    /** 成功返回 true；失败时调用方可以退回普通删除 */
    fun delete(path: java.nio.file.Path): Boolean {
        if (!Win32.isWindows || shell32 == null) return false
        if (!java.nio.file.Files.exists(path)) return false
        return runCatching {
            // pFrom 需要 double-null 结尾的宽字符串
            val text = path.toAbsolutePath().toString() + "\u0000"
            val memory = com.sun.jna.Memory((text.length + 2) * 2L)
            memory.setWideString(0, text)
            val op = SHFILEOPSTRUCTW().apply {
                hwnd = null
                wFunc = FO_DELETE
                pFrom = memory
                pTo = null
                fFlags = (FOF_ALLOWUNDO or FOF_NOCONFIRMATION or FOF_SILENT or FOF_NOERRORUI).toShort()
                fAnyOperationsAborted = 0
            }
            op.write()
            val code = shell32!!.SHFileOperationW(op)
            code == 0 && op.fAnyOperationsAborted == 0
        }.getOrDefault(false)
    }
}
