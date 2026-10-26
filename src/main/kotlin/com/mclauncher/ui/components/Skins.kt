package com.mclauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mclauncher.core.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 皮肤渲染：官方皮肤是 64x64，头部正面在 (8,8)-(16,16)，帽子层在 (40,8)。
 */
object Skins {

    /** file:// 或本地绝对路径直接读文件，其余走网络 */
    private fun rawBytes(url: String): ByteArray = when {
        url.startsWith("file:", ignoreCase = true) ->
            java.io.File(java.net.URI(url)).readBytes()
        url.length > 2 && url[1] == ':' && url[2] == '\\' ->
            java.io.File(url).readBytes()
        else -> Http.bytes(url)
    }

    fun decode(bytes: ByteArray): BufferedImage? =
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    fun head(skin: BufferedImage, includeHat: Boolean = true): BufferedImage {
        val scale = if (skin.width >= 64) 1 else 1
        val out = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(skin, 0, 0, 8, 8, 8 * scale, 8 * scale, 16 * scale, 16 * scale, null)
        if (includeHat && skin.width >= 64) {
            g.drawImage(skin, 0, 0, 8, 8, 40 * scale, 8 * scale, 48 * scale, 16 * scale, null)
        }
        g.dispose()
        return out
    }

    fun body(skin: BufferedImage): BufferedImage {
        val out = BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        // 头
        g.drawImage(skin, 4, 0, 12, 8, 8, 8, 16, 16, null)
        // 身体
        g.drawImage(skin, 4, 8, 12, 20, 20, 20, 28, 32, null)
        // 手臂
        g.drawImage(skin, 0, 8, 4, 20, 44, 20, 48, 32, null)
        g.drawImage(skin, 12, 8, 16, 20, 44, 20, 48, 32, null)
        // 腿
        g.drawImage(skin, 4, 20, 8, 32, 4, 20, 8, 32, null)
        g.drawImage(skin, 8, 20, 12, 32, 20, 52, 24, 64, null)
        g.dispose()
        return out
    }

    suspend fun fetch(url: String?): BufferedImage? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching { rawBytes(url) }.getOrNull()?.let { decode(it) }
        }
    }

    /** 原始 PNG 字节（下载皮肤用） */
    suspend fun fetchBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching { rawBytes(url) }.getOrNull()
        }
    }
}

/** 弹出"另存为"对话框，返回要写入的文件 */
fun saveSkinFile(defaultName: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "保存皮肤", java.awt.FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    val file = java.io.File(dir, name)
    return if (file.name.lowercase().endsWith(".png")) file else java.io.File(dir, file.name + ".png")
}

@Composable
fun rememberSkinHead(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = Skins.fetch(url)?.let { skin ->
            // 之前直接把整张 skin 当头像用了，这里裁出头部并按最近邻放大，避免糊
            val head = Skins.head(skin)
            val scale = 8
            val big = java.awt.image.BufferedImage(
                head.width * scale, head.height * scale, java.awt.image.BufferedImage.TYPE_INT_ARGB
            )
            val g = big.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            )
            g.drawImage(head, 0, 0, big.width, big.height, null)
            g.dispose()
            big.toCompose()
        }
    }
    return bitmap
}

@Composable
fun rememberSkinFull(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = Skins.fetch(url)?.let { skin ->
            val out = java.awt.image.BufferedImage(skin.width * 6, skin.height * 6, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            )
            g.drawImage(skin, 0, 0, out.width, out.height, null)
            g.dispose()
            out.toCompose()
        }
    }
    return bitmap
}

private fun BufferedImage.toCompose(): ImageBitmap {
    val out = java.io.ByteArrayOutputStream()
    ImageIO.write(this, "png", out)
    return org.jetbrains.skia.Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap()
}
/** 把皮肤渲染成"站立角色"的位图，给 3D 换肤轮播用 */
fun bodyBitmap(bytes: ByteArray, heightCells: Int = 7): ImageBitmap? {
    val skin = Skins.decode(bytes) ?: return null
    val body = Skins.body(skin)
    val w = body.width * heightCells
    val h = body.height * heightCells
    val out = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
    )
    g.drawImage(body, 0, 0, w, h, null)
    g.dispose()
    return out.toCompose()
}