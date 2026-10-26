package com.mclauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.mclauncher.core.Settings
import com.mclauncher.core.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * 背景缓存。
 * - desktop 模式：读系统壁纸 → 按窗口位置裁剪 → 模糊，窗口后面就是"真实的桌面"，只是被磨砂了
 * - gradient 模式：渲染自定义渐变
 */
class BackdropState {
    var base: ImageBitmap? by mutableStateOf(null)
        private set
    var blurred: ImageBitmap? by mutableStateOf(null)
        private set
    var size: IntSize by mutableStateOf(IntSize.Zero)
        private set
    var failed: Boolean by mutableStateOf(false)
        private set
    /** 背景是否直接画模糊版（图片"全部模糊"模式） */
    var backgroundUsesBlur: Boolean by mutableStateOf(false)
        private set
    var panelUsesSharp: Boolean by mutableStateOf(false)
        private set

    private var key: String = ""

    suspend fun refresh(
        settings: Settings,
        width: Int,
        height: Int,
        windowX: Int = 0,
        windowY: Int = 0,
        screenWidth: Int = width,
        screenHeight: Int = height,
        dark: Boolean = true
    ) {
        if (width < 32 || height < 32) return
        val newKey = buildString {
            append(settings.backdropMode).append('|')
            append(settings.activeGradient).append('|')
            append(settings.imagePath).append('|')
            append(settings.imageBlur).append('|')
            append(settings.imageScale).append('|')
            append(dark).append('|')
            append(settings.blur).append('|')
            append(settings.dim).append('|')
            append(width).append('x').append(height)
            append('@').append(windowX / 4).append(',').append(windowY / 4)
        }
        if (newKey == key && blurred != null) return
        key = newKey

        val result = withContext(Dispatchers.IO) {
            val image = when {
                settings.useImage -> {
                    // 先读原图：load() 会先做 cover 裁切，那样"拉伸模式"就永远是 crop 了
                    val raw = Wallpaper.readFile(settings.imagePath)
                        ?: Wallpaper.load("custom:${settings.imagePath}", width, height)
                    com.mclauncher.core.fit(raw, width, height, settings.imageScale)
                }

                else -> renderGradientImage(settings.gradient(), width, height)
            } ?: return@withContext null

            val blurRadius = if (settings.useImage) settings.imageBlurRadius else settings.blur
            val dimAmount = if (settings.useImage) settings.imageDim else settings.dim
            val bl = Wallpaper.blur(image, 4, blurRadius.coerceAtLeast(2))
            val dimmed = Wallpaper.dim(image, dimAmount)
            dimmed.toImageBitmap() to bl.toImageBitmap()
        }
        backgroundUsesBlur = settings.useImage && settings.imageBlur == Settings.IMAGE_BLUR_ALL
        // "仅侧边栏模糊"：玻璃面板采样清晰图，只有侧边栏自己用模糊图
        panelUsesSharp = settings.useImage && settings.imageBlur == Settings.IMAGE_BLUR_SIDEBAR

        if (result == null) {
            failed = true
            base = null
            blurred = null
        } else {
            failed = false
            base = result.first
            blurred = result.second
        }
        size = IntSize(width, height)
    }
}

val LocalBackdrop = staticCompositionLocalOf<ImageBitmap?> { null }
val LocalBackdropSize = staticCompositionLocalOf { IntSize.Zero }

/** 侧边栏专用：只有"仅侧边栏模糊"模式下才是模糊图 */
val LocalSidebarBackdrop = staticCompositionLocalOf<ImageBitmap?> { null }

fun BufferedImage.toImageBitmap(): ImageBitmap {
    val out = ByteArrayOutputStream()
    ImageIO.write(this, "png", out)
    return org.jetbrains.skia.Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap()
}

@Composable
fun rememberBackdrop(
    settings: Settings,
    width: Int,
    height: Int,
    windowX: Int = 0,
    windowY: Int = 0,
    screenWidth: Int = width,
    screenHeight: Int = height,
    dark: Boolean = true
): BackdropState {
    val state = androidx.compose.runtime.remember { BackdropState() }
    LaunchedEffect(
        settings.backdropMode, settings.activeGradient, settings.imagePath, settings.imageBlur, settings.imageScale, settings.imageBlurRadius, settings.imageDim,
        settings.blur, settings.dim, width, height, windowX / 4, windowY / 4, dark
    ) {
        state.refresh(settings, width, height, windowX, windowY, screenWidth, screenHeight, dark)
    }
    return state
}
