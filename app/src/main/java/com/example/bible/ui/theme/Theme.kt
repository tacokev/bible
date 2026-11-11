package com.example.bible.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.bible.R

/** A bright face colour with its darker "lip" used for the 3D pop effect. */
data class Pop(val face: Color, val edge: Color)

enum class ThemeMode(val label: String) { System("Auto"), Light("Light"), Dark("Dark"), Black("Black") }

/** Neutral colours that change between light and dark mode. Accent colours stay vivid in both. */
data class Neutrals(
    val isDark: Boolean,
    val background: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val snow: Color,
    val card: Pop,
    val locked: Pop,
    val selected: Color,
    val selectedBorder: Color,
)

private val LightNeutrals = Neutrals(
    isDark = false,
    background = Color.White,
    ink = Color(0xFF3C3C3C),
    muted = Color(0xFF777777),
    faint = Color(0xFFAFAFAF),
    line = Color(0xFFE5E5E5),
    snow = Color(0xFFF7F7F7),
    card = Pop(Color.White, Color(0xFFE5E5E5)),
    locked = Pop(Color(0xFFE5E5E5), Color(0xFFCECECE)),
    selected = Color(0xFFDDF4FF),
    selectedBorder = Color(0xFF84D8FF),
)

private val DarkNeutrals = Neutrals(
    isDark = true,
    background = Color(0xFF131F24),
    ink = Color(0xFFF1F7FB),
    muted = Color(0xFFB4C2CA),
    faint = Color(0xFF6F8691),
    line = Color(0xFF37464F),
    snow = Color(0xFF202F36),
    card = Pop(Color(0xFF131F24), Color(0xFF37464F)),
    locked = Pop(Color(0xFF37464F), Color(0xFF2A383F)),
    selected = Color(0xFF1B3644),
    selectedBorder = Color(0xFF3F85A7),
)

/** Pure black for OLED screens: black pixels are switched off. Greys are kept dim so it stays dark. */
private val BlackNeutrals = Neutrals(
    isDark = true,
    background = Color.Black,
    ink = Color(0xFFF2F2F2),
    muted = Color(0xFFA6A6A6),
    faint = Color(0xFF6E6E6E),
    line = Color(0xFF262626),
    snow = Color(0xFF111111),
    card = Pop(Color.Black, Color(0xFF262626)),
    locked = Pop(Color(0xFF262626), Color(0xFF171717)),
    selected = Color(0xFF0A2533),
    selectedBorder = Color(0xFF2C7AA0),
)

val LocalNeutrals = staticCompositionLocalOf { LightNeutrals }

object Palette {
    val Green = Pop(Color(0xFF58CC02), Color(0xFF46A302))
    val Blue = Pop(Color(0xFF1CB0F6), Color(0xFF1899D6))
    val Purple = Pop(Color(0xFFCE82FF), Color(0xFFA568CC))
    val Orange = Pop(Color(0xFFFF9600), Color(0xFFCC7900))
    val Red = Pop(Color(0xFFFF4B4B), Color(0xFFEA2B2B))
    val Gold = Pop(Color(0xFFFFC800), Color(0xFFE5A400))
    val Teal = Pop(Color(0xFF00CD9C), Color(0xFF00A57D))
    val Pink = Pop(Color(0xFFFF86D0), Color(0xFFDB6BB1))

    val units = listOf(Green, Blue, Purple, Orange, Teal, Pink, Red, Gold)

    val Flame = Color(0xFFFF9600)

    val Background: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.background
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.muted
    val Faint: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.faint
    val Line: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.line
    val Snow: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.snow
    val Selected: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.selected
    val SelectedBorder: Color @Composable @ReadOnlyComposable get() = LocalNeutrals.current.selectedBorder

    /** Outlined card / secondary button surface (white in light mode, background-coloured in dark). */
    val Card: Pop @Composable @ReadOnlyComposable get() = LocalNeutrals.current.card
    val Locked: Pop @Composable @ReadOnlyComposable get() = LocalNeutrals.current.locked
}

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

val ScriptureFont = FontFamily.Serif

private fun typography(ink: Color): Typography {
    val base = TextStyle(fontFamily = Nunito, color = ink)
    return Typography(
        displayLarge = base.copy(fontSize = 48.sp, fontWeight = FontWeight.Black),
        headlineLarge = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Black),
        headlineMedium = base.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
        titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
        titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
        bodyLarge = base.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
        bodyMedium = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
        labelMedium = base.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Black -> true
}

@Composable
fun BibleTheme(dark: Boolean = isSystemInDarkTheme(), black: Boolean = false, content: @Composable () -> Unit) {
    val n = when {
        dark && black -> BlackNeutrals
        dark -> DarkNeutrals
        else -> LightNeutrals
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.Green.face, secondary = Palette.Blue.face,
            background = n.background, surface = n.background, surfaceContainerLow = n.background,
            onSurface = n.ink, onBackground = n.ink,
        )
    } else {
        lightColorScheme(
            primary = Palette.Green.face, secondary = Palette.Blue.face,
            background = n.background, surface = n.background, surfaceContainerLow = n.background,
            onSurface = n.ink, onBackground = n.ink,
        )
    }
    CompositionLocalProvider(LocalNeutrals provides n) {
        MaterialTheme(colorScheme = scheme, typography = typography(n.ink), content = content)
    }
}
