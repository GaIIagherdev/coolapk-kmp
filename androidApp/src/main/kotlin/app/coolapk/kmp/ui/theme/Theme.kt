package app.coolapk.kmp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = CoolapkGreen,
    onPrimary = OnPrimaryLight,
    primaryContainer = CoolapkGreenContainer,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = CoolapkMint,
    onSecondary = OnSecondaryLight,
    secondaryContainer = CoolapkMintContainer,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = CoolapkAmber,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = CoolapkAmberContainer,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceHighLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceLowestLight,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    inverseSurface = Ink,
    inverseOnSurface = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = CoolapkGreenDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = CoolapkGreenContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = CoolapkMintDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = CoolapkMintContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = CoolapkAmberDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = CoolapkAmberContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    inverseSurface = InkLight,
    inverseOnSurface = SurfaceDark,
)

internal val CoolapkShapes = Shapes(
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
        shapes = CoolapkShapes,
        typography = CoolapkTypography,
        content = content,
    )
}
