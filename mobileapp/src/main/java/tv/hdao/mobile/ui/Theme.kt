package tv.hdao.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MobileInk = Color.Black
val MobilePanel = Color(0xFF181818)
val MobileAccent = Color.White
val MobileMuted = Color(0xFFB3B3B3)

private val MobileColors = darkColorScheme(
    primary = MobileAccent,
    onPrimary = MobileInk,
    secondary = MobileMuted,
    onSecondary = MobileInk,
    secondaryContainer = Color(0xFF2F2F2F),
    onSecondaryContainer = Color.White,
    tertiary = MobileAccent,
    onTertiary = MobileInk,
    background = MobileInk,
    onBackground = Color.White,
    surface = MobilePanel,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = MobileMuted,
    outline = Color(0xFF808080),
)

@Composable
fun HdaoMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors, content = content)
}
