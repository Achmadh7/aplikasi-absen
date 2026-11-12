package id.absensi.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** R1 Kaca Aurora — tema gelap utama aplikasi Absensi. */
val AuroraNavy = Color(0xFF070B14)
val AuroraSurface = Color(0xFF0E1626)
val AuroraCard = Color(0xFF16233A)
val AuroraOutline = Color(0xFF22364F)
val AuroraCyan = Color(0xFF40C4FF)
val AuroraViolet = Color(0xFFA78BFA)
val AuroraTeal = Color(0xFF2DD4BF)

private val DarkScheme = darkColorScheme(
    primary = AuroraCyan,
    secondary = AuroraViolet,
    tertiary = AuroraTeal,
    background = AuroraNavy,
    surface = AuroraSurface,
    surfaceVariant = AuroraCard,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFA0AFC3),
    outlineVariant = AuroraOutline
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF6D28D9),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFEDF2F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EDF3),
    onBackground = Color(0xFF0F1B2D),
    onSurface = Color(0xFF0F1B2D),
    onSurfaceVariant = Color(0xFF5A6878),
    outlineVariant = Color(0xFFDDE5EE)
)

/** Latar aurora: 3 blob radial (cyan / violet / teal) di atas warna background. */
@Composable
fun AuroraBackground(isDark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) AuroraNavy else Color(0xFFEDF2F8))
    ) {
        if (isDark) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(AuroraCyan.copy(alpha = 0.20f), AuroraCyan.copy(alpha = 0f)),
                        center = Offset(size.width * 0.88f, size.height * 0.04f),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(size.width * 0.88f, size.height * 0.04f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(AuroraViolet.copy(alpha = 0.18f), AuroraViolet.copy(alpha = 0f)),
                        center = Offset(size.width * 0.06f, size.height * 0.38f),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(size.width * 0.06f, size.height * 0.38f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(AuroraTeal.copy(alpha = 0.14f), AuroraTeal.copy(alpha = 0f)),
                        center = Offset(size.width * 0.92f, size.height * 0.85f),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(size.width * 0.92f, size.height * 0.85f)
                )
            }
        }
        content()
    }
}

@Composable
fun AbsensiTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
