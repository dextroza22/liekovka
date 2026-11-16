package sk.kajka.liekovka.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8DF),
    onPrimaryContainer = Color(0xFF0B2E25),
    secondary = Color(0xFF4D6360),
    secondaryContainer = Color(0xFFE2ECE9),
    onSecondaryContainer = Color(0xFF1C2B29),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE6B8),
    onTertiaryContainer = Color(0xFF2C1C00),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F8F7),
    surface = Color(0xFFF6F8F7),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF2F0),
    surfaceContainerHigh = Color(0xFFE6ECEA),
    onSurface = Color(0xFF181D1B),
    onSurfaceVariant = Color(0xFF55605D),
    outlineVariant = Color(0xFFD3DBD8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3BD),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF105344),
    onPrimaryContainer = Color(0xFFCFE8DF),
    secondary = Color(0xFFB3CBC6),
    secondaryContainer = Color(0xFF2F403D),
    onSecondaryContainer = Color(0xFFE2ECE9),
    tertiary = Color(0xFFF2BE5C),
    tertiaryContainer = Color(0xFF5A3D00),
    onTertiaryContainer = Color(0xFFFFE6B8),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111614),
    surface = Color(0xFF111614),
    surfaceContainerLow = Color(0xFF181E1C),
    surfaceContainer = Color(0xFF1D2321),
    surfaceContainerHigh = Color(0xFF272E2B),
    onSurface = Color(0xFFE0E4E2),
    onSurfaceVariant = Color(0xFFB9C3C0),
    outlineVariant = Color(0xFF3B4542),
)

/** Či sa má použiť tmavá téma podľa nastavenia ("system", "svetla", "tmava"). */
@Composable
fun jeTmava(tema: String): Boolean = when (tema) {
    "svetla" -> false
    "tmava" -> true
    else -> isSystemInDarkTheme()
}

private val Typo = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.3.sp),
    )
}

/**
 * Téma aplikácie. [pismo] násobí veľkosť písma nastavenú v systéme,
 * takže staršie osoby si môžu text zväčšiť priamo v aplikácii.
 */
@Composable
fun LiekovkaTheme(tema: String = "system", pismo: Float = 1f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    MaterialTheme(colorScheme = if (jeTmava(tema)) Dark else Light, typography = Typo) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * pismo), content = content)
    }
}
