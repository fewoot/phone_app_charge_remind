package com.example.chargereminder.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.example.chargereminder.data.Settings

data class AppColors(
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val card: Color,
    val cardBorder: Color,
    val chip: Color,
    val track: Color,
    val divider: Color,
    val danger: Color,
    val dangerBg: Color,
    val amber: Color,
    val amberBg: Color,
    val sheet: Color
)

data class Palette(val key: String, val label: String, val light: Color, val dark: Color)

val Palettes = listOf(
    Palette("mint", "薄荷", Color(0xFF0B8F6A), Color(0xFF37C99A)),
    Palette("ocean", "海盐", Color(0xFF2563EB), Color(0xFF7FB0FF)),
    Palette("sunset", "暖阳", Color(0xFFC2620F), Color(0xFFFFB067)),
    Palette("violet", "紫霞", Color(0xFF6D4AE0), Color(0xFFB79CFF))
)

fun paletteOf(key: String): Palette = Palettes.firstOrNull { it.key == key } ?: Palettes[0]

@Composable
fun isDarkTheme(settings: Settings): Boolean = when (settings.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun colorsFor(settings: Settings): AppColors {
    val dark = isDarkTheme(settings)
    val palette = paletteOf(settings.palette)
    val context = LocalContext.current

    val systemAccent: Color? = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scheme.primary
    } else {
        null
    }

    val accent = systemAccent ?: if (dark) palette.dark else palette.light
    return remember(dark, accent) { buildColors(accent, dark) }
}

fun buildColors(accent: Color, dark: Boolean): AppColors {
    val bgTop = if (dark) lerp(Color(0xFF0A0F14), accent, 0.18f) else lerp(Color.White, accent, 0.10f)
    val bgBottom = if (dark) Color(0xFF070A0D) else lerp(Color.White, accent, 0.03f)

    return AppColors(
        accent = accent,
        accent2 = if (dark) lerp(accent, Color.White, 0.25f) else lerp(accent, Color.White, 0.35f),
        onAccent = if (dark) Color(0xFF04231C) else Color.White,
        bgTop = bgTop,
        bgBottom = bgBottom,
        text = if (dark) Color(0xFFF2F5F9) else Color(0xFF10151D),
        text2 = if (dark) Color(0xFFA6B0BF) else Color(0xFF5B6675),
        text3 = if (dark) Color(0xFF78838F) else Color(0xFF8B95A5),
        card = if (dark) lerp(Color(0xFF141A21), accent, 0.06f) else Color.White.copy(alpha = 0.74f),
        cardBorder = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.9f),
        chip = if (dark) Color.White.copy(alpha = 0.07f) else Color(0xFF10151D).copy(alpha = 0.05f),
        track = if (dark) Color.White.copy(alpha = 0.14f) else Color(0xFF10151D).copy(alpha = 0.09f),
        divider = if (dark) Color.White.copy(alpha = 0.07f) else Color(0xFF10151D).copy(alpha = 0.07f),
        danger = if (dark) Color(0xFFFF7A7F) else Color(0xFFC62B31),
        dangerBg = if (dark) Color(0x24FF7A7F) else Color(0x21E5484D),
        amber = if (dark) Color(0xFFF2B24C) else Color(0xFFB26A00),
        amberBg = if (dark) Color(0x24F2B24C) else Color(0x26F59E0B),
        sheet = if (dark) Color(0xFF141920) else Color(0xFFFBFCFE)
    )
}

fun schemeFor(c: AppColors, dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accent.copy(alpha = 0.16f),
            onPrimaryContainer = c.text,
            secondary = c.accent,
            onSecondary = c.onAccent,
            background = c.bgBottom,
            onBackground = c.text,
            surface = c.sheet,
            onSurface = c.text,
            surfaceVariant = c.chip,
            onSurfaceVariant = c.text2,
            outline = c.text3.copy(alpha = 0.6f),
            outlineVariant = c.divider,
            error = c.danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accent.copy(alpha = 0.16f),
            onPrimaryContainer = c.text,
            secondary = c.accent,
            onSecondary = c.onAccent,
            background = c.bgBottom,
            onBackground = c.text,
            surface = c.sheet,
            onSurface = c.text,
            surfaceVariant = c.chip,
            onSurfaceVariant = c.text2,
            outline = c.text3.copy(alpha = 0.6f),
            outlineVariant = c.divider,
            error = c.danger,
            onError = Color.White
        )
    }
}
