package app.wichtelein2.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WichtelLight = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF2E6B4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB7F1CE),
    onSecondaryContainer = Color(0xFF002111),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFF2DFD9),
    onSurfaceVariant = Color(0xFF53433F),
)

val WichtelDark = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFF9CD5B3),
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF14513A),
    onSecondaryContainer = Color(0xFFB7F1CE),
    background = Color(0xFF181210),
    onBackground = Color(0xFFEDE0DC),
    surface = Color(0xFF181210),
    onSurface = Color(0xFFEDE0DC),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
)

/** themeMode: 0 = wie das Handy, 1 = hell, 2 = dunkel. */
fun isDark(themeMode: Int, system: Boolean): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> system
}

@Composable
fun WichtelTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = isDark(themeMode, isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) WichtelDark else WichtelLight, content = content)
}
