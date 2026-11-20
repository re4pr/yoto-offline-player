package com.homelab.yototablet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.LocalDate

data class MagicalWorld(
    val id: String,
    val label: String,
    val particleMode: String,
    val accentColor: Color,
    val secondaryColor: Color,
    val surfaceTint: Color,
)

val MAGICAL_WORLDS = listOf(
    MagicalWorld(
        id = "princess",
        label = "Princess Castle",
        particleMode = "sparkles",
        accentColor = Color(0xFFFF9CE3),
        secondaryColor = Color(0xFFFFD97D),
        surfaceTint = Color(0xFF1B1530),
    ),
    MagicalWorld(
        id = "mermaid",
        label = "Mermaid Lagoon",
        particleMode = "bubbles",
        accentColor = Color(0xFF5EEAD4),
        secondaryColor = Color(0xFFF0ABFC),
        surfaceTint = Color(0xFF101F2E),
    ),
    MagicalWorld(
        id = "unicorn",
        label = "Unicorn Dreamland",
        particleMode = "stars",
        accentColor = Color(0xFFE879F9),
        secondaryColor = Color(0xFFFDE68A),
        surfaceTint = Color(0xFF1E132E),
    ),
    MagicalWorld(
        id = "frozen",
        label = "Frozen Ice Palace",
        particleMode = "snowflakes",
        accentColor = Color(0xFF7DD3FC),
        secondaryColor = Color(0xFFC4B5FD),
        surfaceTint = Color(0xFF0F1B33),
    ),
    MagicalWorld(
        id = "fairy",
        label = "Fairy Garden",
        particleMode = "fireflies",
        accentColor = Color(0xFFA7F3D0),
        secondaryColor = Color(0xFFFDE68A),
        surfaceTint = Color(0xFF122421),
    ),
)

fun resolveMagicalWorld(savedId: String?): MagicalWorld {
    if (!savedId.isNullOrBlank()) {
        MAGICAL_WORLDS.firstOrNull { it.id == savedId }?.let { return it }
    }
    val dayIndex = LocalDate.now().dayOfYear % MAGICAL_WORLDS.size
    return MAGICAL_WORLDS[dayIndex]
}

fun nextMagicalWorld(currentId: String): MagicalWorld {
    val idx = MAGICAL_WORLDS.indexOfFirst { it.id == currentId }
    if (idx < 0) return MAGICAL_WORLDS.first()
    return MAGICAL_WORLDS[(idx + 1) % MAGICAL_WORLDS.size]
}

private val TabletTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

@Composable
fun YotoTabletTheme(
    world: MagicalWorld,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = world.accentColor,
        secondary = world.secondaryColor,
        background = Color(0xFF0A0C16),
        surface = world.surfaceTint,
        onPrimary = Color(0xFF0A0B10),
        onSecondary = Color(0xFF0A0B10),
        onBackground = Color.White,
        onSurface = Color.White,
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = TabletTypography,
        content = content,
    )
}
