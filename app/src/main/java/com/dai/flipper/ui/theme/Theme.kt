package com.dai.flipper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════
// DAI BRAND — Electric Teal / Deep Slate
// ═══════════════════════════════════════════════════════════

// Primary: Electric Teal — the signal in the dark
val DaiWine = Color(0xFF0E9488)           // Deep teal
val DaiWineLight = Color(0xFF2DD4C0)      // Lighter teal for highlights
val DaiWineDark = Color(0xFF0A6D63)        // Darker teal for pressed states

// Legacy alias — keeps existing references working
val DaiOrange = DaiWine
val DaiOrangeDark = DaiWineDark

// Secondary: Deep Slate — cold steel sophistication
val DaiSecondary = Color(0xFF141B2E)       // Slate blue-black
val DaiGunmetal = Color(0xFF212B45)         // Lighter slate

// Accent: Warm Copper — contrast against the teal
val DaiAccent = Color(0xFFE8A33D)           // Warm copper/amber
val DaiGold = Color(0xFFE8A33D)
val DaiGoldMuted = Color(0xFFB07E2E)        // Muted copper for secondary accents

// Surface Colors — deep noir palette
val DaiSurface = Color(0xFF0B1016)
val DaiSurfaceVariant = Color(0xFF121A24)
val DaiBackground = Color(0xFF05080C)
val DaiBackgroundDeep = Color(0xFF03050A)
val DaiBackgroundGlow = Color(0xFF081414)   // Very subtle teal tint

val DaiBackdropBrush = Brush.verticalGradient(
    colors = listOf(
        DaiBackground,
        DaiBackgroundDeep,
        DaiBackgroundGlow,
        DaiBackground
    )
)

// Risk Colors — refined
val RiskLow = Color(0xFF4CAF7D)
val RiskMedium = Color(0xFFD4AF37)   // Gold for medium — on brand
val RiskHigh = Color(0xFFCF4455)     // Wine-adjacent red
val RiskBlocked = Color(0xFF6B7394)

// Diff Colors
val DiffAdded = Color(0xFF3CBF88)
val DiffRemoved = Color(0xFFCF4455)
val DiffChanged = Color(0xFFD4AF37)
val DiffAddedBackground = Color(0x403CBF88)
val DiffRemovedBackground = Color(0x40CF4455)

// Chat Colors — distinct from system
val ChatAssistant = Color(0xFF161B28)       // Dark gunmetal bubble
val ChatUser = DaiWine                    // Wine bubble for user
val ChatTool = Color(0xFF121620)             // Even darker for tool results
val ChatToolAccent = DaiGold              // Gold for tool highlights

private val DarkColorScheme = darkColorScheme(
    primary = DaiWine,
    onPrimary = Color(0xFFF5E6EC),
    primaryContainer = DaiWineDark,
    onPrimaryContainer = Color(0xFFF8E0EC),
    secondary = DaiSecondary,
    onSecondary = Color(0xFFCAD0E0),
    secondaryContainer = DaiSurfaceVariant,
    onSecondaryContainer = Color(0xFFD5DEF0),
    tertiary = DaiGold,
    onTertiary = Color(0xFF1A1508),
    background = DaiBackground,
    onBackground = Color(0xFFE6E8F0),
    surface = DaiSurface,
    onSurface = Color(0xFFE4E6EE),
    surfaceVariant = DaiSurfaceVariant,
    onSurfaceVariant = Color(0xFF8E96AE),
    outline = Color(0xFF2E3548),
    outlineVariant = Color(0xFF1E2536),
    error = RiskHigh,
    onError = Color(0xFF200909)
)

private val LightColorScheme = lightColorScheme(
    primary = DaiWine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5D6E4),
    onPrimaryContainer = Color(0xFF4D1830),
    secondary = DaiSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE0F0),
    onSecondaryContainer = Color(0xFF1A1F2E),
    tertiary = DaiGold,
    onTertiary = Color(0xFF1A1508),
    background = Color(0xFFF4F2F5),
    onBackground = Color(0xFF121520),
    surface = Color.White,
    onSurface = Color(0xFF111420),
    surfaceVariant = Color(0xFFEBE8EE),
    onSurfaceVariant = Color(0xFF504862),
    outline = Color(0xFF706680),
    outlineVariant = Color(0xFFBEB4C8),
    error = RiskHigh,
    onError = Color.White
)

private val DaiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

private val BaseTypography = androidx.compose.material3.Typography()

val DaiTypography = BaseTypography.copy(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        letterSpacing = 1.2.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        letterSpacing = 0.6.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        letterSpacing = 0.3.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun DaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DaiTypography,
        shapes = DaiShapes,
        content = content
    )
}
