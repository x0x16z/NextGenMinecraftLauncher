package com.mclauncher.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

/** 程序图标：一个等距视角的方块，和界面里的 logo 同一套几何。 */
object Brand {

    fun icon(size: Int = 128): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 圆角底板
        g.color = Color(0, 0, 0, 0)
        g.fillRect(0, 0, size, size)
        val bg = java.awt.geom.RoundRectangle2D.Float(size * 0.04f, size * 0.04f, size * 0.92f, size * 0.92f, size * 0.28f, size * 0.28f)
        g.paint = GradientPaint(0f, 0f, Color(0x1B, 0x22, 0x33), size.toFloat(), size.toFloat(), Color(0x0B, 0x10, 0x1C))
        g.fill(bg)

        val accent = Color(0x5B, 0x8C, 0xFF)
        val accent2 = Color(0x4F, 0xE3, 0xD0)

        val cx = size / 2.0
        val top = size * 0.14
        val midY = size * 0.44
        val bottom = size * 0.86
        val halfW = size * 0.36

        val topFace = Path2D.Double().apply {
            moveTo(cx, top)
            lineTo(cx + halfW, top + size * 0.15)
            lineTo(cx, midY)
            lineTo(cx - halfW, top + size * 0.15)
            closePath()
        }
        val leftFace = Path2D.Double().apply {
            moveTo(cx - halfW, top + size * 0.15)
            lineTo(cx, midY)
            lineTo(cx, bottom)
            lineTo(cx - halfW, bottom - size * 0.15)
            closePath()
        }
        val rightFace = Path2D.Double().apply {
            moveTo(cx + halfW, top + size * 0.15)
            lineTo(cx, midY)
            lineTo(cx, bottom)
            lineTo(cx + halfW, bottom - size * 0.15)
            closePath()
        }

        g.paint = GradientPaint(0f, top.toFloat(), accent2, size.toFloat(), midY.toFloat(), accent)
        g.fill(topFace)
        g.paint = GradientPaint(0f, midY.toFloat(), Color(0x3D, 0x6B, 0xE0), size.toFloat() * 0.6f, bottom.toFloat(), Color(0x24, 0x44, 0x9E))
        g.fill(leftFace)
        g.paint = GradientPaint(cx.toFloat(), midY.toFloat(), Color(0x36, 0xC8, 0xB8), size.toFloat(), bottom.toFloat(), Color(0x1E, 0x76, 0x74))
        g.fill(rightFace)

        g.color = Color(255, 255, 255, 90)
        g.stroke = BasicStroke(size * 0.02f)
        g.draw(topFace)

        g.dispose()
        return image
    }
}
