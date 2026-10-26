package com.mclauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import java.util.concurrent.ConcurrentHashMap

/**
 * 版本图标：优先用打包进 resources 的官方物品贴图（minecraft.wiki 的 16x16 原图），
 * 万一资源缺失再退回内置的像素点阵画法。
 */
object McIcons {

    data class Icon(val id: String, val name: String)

    val all = listOf(
        Icon("grass", "草方块"),
        Icon("diamond", "钻石"),
        Icon("pickaxe", "钻石镐"),
        Icon("apple", "苹果"),
        Icon("golden_apple", "金苹果"),
        Icon("pearl", "末影珍珠"),
        Icon("book", "书"),
        Icon("tnt", "TNT"),
        Icon("emerald", "绿宝石"),
        Icon("netherite", "下界合金"),
        Icon("crafting", "工作台"),
        Icon("creeper", "苦力怕")
    )

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun byId(id: String): Icon = all.firstOrNull { it.id == id } ?: all.first()

    fun png(id: String): ImageBitmap? {
        cache[id]?.let { return it }
        if (id in missing) return null
        val image = runCatching {
            val stream = McIcons::class.java.getResourceAsStream("/mcicons/$id.png") ?: return@runCatching null
            val bytes = stream.use { it.readBytes() }
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
        if (image == null) missing += id else cache[id] = image
        return image
    }

    @Composable
    fun McIconView(id: String, size: Dp, modifier: Modifier = Modifier) {
        val bitmap = remember(id) { png(id) }
        if (bitmap != null) {
            Image(bitmap, null, modifier.size(size), contentScale = ContentScale.Fit)
        } else {
            PixelFallback(id, modifier.size(size))
        }
    }

    /* --------------------------- 兜底：像素点阵 --------------------------- */

    private val fallbackPixels = mapOf(
        "grass" to listOf(
            "................", "....gggggggg....", "..gggggggggggg..", ".gggggggggggggg.",
            "gggggggggggggggg", "gggggggggggggggg", "dddddddddddddddd", "dddddddddddddddd",
            "dddddddddddddddd", "dddddddddddddddd", "dddddddddddddddd", "dddddddddddddddd",
            "dddddddddddddddd", "dddddddddddddddd", "dddddddddddddddd", "dddddddddddddddd"
        ),
        "creeper" to listOf(
            "...gggggggggg...", "..gggggggggggg..", ".gggggggggggggg.", ".ggkkggggggkkgg.",
            ".ggkkggggggkkgg.", ".gggggggggggggg.", ".ggggkkkkkkgggg.", ".ggggkkkkkkgggg.",
            ".ggkkggggggkkgg.", ".ggkkggggggkkgg.", ".gggggggggggggg.", "..gggggggggggg..",
            "...gggggggggg...", "................", "................", "................"
        )
    )

    private val palette = mapOf(
        'g' to Color(0xFF5FA84E),
        'd' to Color(0xFF8B5A2B),
        'k' to Color(0xFF1E2430)
    )

    @Composable
    private fun PixelFallback(id: String, modifier: Modifier) {
        val rows = fallbackPixels[id] ?: fallbackPixels["grass"]!!
        Canvas(modifier) {
            val cell = size.width / 16f
            for (y in 0 until 16) {
                val row = rows.getOrNull(y) ?: continue
                for (x in 0 until 16) {
                    val ch = row.getOrNull(x) ?: '.'
                    if (ch == '.') continue
                    drawRect(
                        color = palette[ch] ?: continue,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.6f, cell + 0.6f)
                    )
                }
            }
        }
    }
}
