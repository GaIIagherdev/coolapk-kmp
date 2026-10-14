package app.coolapk.kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.coolapk.kmp.ui.CoolapkApp
import app.coolapk.kmp.ui.theme.CoolapkKmpTheme
import app.coolapk.kmp.ui.theme.ThemeMode
import app.coolapk.kmp.ui.theme.ThemePreferenceStore
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.coolapk.kmp.core.initializePlatformGateway

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializePlatformGateway(applicationContext)
        enableEdgeToEdge()
        setContent {
            val store = remember { ThemePreferenceStore(this) }
            var themeMode by remember { mutableStateOf(store.load()) }
            CoolapkKmpTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                CoolapkApp(
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        store.save(it)
                    },
                )
            }
        }
    }
}
