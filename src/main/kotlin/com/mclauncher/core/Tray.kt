package com.mclauncher.core

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionListener

/** 最小化的托盘图标：启动后隐藏窗口时用来把启动器叫回来。 */
object Tray {

    private var trayIcon: TrayIcon? = null

    fun isSupported(): Boolean = SystemTray.isSupported()

    fun install(onShow: () -> Unit, onExit: () -> Unit) {
        if (!isSupported() || trayIcon != null) return
        runCatching {
            val tray = SystemTray.getSystemTray()
            val icon = TrayIcon(Brand.icon(64), "NGML")
            icon.isImageAutoSize = true
            val menu = PopupMenu()
            val show = MenuItem("显示启动器")
            show.addActionListener(ActionListener { onShow() })
            val exit = MenuItem("退出 NGML")
            exit.addActionListener(ActionListener { onExit() })
            menu.add(show)
            menu.addSeparator()
            menu.add(exit)
            icon.popupMenu = menu
            icon.addActionListener(ActionListener { onShow() })
            tray.add(icon)
            trayIcon = icon
        }.onFailure { AppLog.error("托盘图标创建失败", it) }
    }

    fun notify(title: String, text: String) {
        runCatching { trayIcon?.displayMessage(title, text, TrayIcon.MessageType.NONE) }
    }

    fun remove() {
        runCatching { trayIcon?.let { SystemTray.getSystemTray().remove(it) } }
        trayIcon = null
    }
}
