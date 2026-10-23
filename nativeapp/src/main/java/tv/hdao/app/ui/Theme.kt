package tv.hdao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color.Black
val Panel = Color(0xFF181818)
val Accent = Color.White
val Muted = Color(0xFFB3B3B3)
val Rating = Color(0xFF46D369)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    secondary = Muted,
)

@Composable
fun HdaoTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
