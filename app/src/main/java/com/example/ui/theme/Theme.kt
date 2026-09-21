package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val code: String, val label: String) {
    SYSTEM("SYSTEM", "System"),
    LIGHT("LIGHT", "Light"),
    DARK("DARK", "Dark");

    companion object {
        fun fromCode(code: String): ThemeMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalIsDarkTheme = compositionLocalOf { false }

data class NovaCustomColors(
    val userBubble: Color,
    val userBubbleBorder: Color,
    val goldLabel: Color,
    val accentEmerald: Color
)

val LocalNovaCustomColors = compositionLocalOf {
    NovaCustomColors(
        userBubble = Color(0xFF2A2A21),
        userBubbleBorder = Color(0xFF2E2E2E),
        goldLabel = Color(0xFFD9B95C),
        accentEmerald = Color(0xFF10A37F)
    )
}

private val LightColorScheme = lightColorScheme(
    primary = ChatGPTLightAccent,
    onPrimary = Color.White,
    primaryContainer = ChatGPTLightSelectedBg,
    onPrimaryContainer = Color(0xFF0A6B53),
    secondary = ChatGPTLightTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = ChatGPTLightSurfaceVariant,
    onSecondaryContainer = ChatGPTLightTextPrimary,
    tertiary = ChatGPTLightAccent,
    onTertiary = Color.White,
    tertiaryContainer = ChatGPTLightSelectedBg,
    onTertiaryContainer = Color(0xFF0A6B53),
    background = ChatGPTLightBackground,
    onBackground = ChatGPTLightTextPrimary,
    surface = ChatGPTLightCard,
    onSurface = ChatGPTLightTextPrimary,
    surfaceVariant = ChatGPTLightSurfaceVariant,
    onSurfaceVariant = ChatGPTLightTextSecondary,
    outline = ChatGPTLightBorder,
    outlineVariant = ChatGPTLightBorderVariant,
    error = Color(0xFFEF4444),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = NovaDarkAccent,
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF2E2422),
    onPrimaryContainer = NovaDarkAccent,
    secondary = NovaDarkTextSecondary,
    onSecondary = Color(0xFF121212),
    secondaryContainer = NovaDarkSurfaceVariant,
    onSecondaryContainer = NovaDarkTextPrimary,
    tertiary = NovaDarkAccent,
    onTertiary = Color(0xFF121212),
    tertiaryContainer = Color(0xFF2E2422),
    onTertiaryContainer = NovaDarkAccent,
    background = NovaDarkBackground,
    onBackground = NovaDarkTextPrimary,
    surface = NovaDarkCard,
    onSurface = NovaDarkTextPrimary,
    surfaceVariant = NovaDarkSurfaceVariant,
    onSurfaceVariant = NovaDarkTextSecondary,
    outline = NovaDarkBorder,
    outlineVariant = NovaDarkBorder,
    error = NovaDarkAccent,
    onError = Color.White
)

/**
 * Interpolates all Material 3 ColorScheme tokens with a smooth fade animation
 * to prevent abrupt color snapping during theme changes.
 */
@Composable
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(durationMillis = 400, easing = FastOutSlowInEasing)
): ColorScheme {
    val primary by animateColorAsState(targetColorScheme.primary, animationSpec, label = "theme_primary")
    val onPrimary by animateColorAsState(targetColorScheme.onPrimary, animationSpec, label = "theme_onPrimary")
    val primaryContainer by animateColorAsState(targetColorScheme.primaryContainer, animationSpec, label = "theme_primaryContainer")
    val onPrimaryContainer by animateColorAsState(targetColorScheme.onPrimaryContainer, animationSpec, label = "theme_onPrimaryContainer")
    val secondary by animateColorAsState(targetColorScheme.secondary, animationSpec, label = "theme_secondary")
    val onSecondary by animateColorAsState(targetColorScheme.onSecondary, animationSpec, label = "theme_onSecondary")
    val secondaryContainer by animateColorAsState(targetColorScheme.secondaryContainer, animationSpec, label = "theme_secondaryContainer")
    val onSecondaryContainer by animateColorAsState(targetColorScheme.onSecondaryContainer, animationSpec, label = "theme_onSecondaryContainer")
    val tertiary by animateColorAsState(targetColorScheme.tertiary, animationSpec, label = "theme_tertiary")
    val onTertiary by animateColorAsState(targetColorScheme.onTertiary, animationSpec, label = "theme_onTertiary")
    val tertiaryContainer by animateColorAsState(targetColorScheme.tertiaryContainer, animationSpec, label = "theme_tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(targetColorScheme.onTertiaryContainer, animationSpec, label = "theme_onTertiaryContainer")
    val background by animateColorAsState(targetColorScheme.background, animationSpec, label = "theme_background")
    val onBackground by animateColorAsState(targetColorScheme.onBackground, animationSpec, label = "theme_onBackground")
    val surface by animateColorAsState(targetColorScheme.surface, animationSpec, label = "theme_surface")
    val onSurface by animateColorAsState(targetColorScheme.onSurface, animationSpec, label = "theme_onSurface")
    val surfaceVariant by animateColorAsState(targetColorScheme.surfaceVariant, animationSpec, label = "theme_surfaceVariant")
    val onSurfaceVariant by animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec, label = "theme_onSurfaceVariant")
    val outline by animateColorAsState(targetColorScheme.outline, animationSpec, label = "theme_outline")
    val outlineVariant by animateColorAsState(targetColorScheme.outlineVariant, animationSpec, label = "theme_outlineVariant")
    val error by animateColorAsState(targetColorScheme.error, animationSpec, label = "theme_error")
    val onError by animateColorAsState(targetColorScheme.onError, animationSpec, label = "theme_onError")
    val errorContainer by animateColorAsState(targetColorScheme.errorContainer, animationSpec, label = "theme_errorContainer")
    val onErrorContainer by animateColorAsState(targetColorScheme.onErrorContainer, animationSpec, label = "theme_onErrorContainer")
    val inverseSurface by animateColorAsState(targetColorScheme.inverseSurface, animationSpec, label = "theme_inverseSurface")
    val inverseOnSurface by animateColorAsState(targetColorScheme.inverseOnSurface, animationSpec, label = "theme_inverseOnSurface")
    val inversePrimary by animateColorAsState(targetColorScheme.inversePrimary, animationSpec, label = "theme_inversePrimary")
    val surfaceTint by animateColorAsState(targetColorScheme.surfaceTint, animationSpec, label = "theme_surfaceTint")
    val scrim by animateColorAsState(targetColorScheme.scrim, animationSpec, label = "theme_scrim")

    return targetColorScheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceTint = surfaceTint,
        scrim = scrim
    )
}

@Composable
fun NovaAssistantTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = darkTheme ?: when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    val targetColorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val animatedColorScheme = animateColorScheme(targetColorScheme)

    // Animated custom semantic tokens for smooth bubble transitions
    val targetUserBubble = if (isDark) NovaDarkUserBubble else ChatGPTLightSurfaceVariant
    val targetUserBubbleBorder = if (isDark) NovaDarkBorder else ChatGPTLightBorder
    val targetGoldLabel = if (isDark) NovaGoldLabel else ChatGPTLightAccent

    val userBubble by animateColorAsState(
        targetValue = targetUserBubble,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "userBubble"
    )
    val userBubbleBorder by animateColorAsState(
        targetValue = targetUserBubbleBorder,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "userBubbleBorder"
    )
    val goldLabel by animateColorAsState(
        targetValue = targetGoldLabel,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "goldLabel"
    )

    val customColors = NovaCustomColors(
        userBubble = userBubble,
        userBubbleBorder = userBubbleBorder,
        goldLabel = goldLabel,
        accentEmerald = ChatGPTLightAccent
    )

    // Adapt system status & navigation bar icons (dark icons on light theme, light on dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalIsDarkTheme provides isDark,
        LocalNovaCustomColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = animatedColorScheme,
            typography = Typography
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(animatedColorScheme.background)
            ) {
                content()

                // Subtle cinematic dissolve overlay that provides a gentle crossfade bloom during theme toggle
                val transitionAlpha = remember { Animatable(0f) }
                var previousDark by remember { mutableStateOf(isDark) }

                LaunchedEffect(isDark) {
                    if (previousDark != isDark) {
                        previousDark = isDark
                        transitionAlpha.animateTo(
                            targetValue = 0.16f,
                            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                        )
                        transitionAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                        )
                    }
                }

                if (transitionAlpha.value > 0.002f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                (if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF))
                                    .copy(alpha = transitionAlpha.value)
                            )
                    )
                }
            }
        }
    }
}
