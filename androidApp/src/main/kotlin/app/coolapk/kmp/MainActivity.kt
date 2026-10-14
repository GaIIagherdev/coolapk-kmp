package app.coolapk.kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.coolapk.kmp.ui.CoolapkApp
import app.coolapk.kmp.ui.theme.CoolapkKmpTheme
import com.coolapk.kmp.core.initializePlatformGateway

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializePlatformGateway(applicationContext)
        enableEdgeToEdge()
        setContent {
            CoolapkKmpTheme {
                CoolapkApp()
            }
        }
    }
}
