package com.mclauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NGML 图标：直接用打包进资源里的 ngml.png（固定图案，不再随配色变化）。
 * accent / accent2 只为兼容旧调用点保留，不再参与绘制。
 */
@Composable
fun NgmlLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    accent: Color = Color(0xFF5B8CFF),
    accent2: Color = Color(0xFF4FE3D0)
) {
    val image = rememberNgmlPng()
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "NGML",
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        Canvas(modifier.size(size)) { drawCube(accent, accent2) }
    }
}

/** 从 classpath 读 /ngml.png，只解码一次 */
@Composable
fun rememberNgmlPng(): androidx.compose.ui.graphics.ImageBitmap? = androidx.compose.runtime.remember {
    runCatching {
        val stream = NgmlLogoResource::class.java.getResourceAsStream("/ngml.png") ?: return@remember null
        val buffered = stream.use { javax.imageio.ImageIO.read(it) } ?: return@remember null
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(buffered, "png", out)
        org.jetbrains.skia.Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap()
    }.getOrNull()
}

private object NgmlLogoResource

fun DrawScope.drawCube(accent: Color, accent2: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val top = h * 0.08f
    val midY = h * 0.42f
    val bottom = h * 0.92f
    val halfW = w * 0.44f

    val topFace = Path().apply {
        moveTo(cx, top)
        lineTo(cx + halfW, top + h * 0.17f)
        lineTo(cx, midY)
        lineTo(cx - halfW, top + h * 0.17f)
        close()
    }
    val leftFace = Path().apply {
        moveTo(cx - halfW, top + h * 0.17f)
        lineTo(cx, midY)
        lineTo(cx, bottom)
        lineTo(cx - halfW, top + h * 0.17f + (bottom - midY))
        close()
    }
    val rightFace = Path().apply {
        moveTo(cx + halfW, top + h * 0.17f)
        lineTo(cx, midY)
        lineTo(cx, bottom)
        lineTo(cx + halfW, top + h * 0.17f + (bottom - midY))
        close()
    }

    drawPath(
        topFace,
        Brush.linearGradient(listOf(accent2, accent.copy(alpha = 0.85f)), start = Offset(0f, top), end = Offset(w, midY))
    )
    drawPath(
        leftFace,
        Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f)), start = Offset(0f, top), end = Offset(cx, bottom))
    )
    drawPath(
        rightFace,
        Brush.linearGradient(listOf(accent2.copy(alpha = 0.8f), accent.copy(alpha = 0.4f)), start = Offset(cx, midY), end = Offset(w, bottom))
    )
    drawPath(topFace, Color.White.copy(alpha = 0.35f), style = Stroke(width = w * 0.02f))
}
