package com.example.cryptnote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.cryptnote.data.preferences.ThemeColor

fun buildDarkColorScheme(color: ThemeColor): ColorScheme {
    val (primary, onPrimary, container, onContainer) = when (color) {
        ThemeColor.WARM_ESPRESSO -> listOf(PaletteEspressoDarkPrimary, PaletteEspressoDarkOnPrimary, PaletteEspressoDarkContainer, PaletteEspressoDarkOnContainer)
        ThemeColor.CYBER_TEAL -> listOf(PaletteTealDarkPrimary, PaletteTealDarkOnPrimary, PaletteTealDarkContainer, PaletteTealDarkOnContainer)
        ThemeColor.NEON_BLUE -> listOf(PaletteBlueDarkPrimary, PaletteBlueDarkOnPrimary, PaletteBlueDarkContainer, PaletteBlueDarkOnContainer)
        ThemeColor.ELECTRIC_PURPLE -> listOf(PalettePurpleDarkPrimary, PalettePurpleDarkOnPrimary, PalettePurpleDarkContainer, PalettePurpleDarkOnContainer)
        ThemeColor.EMERALD_MATRIX -> listOf(PaletteEmeraldDarkPrimary, PaletteEmeraldDarkOnPrimary, PaletteEmeraldDarkContainer, PaletteEmeraldDarkOnContainer)
        ThemeColor.SUNSET_AMBER -> listOf(PaletteAmberDarkPrimary, PaletteAmberDarkOnPrimary, PaletteAmberDarkContainer, PaletteAmberDarkOnContainer)
        ThemeColor.CRIMSON_ROSE -> listOf(PaletteRoseDarkPrimary, PaletteRoseDarkOnPrimary, PaletteRoseDarkContainer, PaletteRoseDarkOnContainer)
    }

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = CryptoBlueDark,
        onSecondary = CryptoBlueOnDark,
        secondaryContainer = CryptoBlueContainerDark,
        onSecondaryContainer = CryptoBlueOnContainerDark,
        background = CryptoBackgroundDark,
        onBackground = CryptoOnBackgroundDark,
        surface = CryptoSurfaceDark,
        onSurface = CryptoOnSurfaceDark,
        surfaceVariant = CryptoSurfaceVariantDark,
        onSurfaceVariant = CryptoOnSurfaceVariantDark,
        outline = CryptoOutlineDark,
        error = CryptoErrorDark,
        onError = CryptoErrorOnDark
    )
}

fun buildLightColorScheme(color: ThemeColor): ColorScheme {
    val (primary, onPrimary, container, onContainer) = when (color) {
        ThemeColor.WARM_ESPRESSO -> listOf(PaletteEspressoLightPrimary, PaletteEspressoLightOnPrimary, PaletteEspressoLightContainer, PaletteEspressoLightOnContainer)
        ThemeColor.CYBER_TEAL -> listOf(PaletteTealLightPrimary, PaletteTealLightOnPrimary, PaletteTealLightContainer, PaletteTealLightOnContainer)
        ThemeColor.NEON_BLUE -> listOf(PaletteBlueLightPrimary, PaletteBlueLightOnPrimary, PaletteBlueLightContainer, PaletteBlueLightOnContainer)
        ThemeColor.ELECTRIC_PURPLE -> listOf(PalettePurpleLightPrimary, PalettePurpleLightOnPrimary, PalettePurpleLightContainer, PalettePurpleLightOnContainer)
        ThemeColor.EMERALD_MATRIX -> listOf(PaletteEmeraldLightPrimary, PaletteEmeraldLightOnPrimary, PaletteEmeraldLightContainer, PaletteEmeraldLightOnContainer)
        ThemeColor.SUNSET_AMBER -> listOf(PaletteAmberLightPrimary, PaletteAmberLightOnPrimary, PaletteAmberLightContainer, PaletteAmberLightOnContainer)
        ThemeColor.CRIMSON_ROSE -> listOf(PaletteRoseLightPrimary, PaletteRoseLightOnPrimary, PaletteRoseLightContainer, PaletteRoseLightOnContainer)
    }

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = CryptoBlueLight,
        onSecondary = CryptoBlueOnLight,
        secondaryContainer = CryptoBlueContainerLight,
        onSecondaryContainer = CryptoBlueOnContainerLight,
        background = CryptoBackgroundLight,
        onBackground = CryptoOnBackgroundLight,
        surface = CryptoSurfaceLight,
        onSurface = CryptoOnSurfaceLight,
        surfaceVariant = CryptoSurfaceVariantLight,
        onSurfaceVariant = CryptoOnSurfaceVariantLight,
        outline = CryptoOutlineLight,
        error = CryptoErrorLight,
        onError = CryptoErrorOnLight
    )
}

@Composable
fun CryptNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeColor: ThemeColor = ThemeColor.WARM_ESPRESSO,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> buildDarkColorScheme(themeColor)
        else -> buildLightColorScheme(themeColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
