package app.coolapk.kmp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = CoolapkGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CoolapkGreenContainer,
    onPrimaryContainer = Ink,
    secondary = CoolapkMint,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = CoolapkMintContainer,
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF093A21),
    tertiary = CoolapkAmber,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = CoolapkAmberContainer,
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF412900),
    surface = androidx.compose.ui.graphics.Color(0xFFF9FCF9),
    background = androidx.compose.ui.graphics.Color(0xFFF9FCF9),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF3F8F4),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEFF5F0),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE9F0EA),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE1EAE3),
    onSurface = Ink,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF435149),
    outline = androidx.compose.ui.graphics.Color(0xFF738278),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFC2CEC5),
)

private val DarkColors = darkColorScheme(
    primary = CoolapkGreenDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00391D),
    primaryContainer = CoolapkGreenContainerDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFB5F2C9),
    secondary = androidx.compose.ui.graphics.Color(0xFF8BD5A5),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF093A21),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF244B34),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFB6DDC0),
    tertiary = androidx.compose.ui.graphics.Color(0xFFEDC58E),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF462D00),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF5D421B),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDEAD),
    background = androidx.compose.ui.graphics.Color(0xFF101512),
    surface = androidx.compose.ui.graphics.Color(0xFF101512),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0B100D),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF151D18),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF19221C),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF232D26),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2D382F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E9E1),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB9C9BC),
    outline = androidx.compose.ui.graphics.Color(0xFF899B8D),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3D4A40),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun CoolapkKmpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = Typography(),
        content = content,
    )
}
