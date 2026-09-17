package tv.hdao.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import tv.hdao.mobile.ui.HdaoMobileApp
import tv.hdao.mobile.ui.HdaoMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { HdaoMobileTheme { HdaoMobileApp() } }
    }
}
