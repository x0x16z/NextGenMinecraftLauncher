package com.mclauncher.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.sin

/**
 * 纯灰度的几何图案：只随深/浅色变化明暗，配合毛玻璃会很有"雾"的质感。
 * 全部程序化绘制，不依赖任何图片素材。
 *
 * @param transparent true = 不画底色，只留图形（用作"现代"模式下叠在毛玻璃上的主题层）
 */
object Patterns {

    data class Preset(val id: String, val name: String, val description: String)

    val all = listOf(
        Preset("fog_city", "雾中城市", "灰色天际线 + 雾气分层"),
        Preset("mountain", "远山", "层叠山脊"),
        Preset("waves", "灰潮", "柔和的正弦波纹"),
        Preset("grid", "平面网格", "透视网格，像老式建模软件"),
        Preset("bokeh", "光斑", "虚焦灰点"),
        Preset("circuit", "线路", "正交线路与节点")
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    fun render(id: String, width: Int, height: Int, dark: Boolean, transparent: Boolean = false): BufferedImage {
        val w = width.coerceAtLeast(64)
        val h = height.coerceAtLeast(64)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        if (!transparent) {
            val top = if (dark) Color(0x15, 0x1A, 0x21) else Color(0xEE, 0xF1, 0xF5)
            val bottom = if (dark) Color(0x2A, 0x31, 0x3A) else Color(0xC9, 0xCF, 0xD8)
            g.paint = GradientPaint(0f, 0f, top, 0f, h.toFloat(), bottom)
            g.fillRect(0, 0, w, h)
        }

        when (id) {
            "fog_city" -> city(g, w, h, dark)
            "mountain" -> mountain(g, w, h, dark)
            "waves" -> waves(g, w, h, dark)
            "grid" -> grid(g, w, h, dark)
            "bokeh" -> bokeh(g, w, h, dark)
            "circuit" -> circuit(g, w, h, dark)
            else -> city(g, w, h, dark)
        }

        if (!transparent) {
            g.paint = GradientPaint(
                0f, 0f, if (dark) Color(0, 0, 0, 60) else Color(255, 255, 255, 50),
                0f, h.toFloat(), Color(0, 0, 0, 0)
            )
            g.fillRect(0, 0, w, h)
        }
        g.dispose()
        return img
    }

    private fun gray(dark: Boolean, brightness: Int, alpha: Int): Color =
        if (dark) Color(brightness, brightness, brightness, alpha)
        else Color(255 - brightness, 255 - brightness, 255 - brightness, alpha)

    private fun city(g: java.awt.Graphics2D, w: Int, h: Int, dark: Boolean) {
        val moonR = (minOf(w, h) * 0.16f)
        g.color = Color(255, 255, 255, if (dark) 46 else 90)
        g.fill(Ellipse2D.Float(w * 0.72f - moonR / 2, h * 0.2f - moonR / 2, moonR, moonR))

        val layers = listOf(
            Triple(0.92f, if (dark) 78 else 120, 0.60f),
            Triple(0.78f, if (dark) 58 else 150, 0.48f),
            Triple(0.66f, if (dark) 42 else 175, 0.36f)
        )
        var seed = 7L
        fun rnd(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % 1000).toFloat() / 1000f
        }

        for ((baseline, brightness, alphaScale) in layers) {
            val baseY = h * baseline
            var x = -20
            while (x < w + 20) {
                val bw = (w * (0.04f + rnd() * 0.06f)).toInt()
                val bh = (h * (0.12f + rnd() * 0.30f)).toInt()
                g.color = gray(dark, brightness, (255 * alphaScale).toInt().coerceIn(10, 255))
                g.fillRect(x, (baseY - bh).toInt(), bw, bh)
                g.color = Color(255, 255, 255, if (dark) 42 else 70)
                var wy = (baseY - bh + 10).toInt()
                while (wy < baseY - 8) {
                    var wx = x + 6
                    while (wx < x + bw - 6) {
                        if (rnd() > 0.55f) g.fillRect(wx, wy, 3, 4)
                        wx += 9
                    }
                    wy += 13
                }
                x += bw + (w * 0.004f).toInt().coerceAtLeast(3)
            }
        }

        for (i in 0 until 3) {
            val y = h * (0.55f + i * 0.14f)
            g.paint = GradientPaint(
                0f, y - h * 0.08f, Color(255, 255, 255, 0),
                0f, y + h * 0.08f, Color(255, 255, 255, if (dark) 26 else 70)
            )
            g.fillRect(0, (y - h * 0.08f).toInt(), w, (h * 0.16f).toInt())
        }
    }

    private fun mountain(g: java.awt.Graphics2D, w: Int, h: Int, dark: Boolean) {
        val moonR = minOf(w, h) * 0.13f
        g.color = Color(255, 255, 255, if (dark) 38 else 110)
        g.fill(Ellipse2D.Float(w * 0.24f - moonR / 2, h * 0.16f - moonR / 2, moonR, moonR))

        val layers = listOf(
            Triple(0.58f, if (dark) 128 else 110, 0.55f),
            Triple(0.72f, if (dark) 96 else 140, 0.50f),
            Triple(0.88f, if (dark) 64 else 170, 0.55f)
        )
        for ((baseline, brightness, alphaScale) in layers) {
            val path = Path2D.Double()
            path.moveTo(0.0, h.toDouble())
            path.lineTo(0.0, h.toDouble() * baseline)
            val peaks = 6
            for (i in 0 until peaks) {
                val x0 = w * i / peaks.toDouble()
                val x1 = w * (i + 0.5) / peaks
                val x2 = w * (i + 1.0) / peaks
                val peakY = h.toDouble() * baseline - h.toDouble() * (0.10 + ((i * 37) % 11) / 90.0)
                path.curveTo(x0 + (x1 - x0) * 0.35, peakY, x1 - (x1 - x0) * 0.25, peakY, x1, peakY + h * 0.015)
                path.curveTo(x1 + (x2 - x1) * 0.25, peakY + h * 0.015, x2 - (x2 - x1) * 0.35, h.toDouble() * baseline, x2, h.toDouble() * baseline)
            }
            path.lineTo(w.toDouble(), h.toDouble())
            path.closePath()
            g.color = gray(dark, brightness, (255 * alphaScale).toInt())
            g.fill(path)
            g.color = Color(255, 255, 255, if (dark) 30 else 60)
            g.stroke = BasicStroke(2f)
            g.draw(path)
        }
    }

    private fun waves(g: java.awt.Graphics2D, w: Int, h: Int, dark: Boolean) {
        for (i in 0 until 7) {
            val baseY = h * (0.35f + i * 0.08f)
            val amp = h * (0.02f + i * 0.006f)
            val path = Path2D.Double()
            path.moveTo(0.0, baseY.toDouble())
            var x = 0
            while (x <= w) {
                val y = baseY + sin((x / w.toDouble()) * Math.PI * (2.2 + i * 0.35) + i) * amp
                path.lineTo(x.toDouble(), y)
                x += 6
            }
            g.color = gray(dark, 150 + i * 10, if (dark) 170 else 110)
            g.stroke = BasicStroke(1.4f + i * 0.6f)
            g.draw(path)
        }
    }

    private fun grid(g: java.awt.Graphics2D, w: Int, h: Int, dark: Boolean) {
        val horizon = h * 0.42f
        g.color = gray(dark, 150, if (dark) 130 else 90)
        g.stroke = BasicStroke(1.4f)
        var i = 0
        while (i < 16) {
            val t = i / 16f
            val y = horizon + (h - horizon) * t * t
            g.drawLine(0, y.toInt(), w, y.toInt())
            i++
        }
        val vpX = w / 2f
        var k = -12
        while (k <= 12) {
            val xBottom = w / 2f + k * (w / 14f)
            g.drawLine(vpX.toInt(), horizon.toInt(), xBottom.toInt(), h)
            k++
        }
        g.color = gray(dark, 190, if (dark) 180 else 120)
        g.stroke = BasicStroke(2.2f)
        g.drawLine(0, horizon.toInt(), w, horizon.toInt())
    }

    private fun bokeh(g: java.awt.Graphics2D, w: Int, h: Int, dark: Boolean) {
        var seed = 31L
        fun rnd(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % 1000).toFloat() / 1000f
        }
        repeat(26) {
            val r = minOf(w, h) * (0.03f + rnd() * 0.11f)
            val cx = w * rnd()
            val cy = h * rnd()
            val alpha = if (dark) (30 + rnd() * 60).toInt() else (30 + rnd() * 50).toInt()
            g.paint = java.awt.RadialGradientPaint(
                java.awt.geom.Point2D.Float(cx, cy),
                r,
                floatArrayOf(0f, 0.65f, 1f),
                arrayOf(
                    Color(255, 255, 255, alpha),
                    Color(255, 255, 255, alpha / 2),
                    Color(255, 255, 255, 0)
                )
            )
            g.fill(Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2))
        }
    }

    private fun circuit(g: java.awt.Graphics2D, w: Int, h: Int, dark: Boolean) {
        var seed = 99L
        fun rnd(): Float {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) % 1000).toFloat() / 1000f
        }
        g.stroke = BasicStroke(1.6f)
        repeat(26) {
            var x = (w * rnd()).toInt()
            var y = (h * rnd()).toInt()
            val path = Path2D.Double()
            path.moveTo(x.toDouble(), y.toDouble())
            repeat(4) {
                if (rnd() > 0.5f) x += ((w * 0.08f) * (if (rnd() > 0.5f) 1 else -1)).toInt()
                else y += ((h * 0.08f) * (if (rnd() > 0.5f) 1 else -1)).toInt()
                path.lineTo(x.toDouble(), y.toDouble())
            }
            g.color = gray(dark, 150, if (dark) 150 else 100)
            g.draw(path)
            g.color = gray(dark, 220, if (dark) 190 else 140)
            g.fill(Ellipse2D.Float(x - 3.5f, y - 3.5f, 7f, 7f))
        }
    }
}
