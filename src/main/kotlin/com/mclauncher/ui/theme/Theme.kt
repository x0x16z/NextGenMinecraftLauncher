package com.mclauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Immutable
data class AccentPalette(
    val id: String,
    val label: String,
    val accent: Color,
    val accent2: Color
)

val AC_PALETTES = listOf(
    AccentPalette("azure", "冰川蓝", Color(0xFF5B8CFF), Color(0xFF4FE3D0)),
    AccentPalette("sakura", "樱雾粉", Color(0xFFFF6FA5), Color(0xFFFFB36B)),
    AccentPalette("forest", "苔原绿", Color(0xFF3FD9A4), Color(0xFFA8E063)),
    AccentPalette("violet", "紫罗兰", Color(0xFF9B6BFF), Color(0xFF68A8FF)),
    AccentPalette("sunset", "暮色橙", Color(0xFFFF8A4C), Color(0xFFFF5E7A)),
    AccentPalette("ocean", "深海青", Color(0xFF2BC6D9), Color(0xFF3B7DFF)),
    AccentPalette("amber", "琥珀金", Color(0xFFFFB830), Color(0xFFFF7A45)),
    AccentPalette("rosewood", "檀木红", Color(0xFFE0574F), Color(0xFFF09A5B)),
    AccentPalette("mint", "薄荷冰", Color(0xFF7FE7C4), Color(0xFF5BC8FF)),
    AccentPalette("mono", "石墨灰", Color(0xFF9AA6B8), Color(0xFFD7DEE9)),
    AccentPalette("neon", "霓虹紫", Color(0xFFD45BFF), Color(0xFF4FE3FF)),
    AccentPalette("lava", "熔岩红", Color(0xFFFF4D4D), Color(0xFFFFB03A))
)

fun paletteOf(id: String): AccentPalette = AC_PALETTES.firstOrNull { it.id == id } ?: AC_PALETTES.first()

@Immutable
data class ThemeTokens(
    val dark: Boolean,
    val accent: Color,
    val accent2: Color,
    val text: Color,
    val subtext: Color,
    val glass: Color,
    val border: Color,
    val divider: Color,
    val scrim: Color,
    val field: Color,
    val danger: Color,
    val success: Color,
    /** 盖在实时背景上的那层压暗 / 提亮 */
    val scrimOverlay: Color
)

val LocalTokens = staticCompositionLocalOf {
    ThemeTokens(
        dark = true, accent = Color(0xFF5B8CFF), accent2 = Color(0xFF4FE3D0),
        text = Color(0xFFF8FAFF), subtext = Color(0xFFDCE4F0), glass = Color(0x4D000000),
        border = Color(0x40FFFFFF), divider = Color(0x24FFFFFF), scrim = Color(0x66000000),
        field = Color(0x33FFFFFF), danger = Color(0xFFFF7A7A), success = Color(0xFF52E0AC),
        scrimOverlay = Color(0x66000000)
    )
}

fun tokensFor(dark: Boolean, palette: AccentPalette, tint: Long): ThemeTokens {
    val customTint = Color(tint.toInt())
    return if (dark) {
        // 真正的深色玻璃：半透明黑底 + 轻微染色，白字配阴影
        val darkGlass = if (customTint.alpha > 0.12f) {
            Color(
                red = customTint.red * 0.30f,
                green = customTint.green * 0.30f,
                blue = customTint.blue * 0.30f,
                alpha = 0.36f
            )
        } else {
            Color(0x4D000000)
        }
        ThemeTokens(
            dark = true,
            accent = palette.accent,
            accent2 = palette.accent2,
            text = Color(0xFFF8FAFF),
            subtext = Color(0xFFE2E9F5),
            glass = darkGlass,
            border = Color(0x42FFFFFF),
            divider = Color(0x26FFFFFF),
            scrim = Color(0x66000000),
            field = Color(0x33FFFFFF),
            danger = Color(0xFFFF7A7A),
            success = Color(0xFF52E0AC),
            scrimOverlay = Color(0x6E000000)
        )
    } else {
        ThemeTokens(
            dark = false,
            accent = palette.accent,
            accent2 = palette.accent2,
            text = Color(0xFF0B1017),
            subtext = Color(0xFF2A303C),
            glass = Color(0x6BFFFFFF),
            border = Color(0x8CFFFFFF),
            divider = Color(0x1A000000),
            scrim = Color(0x4D000000),
            field = Color(0x5CFFFFFF),
            danger = Color(0xFFC93A3A),
            success = Color(0xFF0E7A57),
            scrimOverlay = Color(0x45FFFFFF)
        )
    }
}

private val cjkFamily: FontFamily? = runCatching {
    val normal = File("C:\\Windows\\Fonts\\msyh.ttc")
    val bold = File("C:\\Windows\\Fonts\\msyhbd.ttc")
    when {
        normal.exists() && bold.exists() -> FontFamily(
            Font(normal, FontWeight.Normal),
            Font(normal, FontWeight.Medium),
            Font(bold, FontWeight.SemiBold),
            Font(bold, FontWeight.Bold)
        )
        normal.exists() -> FontFamily(Font(normal, FontWeight.Normal))
        else -> null
    }
}.getOrNull()

private fun typography(dark: Boolean): Typography {
    val base = Typography()
    val family = cjkFamily
    val shadow = if (dark) Shadow(Color(0x8C000000), Offset(0f, 1f), 4f) else null

    fun TextStyle.tune(sizeSp: Int? = null, weight: FontWeight? = null): TextStyle =
        this.copy(
            fontFamily = family ?: this.fontFamily,
            fontSize = sizeSp?.sp ?: this.fontSize,
            fontWeight = weight ?: this.fontWeight,
            shadow = shadow ?: this.shadow
        )

    if (family == null && shadow == null) return base

    return Typography(
        displayLarge = base.displayLarge.tune(),
        displayMedium = base.displayMedium.tune(),
        displaySmall = base.displaySmall.tune(),
        headlineLarge = base.headlineLarge.tune(),
        headlineMedium = base.headlineMedium.tune(),
        headlineSmall = base.headlineSmall.tune(),
        titleLarge = base.titleLarge.tune(20, FontWeight.SemiBold),
        titleMedium = base.titleMedium.tune(15, FontWeight.Medium),
        titleSmall = base.titleSmall.tune(13),
        bodyLarge = base.bodyLarge.tune(14),
        bodyMedium = base.bodyMedium.tune(13),
        bodySmall = base.bodySmall.tune(12),
        labelLarge = base.labelLarge.tune(13),
        labelMedium = base.labelMedium.tune(12),
        labelSmall = base.labelSmall.tune(11)
    )
}

@Composable
fun LauncherTheme(
    dark: Boolean,
    palette: AccentPalette,
    glassTint: Long = 0x38FFFFFF,
    content: @Composable () -> Unit
) {
    val tokens = remember(dark, palette, glassTint) { tokensFor(dark, palette, glassTint) }
    val scheme = remember(dark, palette) {
        if (dark) darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent2,
            background = Color.Transparent,
            surface = Color(0xFF151921),
            onSurface = Color(0xFFF8FAFF),
            onSurfaceVariant = Color(0xFFE2E9F5),
            outline = Color(0x40FFFFFF)
        ) else lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent2,
            background = Color.Transparent,
            surface = Color(0xFFF7F9FD),
            onSurface = Color(0xFF0B1017),
            onSurfaceVariant = Color(0xFF2A303C),
            outline = Color(0x33000000)
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography(dark)) {
        CompositionLocalProvider(LocalTokens provides tokens) { content() }
    }
}

val CardShape = RoundedCornerShape(20.dp)
val SmallShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(50)
