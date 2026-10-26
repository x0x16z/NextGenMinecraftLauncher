package com.mclauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

/**
 * "千万别点"彩蛋：一大群小强在窗口里乱窜，点任意处赶走它们。
 * 全部用 Canvas 画（不依赖 emoji 字体），位置更新只触发绘制、不触发重组。
 */
@Composable
fun RoachOverlay(count: Int = 48, onClose: () -> Unit) {
    val roaches = remember { MutableList(count) { Roach.random() } }
    val tick = remember { mutableIntStateOf(0) }
    var started by remember { mutableFloatStateOf(0f) }
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = 0L
        val life = if (System.getenv("NGML_ROACH_LONG") == "1") 120f else 8f
        while (true) {
            val now = withFrameNanos { it }
            if (last == 0L) last = now
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            elapsed += dt
            started += dt
            roaches.forEach { it.step(dt) }
            tick.intValue++
            if (started > life) onClose()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Text(
            "跑啊！点一下赶走它们",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                onClose()
                                return@awaitPointerEventScope
                            }
                        }
                    }
                }
        ) {
            // 读一下 tick：位置变化只让绘制阶段失效，不触发重组
            if (tick.intValue >= 0) {
                roaches.forEach { roach ->
                    translate(size.width * roach.x, size.height * roach.y) {
                        rotate(roach.angle + 90f) {
                            drawOne(roach, wiggle = sin(elapsed * 24f + roach.phase))
                        }
                    }
                }
            }
        }
    }
}

/** 一只独立移动的小强 */
private class Roach {
    var x = 0.5f
    var y = 0.5f
    var angle = 0f
    var tx = 0.5f
    var ty = 0.5f
    var speed = 0.2f
    var phase = 0f
    var scale = 1f
    var tone = 0

    fun step(dt: Float) {
        val dx = tx - x
        val dy = ty - y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < 0.02f) {
            tx = 0.05f + Random.nextFloat() * 0.9f
            ty = 0.08f + Random.nextFloat() * 0.86f
        } else {
            // 小强会时不时"急停再冲"，加一点随机抖动
            val jitter = 0.75f + Random.nextFloat() * 0.6f
            x += dx / dist * speed * jitter * dt
            y += dy / dist * speed * jitter * dt
            angle = (kotlin.math.atan2(dy.toDouble(), dx.toDouble()) * 180.0 / Math.PI).toFloat()
        }
    }

    companion object {
        fun random(): Roach = Roach().apply {
            x = Random.nextFloat()
            y = Random.nextFloat()
            tx = Random.nextFloat()
            ty = Random.nextFloat()
            speed = 0.12f + Random.nextFloat() * 0.32f
            phase = Random.nextFloat() * 6.28f
            scale = 0.55f + Random.nextFloat() * 0.85f
            tone = Random.nextInt(3)
        }
    }
}

private val TONES = listOf(
    Color(0xFF2A1A12) to Color(0xFF4A2C1C),
    Color(0xFF17120F) to Color(0xFF3A2A22),
    Color(0xFF2E1A14) to Color(0xFF5A3320)
)

/** 画一只，头朝 -Y */
private fun DrawScope.drawOne(roach: Roach, wiggle: Float) {
    val s = 46.dp.toPx() * roach.scale
    val (body, shell) = TONES[roach.tone % TONES.size]
    val legStroke = Stroke(width = (2.2f * roach.scale).dp.toPx())

    repeat(3) { i ->
        val ly = -s * 0.35f + i * s * 0.35f
        val sway = wiggle * s * 0.07f * (if (i % 2 == 0) 1 else -1)
        val left = Path().apply {
            moveTo(-s * 0.30f, ly)
            lineTo(-s * 0.85f, ly + s * 0.10f + sway)
            lineTo(-s * 1.05f, ly + s * 0.45f + sway)
        }
        val right = Path().apply {
            moveTo(s * 0.30f, ly)
            lineTo(s * 0.85f, ly + s * 0.10f - sway)
            lineTo(s * 1.05f, ly + s * 0.45f - sway)
        }
        drawPath(left, body, style = legStroke)
        drawPath(right, body, style = legStroke)
    }

    drawOval(
        color = body,
        topLeft = Offset(-s * 0.42f, -s * 0.55f),
        size = Size(s * 0.84f, s * 1.35f)
    )
    drawOval(
        color = shell,
        topLeft = Offset(-s * 0.34f, -s * 0.42f),
        size = Size(s * 0.68f, s * 0.95f)
    )
    drawLine(
        Color(0x66FFFFFF),
        start = Offset(0f, -s * 0.4f),
        end = Offset(0f, s * 0.5f),
        strokeWidth = 1.6.dp.toPx() * roach.scale
    )
    drawOval(
        color = body,
        topLeft = Offset(-s * 0.22f, -s * 0.72f),
        size = Size(s * 0.44f, s * 0.30f)
    )
    val antenna = Path().apply {
        moveTo(-s * 0.12f, -s * 0.70f)
        quadraticTo(-s * 0.45f + wiggle * s * 0.05f, -s * 1.15f, -s * 0.70f, -s * 1.30f)
    }
    val antenna2 = Path().apply {
        moveTo(s * 0.12f, -s * 0.70f)
        quadraticTo(s * 0.45f - wiggle * s * 0.05f, -s * 1.15f, s * 0.70f, -s * 1.30f)
    }
    drawPath(antenna, body, style = Stroke(width = 1.8.dp.toPx() * roach.scale))
    drawPath(antenna2, body, style = Stroke(width = 1.8.dp.toPx() * roach.scale))
}
