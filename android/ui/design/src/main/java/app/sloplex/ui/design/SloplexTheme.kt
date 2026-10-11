package app.sloplex.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared visual tokens for touch and ten-foot interfaces. */
object SloplexPalette {
    val Accent = Color(0xFFFFC66D)
    val OnAccent = Color(0xFF281A05)
    val Background = Color(0xFF0B0D12)
    val Surface = Color(0xFF13161E)
    val Elevated = Color(0xFF1D222D)
    val Border = Color(0xFF343A47)
    val Text = Color(0xFFF3F1EB)
    val Muted = Color(0xFFA8AEBC)
    val AccentContainer = Color(0xFF3D2E18)
}

val SloplexColors = darkColorScheme(
    primary = SloplexPalette.Accent, onPrimary = SloplexPalette.OnAccent,
    primaryContainer = SloplexPalette.AccentContainer, onPrimaryContainer = SloplexPalette.Accent,
    secondary = SloplexPalette.Muted, onSecondary = SloplexPalette.Background,
    secondaryContainer = SloplexPalette.Elevated, onSecondaryContainer = SloplexPalette.Text,
    background = SloplexPalette.Background, onBackground = SloplexPalette.Text,
    surface = SloplexPalette.Surface, onSurface = SloplexPalette.Text,
    surfaceVariant = SloplexPalette.Elevated, onSurfaceVariant = SloplexPalette.Muted,
    surfaceContainer = SloplexPalette.Surface, surfaceContainerHigh = SloplexPalette.Elevated,
    outline = SloplexPalette.Border, outlineVariant = SloplexPalette.Border,
)

private val SloplexTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
)

@Composable
fun SloplexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SloplexColors,
        typography = SloplexTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(36.dp).background(SloplexPalette.Accent, RoundedCornerShape(12.dp))) {
        val play = Path().apply {
            moveTo(size.width * 0.42f, size.height * 0.28f)
            lineTo(size.width * 0.73f, size.height * 0.5f)
            lineTo(size.width * 0.42f, size.height * 0.72f)
            close()
        }
        drawLine(SloplexPalette.OnAccent, Offset(size.width * 0.28f, size.height * 0.28f), Offset(size.width * 0.28f, size.height * 0.72f), size.width * 0.07f)
        drawPath(play, SloplexPalette.OnAccent)
    }
}
