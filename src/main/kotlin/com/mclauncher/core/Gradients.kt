package com.mclauncher.core

import kotlinx.serialization.Serializable

/* ----------------------------- 渐变预设 ----------------------------- */

@Serializable
data class GradientPreset(
    val id: String,
    val name: String,
    val colors: List<Long>,        // ARGB
    val positions: List<Float> = emptyList(), // 空 = 均匀分布
    val mode: String = MODE_LINEAR, // linear | radial | sweep | blobs
    val angle: Float = 135f,
    /** 0..1，越大越"糊"，用来把硬边颜色过渡成平滑曲线 */
    val smooth: Float = 0.6f,
    /** 叠在毛玻璃上的不透明度 */
    val alpha: Float = 0.42f
) {
    companion object {
        const val MODE_LINEAR = "linear"
        const val MODE_RADIAL = "radial"
        const val MODE_SWEEP = "sweep"
        const val MODE_BLOBS = "blobs"

        val modes = listOf(
            MODE_LINEAR to "线性",
            MODE_RADIAL to "径向",
            MODE_SWEEP to "锥形",
            MODE_BLOBS to "曲线团"
        )

        fun defaultPresets(): List<GradientPreset> = listOf(
            GradientPreset(
                id = "aurora",
                name = "极光",
                colors = listOf(0xFF111C3D, 0xFF2A5C9A, 0xFF3FC6BD, 0xFFB6F0E4),
                mode = MODE_BLOBS,
                alpha = 0.55f
            ),
            GradientPreset(
                id = "sakura",
                name = "樱雾",
                colors = listOf(0xFF2A1220, 0xFF8A3B5A, 0xFFD98AA0, 0xFFF6D9C9),
                mode = MODE_LINEAR,
                angle = 120f,
                alpha = 0.45f
            ),
            GradientPreset(
                id = "forest",
                name = "深林",
                colors = listOf(0xFF0C2318, 0xFF1E5C3E, 0xFF3FA377, 0xFFC8F0D8),
                mode = MODE_BLOBS,
                alpha = 0.5f
            ),
            GradientPreset(
                id = "dusk",
                name = "暮色",
                colors = listOf(0xFF1A1030, 0xFF4B2A63, 0xFFC85A6A, 0xFFF2B26B),
                mode = MODE_SWEEP,
                alpha = 0.5f
            ),
            GradientPreset(
                id = "graphite",
                name = "石墨",
                colors = listOf(0xFF101114, 0xFF2A2C31, 0xFF55585F),
                mode = MODE_LINEAR,
                angle = 160f,
                alpha = 0.6f
            )
        )
    }
}

/**
 * 把 stops 插值成很多个点，Compose 的 Brush 就会呈现平滑曲线而不是生硬的色带。
 * 用的是 Catmull-Rom 风格的三次平滑（简化版）。
 */
object GradientMath {

    /** 返回 (0f..1f -> ARGB) 的采样，count 个点 */
    fun sample(preset: GradientPreset, count: Int = 48): List<Pair<Float, Long>> {
        val colors = preset.colors
        if (colors.isEmpty()) return listOf(0f to 0xFF000000)
        if (colors.size == 1) return listOf(0f to colors[0], 1f to colors[0])

        val stops = colors.mapIndexed { index, color ->
            val pos = when {
                preset.positions.size == colors.size -> preset.positions[index].coerceIn(0f, 1f)
                else -> index.toFloat() / (colors.size - 1)
            }
            pos to color
        }.sortedBy { it.first }

        val smoothness = preset.smooth.coerceIn(0f, 1f)
        val out = ArrayList<Pair<Float, Long>>(count)
        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1)
            out += t to interpolate(stops, t, smoothness)
        }
        return out
    }

    private fun interpolate(stops: List<Pair<Float, Long>>, t: Float, smoothness: Float): Long {
        if (t <= stops.first().first) return stops.first().second
        if (t >= stops.last().first) return stops.last().second
        for (i in 0 until stops.size - 1) {
            val (p0, c0) = stops[i]
            val (p1, c1) = stops[i + 1]
            if (t in p0..p1) {
                val span = (p1 - p0).takeIf { it > 1e-6f } ?: 1e-6f
                var f = (t - p0) / span
                // 三次平滑，让颜色在两端"减速"，视觉上像曲线而不是直线
                val power = 1f + smoothness * 2f
                f = if (f < 0.5f) 0.5f * Math.pow((2 * f).toDouble(), power.toDouble()).toFloat()
                else 1f - 0.5f * Math.pow((2 * (1 - f)).toDouble(), power.toDouble()).toFloat()
                return lerpColor(c0, c1, f)
            }
        }
        return stops.last().second
    }

    fun lerpColor(from: Long, to: Long, f: Float): Long {
        val a0 = ((from shr 24) and 0xFF).toInt()
        val r0 = ((from shr 16) and 0xFF).toInt()
        val g0 = ((from shr 8) and 0xFF).toInt()
        val b0 = (from and 0xFF).toInt()
        val a1 = ((to shr 24) and 0xFF).toInt()
        val r1 = ((to shr 16) and 0xFF).toInt()
        val g1 = ((to shr 8) and 0xFF).toInt()
        val b1 = (to and 0xFF).toInt()
        val a = (a0 + (a1 - a0) * f).toInt().coerceIn(0, 255)
        val r = (r0 + (r1 - r0) * f).toInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * f).toInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * f).toInt().coerceIn(0, 255)
        return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun randomPreset(index: Int): GradientPreset {
        val palettes = listOf(
            listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364),
            listOf(0xFF42275A, 0xFF734B6D, 0xFFB06AB3),
            listOf(0xFF1D976C, 0xFF93F9B9, 0xFFE8FFF4),
            listOf(0xFF232526, 0xFF414345, 0xFF9A8C98),
            listOf(0xFFFC466B, 0xFF3F5EFB, 0xFF8E9EAB)
        )
        val colors = palettes[index % palettes.size]
        val modes = listOf(GradientPreset.MODE_LINEAR, GradientPreset.MODE_RADIAL, GradientPreset.MODE_BLOBS)
        return GradientPreset(
            id = "custom-" + System.currentTimeMillis().toString(36),
            name = "我的渐变 ${index + 1}",
            colors = colors,
            mode = modes[index % modes.size],
            alpha = 0.45f
        )
    }
}
