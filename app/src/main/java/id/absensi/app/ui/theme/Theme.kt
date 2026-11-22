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

/** Palet Bento Grid Modular (Opsi 1) + fallback Aurora. */
val AuroraNavy = Color(0xFF070B14)
val AuroraSurface = Color(0xFF0E1626)
val AuroraCard = Color(0xFF16233A)
val AuroraOutline = Color(0xFF22364F)
val AuroraCyan = Color(0xFF40C4FF)
val AuroraViolet = Color(0xFFA78BFA)
val AuroraTeal = Color(0xFF2DD4BF)

val BentoBackground = Color(0xFFF4F6FB)
val BentoSurface = Color(0xFFFFFFFF)
val BentoOutline = Color(0xFFE5E7EB)
val BentoIndigo = Color(0xFF4F46E5)
val BentoIndigoLight = Color(0xFF6366F1)
val BentoEmerald = Color(0xFF10B981)
val BentoSlate = Color(0xFF0F172A)
val BentoSlateMuted = Color(0xFF64748B)

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
    primary = BentoIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = BentoIndigo,
    secondary = BentoEmerald,
    onSecondary = Color.White,
    tertiary = Color(0xFF0F766E),
    background = BentoBackground,
    surface = BentoSurface,
    surfaceVariant = Color(0xFFF8FAFC),
    onBackground = BentoSlate,
    onSurface = BentoSlate,
    onSurfaceVariant = BentoSlateMuted,
    outlineVariant = BentoOutline
)

/** Latar aplikasi: Bento off-white pada mode terang, aurora pada mode gelap. */
@Composable
fun AuroraBackground(isDark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) AuroraNavy else BentoBackground)
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
