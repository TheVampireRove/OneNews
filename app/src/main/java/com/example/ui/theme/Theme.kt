package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.ThemeMode
import com.example.model.ThemeSettings

private val ClassicOneUiAmoledDarkScheme = darkColorScheme(
    primary = OneUiBlueDark,
    onPrimary = OneUiOnBlueDark,
    primaryContainer = OneUiBlueContainerDark,
    onPrimaryContainer = OneUiOnBlueContainerDark,
    secondary = OneUiSecondaryDark,
    onSecondary = OneUiOnSecondaryDark,
    secondaryContainer = OneUiSecondaryContainerDark,
    onSecondaryContainer = OneUiOnSecondaryContainerDark,
    tertiary = OneUiTertiaryDark,
    onTertiary = OneUiOnTertiaryDark,
    tertiaryContainer = OneUiTertiaryContainerDark,
    onTertiaryContainer = OneUiOnTertiaryContainerDark,
    background = AmoledBlack,
    onBackground = OneUiTextPrimaryDark,
    surface = AmoledBlack,
    onSurface = OneUiTextPrimaryDark,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = OneUiTextSecondaryDark,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledCardLow,
    surfaceContainer = AmoledCardElevated,
    surfaceContainerHigh = AmoledCardHigh,
    surfaceContainerHighest = AmoledCardHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineSubtle,
)

private val ClassicOneUiTonalDarkScheme = darkColorScheme(
    primary = OneUiBlueDark,
    onPrimary = OneUiOnBlueDark,
    primaryContainer = OneUiBlueContainerDark,
    onPrimaryContainer = OneUiOnBlueContainerDark,
    secondary = OneUiSecondaryDark,
    onSecondary = OneUiOnSecondaryDark,
    secondaryContainer = OneUiSecondaryContainerDark,
    onSecondaryContainer = OneUiOnSecondaryContainerDark,
    tertiary = OneUiTertiaryDark,
    onTertiary = OneUiOnTertiaryDark,
    tertiaryContainer = OneUiTertiaryContainerDark,
    onTertiaryContainer = OneUiOnTertiaryContainerDark,
    background = TonalDarkBackground,
    onBackground = OneUiTextPrimaryDark,
    surface = TonalDarkBackground,
    onSurface = OneUiTextPrimaryDark,
    surfaceVariant = TonalDarkSurface,
    onSurfaceVariant = OneUiTextSecondaryDark,
    surfaceContainerLowest = TonalDarkBackground,
    surfaceContainerLow = TonalDarkSurfaceLow,
    surfaceContainer = TonalDarkSurface,
    surfaceContainerHigh = TonalDarkSurfaceHigh,
    surfaceContainerHighest = TonalDarkSurfaceHighest,
    outline = AmoledOutline,
    outlineVariant = TonalDarkSurfaceHighest,
)

private val ClassicOneUiLightScheme = lightColorScheme(
    primary = OneUiBlueLight,
    onPrimary = OneUiOnBlueLight,
    primaryContainer = OneUiBlueContainerLight,
    onPrimaryContainer = OneUiOnBlueContainerLight,
    secondary = OneUiSecondaryLight,
    onSecondary = OneUiOnSecondaryLight,
    secondaryContainer = OneUiSecondaryContainerLight,
    onSecondaryContainer = OneUiOnSecondaryContainerLight,
    tertiary = OneUiTertiaryLight,
    onTertiary = OneUiOnTertiaryLight,
    tertiaryContainer = OneUiTertiaryContainerLight,
    onTertiaryContainer = OneUiOnTertiaryContainerLight,
    background = OneUiLightBackground,
    onBackground = OneUiTextPrimaryLight,
    surface = OneUiLightSurface,
    onSurface = OneUiTextPrimaryLight,
    surfaceVariant = OneUiLightSurfaceVariant,
    onSurfaceVariant = OneUiTextSecondaryLight,
    surfaceContainerLowest = OneUiLightSurface,
    surfaceContainerLow = OneUiLightSurfaceLow,
    surfaceContainer = OneUiLightSurfaceContainer,
    surfaceContainerHigh = OneUiLightSurfaceHigh,
    surfaceContainerHighest = OneUiLightSurfaceHighest,
    outline = OneUiLightOutline,
    outlineVariant = OneUiLightOutlineSubtle,
)

@Composable
fun OneNewsTheme(
    themeSettings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeSettings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        themeSettings.useDynamicColor && supportsDynamicColor -> {
            if (useDarkTheme) {
                val dynamicDark = dynamicDarkColorScheme(context)
                if (themeSettings.useAmoledBlack) {
                    // Hybrid Monet + One UI AMOLED Black mapping:
                    // Keep Monet's extracted primary/secondary/tertiary accents while enforcing
                    // true #000000 canvas and #17171A elevated card surfaces.
                    dynamicDark.copy(
                        background = AmoledBlack,
                        onBackground = OneUiTextPrimaryDark,
                        surface = AmoledBlack,
                        onSurface = OneUiTextPrimaryDark,
                        surfaceVariant = AmoledSurfaceVariant,
                        onSurfaceVariant = OneUiTextSecondaryDark,
                        surfaceContainerLowest = AmoledBlack,
                        surfaceContainerLow = AmoledCardLow,
                        surfaceContainer = AmoledCardElevated,
                        surfaceContainerHigh = AmoledCardHigh,
                        surfaceContainerHighest = AmoledCardHighest,
                        outlineVariant = AmoledOutlineSubtle,
                    )
                } else {
                    dynamicDark
                }
            } else {
                dynamicLightColorScheme(context)
            }
        }
        useDarkTheme && themeSettings.useAmoledBlack -> ClassicOneUiAmoledDarkScheme
        useDarkTheme -> ClassicOneUiTonalDarkScheme
        else -> ClassicOneUiLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = oneNewsTypography(useSystemFont = themeSettings.useSystemFont),
        shapes = OneNewsShapes,
        content = content,
    )
}
