package tv.hdao.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MobileInk = Color(0xFF090B10)
val MobilePanel = Color(0xFF151922)
val MobileGold = Color(0xFFF4C15D)
val MobileMuted = Color(0xFFADB3C1)

private val MobileColors = darkColorScheme(
    primary = MobileGold,
    onPrimary = MobileInk,
    background = MobileInk,
    onBackground = Color.White,
    surface = MobilePanel,
    onSurface = Color.White,
)

@Composable
fun HdaoMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MobileColors, content = content)
}
