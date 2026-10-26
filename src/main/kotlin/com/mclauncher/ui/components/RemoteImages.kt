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
import java.util.concurrent.ConcurrentHashMap

/** 极简远程图片缓存，MOD 图标 / 头像都用它。 */
object RemoteImages {
    private val cache = ConcurrentHashMap<String, ImageBitmap?>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun peek(url: String?): ImageBitmap? = url?.let { cache[it] }

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank() || failed.contains(url)) return null
        cache[url]?.let { return it }
        val image = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Http.bytes(url)
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
        if (image == null) failed += url
        cache[url] = image
        return image
    }
}

@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? {
    var image by remember(url) { mutableStateOf(RemoteImages.peek(url)) }
    LaunchedEffect(url) {
        if (image == null) image = RemoteImages.load(url)
    }
    return image
}
