package com.mclauncher.core

import java.awt.Color
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 壁纸与模糊。壁纸先等比缩放填充，再做高斯近似模糊，
 * 之后 UI 用同一张模糊图做“真·背景模糊”的毛玻璃卡片。
 */
object Wallpaper {

    data class Preset(val id: String, val name: String, val colors: List<Color>)

    val presets = listOf(
        Preset("aurora", "极光", listOf(Color(0x0B1026), Color(0x1B3A6B), Color(0x2FA8A0), Color(0x8AD6C8))),
        Preset("sakura", "樱雾", listOf(Color(0x2A1220), Color(0x8A3B5A), Color(0xD98AA0), Color(0xF6D9C9))),
        Preset("forest", "深林", listOf(Color(0x07160F), Color(0x14432E), Color(0x2E7D5B), Color(0xB6E3C4))),
        Preset("dusk", "暮色", listOf(Color(0x1A1030), Color(0x4B2A63), Color(0xC85A6A), Color(0xF2B26B))),
        Preset("mono", "石墨", listOf(Color(0x101114), Color(0x2A2C31), Color(0x55585F), Color(0x9BA0A8)))
    )

    /** kind: 预设 id，或 custom:<绝对路径> */
    /** 读本地图片原图（不裁切、不缩放），给"拉伸模式"用 */
    fun readFile(path: String): BufferedImage? = runCatching {
        val file = File(path)
        if (file.exists()) ImageIO.read(file) else null
    }.getOrNull()

    fun load(kind: String, width: Int, height: Int): BufferedImage {
        val w = width.coerceAtLeast(64)
        val h = height.coerceAtLeast(64)
        if (kind.startsWith("custom:")) {
            val file = File(kind.removePrefix("custom:"))
            if (file.exists()) {
                runCatching { ImageIO.read(file) }.getOrNull()?.let { return cover(it, w, h) }
            }
        }
        val preset = presets.firstOrNull { it.id == kind } ?: presets.first()
        return generate(preset, w, h)
    }

    fun cover(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val scale = maxOf(w.toDouble() / src.width, h.toDouble() / src.height)
        val dw = (src.width * scale).toInt()
        val dh = (src.height * scale).toInt()
        g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
        g.dispose()
        return out
    }

    private fun generate(preset: Preset, w: Int, h: Int): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = preset.colors.first()
        g.fillRect(0, 0, w, h)

        // 几个大色斑，做出 iOS 壁纸那种柔和的流动感
        val blobs = listOf(
            Triple(0.18f, 0.20f, 0.62f) to preset.colors[1],
            Triple(0.82f, 0.26f, 0.70f) to preset.colors[2],
            Triple(0.45f, 0.86f, 0.85f) to preset.colors[3],
            Triple(0.95f, 0.92f, 0.55f) to preset.colors[2]
        )
        for ((spec, color) in blobs) {
            val (cx, cy, r) = spec
            val radius = (maxOf(w, h) * r).toFloat()
            val paint = RadialGradientPaint(
                Point2D.Float(w * cx, h * cy),
                radius,
                floatArrayOf(0f, 1f),
                arrayOf(
                    Color(color.red, color.green, color.blue, 205),
                    Color(color.red, color.green, color.blue, 0)
                )
            )
            g.paint = paint
            g.fillOval((w * cx - radius).toInt(), (h * cy - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
        }

        // 顶部压暗，保证标题栏文字可读
        val shade = java.awt.GradientPaint(
            0f, 0f, Color(0, 0, 0, 55),
            0f, h.toFloat(), Color(0, 0, 0, 0)
        )
        g.paint = shade
        g.fillRect(0, 0, w, h)
        g.dispose()
        return img
    }

    /**
     * 高斯近似模糊：先缩小到 1/scale，再在缩略图上做 3 次盒式模糊。
     * 1200x800 的图在 10ms 量级，完全够用。
     */
    fun blur(src: BufferedImage, scale: Int, radius: Int): BufferedImage {
        val s = scale.coerceIn(1, 8)
        val w = (src.width / s).coerceAtLeast(8)
        val h = (src.height / s).coerceAtLeast(8)
        val small = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = small.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()

        var pixels = IntArray(w * h)
        small.getRGB(0, 0, w, h, pixels, 0, w)
        val r = (radius / s).coerceIn(1, 60)
        repeat(3) {
            pixels = boxBlurH(pixels, w, h, r)
            pixels = boxBlurV(pixels, w, h, r)
        }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, w, h, pixels, 0, w)
        return out
    }

    private fun boxBlurH(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val dst = IntArray(src.size)
        val div = r * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var ri = 0; var gi = 0; var bi = 0
            // 初始窗口
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                a += (c ushr 24) and 0xFF; ri += (c shr 16) and 0xFF
                gi += (c shr 8) and 0xFF; bi += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((a / div) shl 24) or ((ri / div) shl 16) or ((gi / div) shl 8) or (bi / div)
                val outIdx = row + (x - r).coerceIn(0, w - 1)
                val inIdx = row + (x + r + 1).coerceIn(0, w - 1)
                val co = src[outIdx]; val ci = src[inIdx]
                a += ((ci ushr 24) and 0xFF) - ((co ushr 24) and 0xFF)
                ri += ((ci shr 16) and 0xFF) - ((co shr 16) and 0xFF)
                gi += ((ci shr 8) and 0xFF) - ((co shr 8) and 0xFF)
                bi += (ci and 0xFF) - (co and 0xFF)
            }
        }
        return dst
    }

    private fun boxBlurV(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val dst = IntArray(src.size)
        val div = r * 2 + 1
        for (x in 0 until w) {
            var a = 0; var ri = 0; var gi = 0; var bi = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                a += (c ushr 24) and 0xFF; ri += (c shr 16) and 0xFF
                gi += (c shr 8) and 0xFF; bi += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((a / div) shl 24) or ((ri / div) shl 16) or ((gi / div) shl 8) or (bi / div)
                val co = src[(y - r).coerceIn(0, h - 1) * w + x]
                val ci = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += ((ci ushr 24) and 0xFF) - ((co ushr 24) and 0xFF)
                ri += ((ci shr 16) and 0xFF) - ((co shr 16) and 0xFF)
                gi += ((ci shr 8) and 0xFF) - ((co shr 8) and 0xFF)
                bi += (ci and 0xFF) - (co and 0xFF)
            }
        }
        return dst
    }

    fun dim(src: BufferedImage, ratio: Float): BufferedImage {
        if (ratio <= 0f) return src
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics() as Graphics2D
        g.drawImage(src, 0, 0, null)
        g.color = Color(0, 0, 0, (255 * ratio).toInt().coerceIn(0, 255))
        g.fillRect(0, 0, src.width, src.height)
        g.dispose()
        return out
    }
}
    /** 按指定方式把图片铺到 w×h：crop 裁切填满 / fit 完整显示 / stretch 拉伸 / tile 平铺 */
    fun fit(src: BufferedImage, w: Int, h: Int, mode: String): BufferedImage {
        val out = BufferedImage(w.coerceAtLeast(8), h.coerceAtLeast(8), BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        when (mode) {
            "fit" -> {
                val scale = minOf(w.toDouble() / src.width, h.toDouble() / src.height)
                val dw = (src.width * scale).toInt()
                val dh = (src.height * scale).toInt()
                g.color = Color(0x0D0F14)
                g.fillRect(0, 0, w, h)
                g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
            }
            "stretch" -> g.drawImage(src, 0, 0, w, h, null)
            "tile" -> {
                var y = 0
                while (y < h) {
                    var x = 0
                    while (x < w) {
                        g.drawImage(src, x, y, null)
                        x += src.width
                    }
                    y += src.height
                }
            }
            else -> {
                val scale = maxOf(w.toDouble() / src.width, h.toDouble() / src.height)
                val dw = (src.width * scale).toInt()
                val dh = (src.height * scale).toInt()
                g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null)
            }
        }
        g.dispose()
        return out
    }