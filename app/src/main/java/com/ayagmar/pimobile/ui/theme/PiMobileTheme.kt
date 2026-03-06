@file:Suppress("MagicNumber")

package com.ayagmar.pimobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Monospace font family used for code blocks, diffs, bash output, and inline code.
 * Centralised here so a bundled font (e.g. JetBrains Mono) can be swapped in later.
 */
val PiCodeFontFamily: FontFamily = FontFamily.Monospace

// ---------------------------------------------------------------------------
// Color schemes
// ---------------------------------------------------------------------------

private val PiLightColors =
    lightColorScheme(
        primary = Color(0xFF3559E0),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF5F5B71),
        tertiary = Color(0xFF7A4D9A),
        error = Color(0xFFB3261E),
    )

private val PiDarkColors =
    darkColorScheme(
        primary = Color(0xFFB8C3FF),
        onPrimary = Color(0xFF00237A),
        secondary = Color(0xFFC9C4DD),
        tertiary = Color(0xFFE7B6FF),
        error = Color(0xFFF2B8B5),
    )

// ---------------------------------------------------------------------------
// Semantic chat colours
// ---------------------------------------------------------------------------

/**
 * Semantic colour tokens for the chat timeline. Consuming code uses
 * `LocalChatColors.current` instead of reaching into `colorScheme` directly,
 * which makes the role-based intent explicit and easy to change globally.
 */
@Immutable
data class ChatColors(
    // User message
    val userContainer: Color,
    val onUserContainer: Color,
    // Assistant message
    val assistantContainer: Color,
    val onAssistantContainer: Color,
    val assistantAccent: Color,
    // Thinking block
    val thinkingContainer: Color,
    val onThinkingContainer: Color,
    val thinkingBorder: Color,
    // Tool card
    val toolContainer: Color,
    val onToolContainer: Color,
    // Code block surface
    val codeSurface: Color,
)

val LocalChatColors = staticCompositionLocalOf<ChatColors> {
    error("No ChatColors provided – wrap your UI in PiMobileTheme")
}

private fun lightChatColors(scheme: androidx.compose.material3.ColorScheme) =
    ChatColors(
        userContainer = scheme.secondaryContainer,
        onUserContainer = scheme.onSecondaryContainer,
        assistantContainer = scheme.surface,
        onAssistantContainer = scheme.onSurface,
        assistantAccent = scheme.primary,
        thinkingContainer = scheme.tertiaryContainer.copy(alpha = 0.6f),
        onThinkingContainer = scheme.onTertiaryContainer,
        thinkingBorder = scheme.outlineVariant,
        toolContainer = scheme.surfaceVariant,
        onToolContainer = scheme.onSurfaceVariant,
        codeSurface = scheme.surfaceVariant.copy(alpha = 0.85f),
    )

private fun darkChatColors(scheme: androidx.compose.material3.ColorScheme) =
    ChatColors(
        userContainer = scheme.secondaryContainer,
        onUserContainer = scheme.onSecondaryContainer,
        assistantContainer = scheme.surface,
        onAssistantContainer = scheme.onSurface,
        assistantAccent = scheme.primary,
        thinkingContainer = scheme.tertiaryContainer.copy(alpha = 0.6f),
        onThinkingContainer = scheme.onTertiaryContainer,
        thinkingBorder = scheme.outlineVariant,
        toolContainer = scheme.surfaceVariant,
        onToolContainer = scheme.onSurfaceVariant,
        codeSurface = scheme.surfaceVariant.copy(alpha = 0.85f),
    )

// ---------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------

/**
 * Custom typography with tighter line heights for the chat timeline.
 *
 * Material 3 defaults use ~1.5× line height which wastes vertical space in a
 * messaging UI. We tighten body and label styles to ~1.3–1.35× so messages
 * feel denser without hurting readability.
 */
private val PiTypography = Typography(
    // Headings (used in markdown H1-H2, sheet titles)
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,     // default 32
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,     // default 24
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,     // default 20
        letterSpacing = 0.1.sp,
    ),
    // Body (primary chat text)
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,     // default 20 — slightly tighter
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Labels (metadata, timestamps, chips)
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,     // default 20
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,     // default 16
        letterSpacing = 0.5.sp,
    ),
)

// ---------------------------------------------------------------------------
// Theme entry-point
// ---------------------------------------------------------------------------

@Composable
fun PiMobileTheme(
    themePreference: ThemePreference,
    content: @Composable () -> Unit,
) {
    val useDarkTheme =
        when (themePreference) {
            ThemePreference.SYSTEM -> isSystemInDarkTheme()
            ThemePreference.LIGHT -> false
            ThemePreference.DARK -> true
        }

    val colorScheme = if (useDarkTheme) PiDarkColors else PiLightColors
    val chatColors = if (useDarkTheme) darkChatColors(PiDarkColors) else lightChatColors(PiLightColors)

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PiTypography,
            content = content,
        )
    }
}
