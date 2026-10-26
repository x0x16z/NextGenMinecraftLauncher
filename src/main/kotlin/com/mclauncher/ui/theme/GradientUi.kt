package com.mclauncher.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mclauncher.core.GradientMath
import com.mclauncher.core.GradientPreset
import java.awt.image.BufferedImage

/**
 * 把预设渐变画到整个窗口上。
 * 所有预设都会先被插值成几十个色标，视觉上是平滑曲线而不是硬色带。
 */
@Composable
fun GradientFill(preset: GradientPreset, alpha: Float, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier) { drawGradient(this, preset, alpha) }
}

private fun drawGradient(scope: DrawScope, preset: GradientPreset, alpha: Float) {
    val stops = GradientMath.sample(preset, 64)
    val colors = stops.map { (_, argb) ->
        val c = Color(argb.toInt())
        c.copy(alpha = c.alpha * alpha.coerceIn(0f, 1f))
    }
    with(scope) {
        when (preset.mode) {
            GradientPreset.MODE_RADIAL -> {
                drawRect(
                    Brush.radialGradient(
                        colors = colors,
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = size.maxDimension * 0.95f
                    )
                )
            }
            GradientPreset.MODE_SWEEP -> {
                drawRect(
                    Brush.sweepGradient(
                        colors = colors,
                        center = Offset(size.width * 0.5f, size.height * 0.45f)
                    )
                )
            }
            GradientPreset.MODE_BLOBS -> {
                // 底色 + 若干柔和的曲线色团，像 iOS 那种流动壁纸
                drawRect(colors.first())
                val spots = listOf(
                    Triple(0.18f, 0.22f, 0.75f),
                    Triple(0.82f, 0.28f, 0.85f),
                    Triple(0.45f, 0.88f, 0.95f),
                    Triple(0.95f, 0.9f, 0.6f),
                    Triple(0.08f, 0.78f, 0.55f)
                )
                val palette = stops.map { Color(it.second.toInt()) }
                for (i in spots.indices) {
                    val (cx, cy, r) = spots[i]
                    val color = palette[(i + 1) % palette.size]
                    val radius = size.maxDimension * r * 0.8f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = color.alpha * alpha * 0.85f),
                                color.copy(alpha = 0f)
                            ),
                            center = Offset(size.width * cx, size.height * cy),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(size.width * cx, size.height * cy)
                    )
                }
            }
            else -> {
                val angle = Math.toRadians(preset.angle.toDouble())
                val dx = kotlin.math.cos(angle).toFloat()
                val dy = kotlin.math.sin(angle).toFloat()
                val start = Offset(size.width * (0.5f - dx * 0.5f), size.height * (0.5f - dy * 0.5f))
                val end = Offset(size.width * (0.5f + dx * 0.5f), size.height * (0.5f + dy * 0.5f))
                drawRect(Brush.linearGradient(colors = colors, start = start, end = end))
            }
        }
    }
}

/** 供面板采样用的位图版本 */
fun renderGradientImage(preset: GradientPreset, width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    val stops = GradientMath.sample(preset, 64)
    g.color = java.awt.Color(stops.first().second.toInt(), true)
    g.fillRect(0, 0, width, height)

    when (preset.mode) {
        GradientPreset.MODE_BLOBS -> {
            val spots = listOf(
                Triple(0.18f, 0.22f, 0.75f),
                Triple(0.82f, 0.28f, 0.85f),
                Triple(0.45f, 0.88f, 0.95f),
                Triple(0.95f, 0.9f, 0.6f)
            )
            for ((i, spec) in spots.withIndex()) {
                val (cx, cy, r) = spec
                val color = java.awt.Color(stops[(i + 1).coerceAtMost(stops.lastIndex)].second.toInt(), true)
                val radius = (maxOf(width, height) * r * 0.8f).toInt()
                val paint = java.awt.RadialGradientPaint(
                    java.awt.geom.Point2D.Float(width * cx, height * cy),
                    radius.toFloat(),
                    floatArrayOf(0f, 1f),
                    arrayOf(
                        java.awt.Color(color.red, color.green, color.blue, 190),
                        java.awt.Color(color.red, color.green, color.blue, 0)
                    )
                )
                g.paint = paint
                g.fillOval((width * cx - radius).toInt(), (height * cy - radius).toInt(), radius * 2, radius * 2)
            }
        }
        GradientPreset.MODE_RADIAL, GradientPreset.MODE_SWEEP -> {
            // 径向/锥形用多圈色环近似，够采样用
            val cx = width / 2f
            val cy = height / 2f
            val maxR = maxOf(width, height).toFloat()
            for (i in stops.indices.reversed()) {
                val (t, argb) = stops[i]
                g.color = java.awt.Color(argb.toInt(), true)
                val r = maxR * t
                g.fillOval((cx - r).toInt(), (cy - r).toInt(), (r * 2).toInt(), (r * 2).toInt())
            }
        }
        else -> {
            val rad = Math.toRadians(preset.angle.toDouble())
            val dx = kotlin.math.cos(rad).toFloat()
            val dy = kotlin.math.sin(rad).toFloat()
            val x1 = width * (0.5f - dx * 0.5f)
            val y1 = height * (0.5f - dy * 0.5f)
            val x2 = width * (0.5f + dx * 0.5f)
            val y2 = height * (0.5f + dy * 0.5f)
            val fractions = FloatArray(stops.size) { stops[it].first }
            val awtColors = Array(stops.size) { java.awt.Color(stops[it].second.toInt(), true) }
            g.paint = java.awt.LinearGradientPaint(
                java.awt.geom.Point2D.Float(x1, y1),
                java.awt.geom.Point2D.Float(x2, y2),
                fractions,
                awtColors
            )
            g.fillRect(0, 0, width, height)
        }
    }
    g.dispose()
    return image
}
