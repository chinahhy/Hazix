package tv.hdao.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090B10)
val Panel = Color(0xFF151922)
val Gold = Color(0xFFF4C15D)
val Muted = Color(0xFFADB3C1)

private val colors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    secondary = Color(0xFF70C1B3),
)

@Composable
fun HdaoTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
