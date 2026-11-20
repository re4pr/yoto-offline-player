package com.homelab.yototablet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ParticleSeed(
    val xNorm: Float,
    val yNorm: Float,
    val speed: Float,
    val size: Float,
    val driftAmp: Float,
    val phase: Float,
    val alpha: Float,
)

@Composable
fun MagicalBackdrop(
    particleMode: String,
    accentColor: Color,
    secondaryColor: Color,
    surfaceTint: Color,
    modifier: Modifier = Modifier,
) {
    val seeds = remember(particleMode) {
        val rng = Random(particleMode.hashCode())
        List(24) {
            ParticleSeed(
                xNorm = rng.nextFloat(),
                yNorm = rng.nextFloat(),
                speed = 0.022f + rng.nextFloat() * 0.04f,
                size = 3.5f + rng.nextFloat() * 7.0f,
                driftAmp = 14f + rng.nextFloat() * 26f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                alpha = 0.30f + rng.nextFloat() * 0.45f,
            )
        }
    }

    var elapsedNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(particleMode) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                elapsedNanos = now - startNanos
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C16))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            surfaceTint.copy(alpha = 0.92f),
                            Color(0xFF080A12),
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to secondaryColor.copy(alpha = 0.12f),
                        0.45f to Color.Transparent,
                        1.0f to Color(0xDC06070D),
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = elapsedNanos / 1_000_000_000f
            val w = size.width
            val h = size.height
            seeds.forEachIndexed { idx, s ->
                val color = if (idx % 2 == 0) accentColor else secondaryColor
                when (particleMode) {
                    "bubbles" -> drawBubble(s, t, w, h, color)
                    "snowflakes" -> drawSnowflake(s, t, w, h)
                    "fireflies" -> drawFirefly(s, t, w, h, color)
                    "stars" -> drawTwinkleStar(s, t, w, h, color)
                    else -> drawSparkle(s, t, w, h, color)
                }
            }
        }
    }
}

private fun DrawScope.drawBubble(s: ParticleSeed, t: Float, w: Float, h: Float, color: Color) {
    val rawY = (s.yNorm - t * s.speed) % 1.1f
    val y = (if (rawY < 0f) rawY + 1.1f else rawY) * h
    val x = s.xNorm * w + sin(t * 1.2f + s.phase) * s.driftAmp
    val r = s.size * 1.35f
    drawCircle(color = color.copy(alpha = s.alpha * 0.24f), radius = r, center = Offset(x, y))
    drawCircle(
        color = Color.White.copy(alpha = s.alpha * 0.42f),
        radius = r * 0.30f,
        center = Offset(x - r * 0.3f, y - r * 0.3f),
    )
}

private fun DrawScope.drawSnowflake(s: ParticleSeed, t: Float, w: Float, h: Float) {
    val y = ((s.yNorm + t * s.speed * 0.85f) % 1.1f) * h
    val x = s.xNorm * w + sin(t * 1.1f + s.phase) * s.driftAmp
    drawCircle(color = Color.White.copy(alpha = s.alpha * 0.20f), radius = s.size * 1.4f, center = Offset(x, y))
    drawCircle(color = Color(0xFFE0F2FE).copy(alpha = s.alpha * 0.82f), radius = s.size * 0.52f, center = Offset(x, y))
}

private fun DrawScope.drawFirefly(s: ParticleSeed, t: Float, w: Float, h: Float, color: Color) {
    val x = s.xNorm * w + cos(t * 0.7f + s.phase) * s.driftAmp * 1.4f
    val y = s.yNorm * h + sin(t * 0.9f + s.phase * 1.3f) * s.driftAmp
    val pulse = 0.4f + 0.6f * ((sin(t * 2.4f + s.phase) + 1f) * 0.5f)
    drawCircle(color = color.copy(alpha = s.alpha * 0.25f * pulse), radius = s.size * 2.1f, center = Offset(x, y))
    drawCircle(color = Color(0xFFFFFCE0).copy(alpha = s.alpha * 0.88f * pulse), radius = s.size * 0.5f, center = Offset(x, y))
}

private fun DrawScope.drawSparkle(s: ParticleSeed, t: Float, w: Float, h: Float, color: Color) {
    val x = s.xNorm * w + sin(t * 0.5f + s.phase) * (s.driftAmp * 0.6f)
    val y = s.yNorm * h + cos(t * 0.4f + s.phase) * (s.driftAmp * 0.6f)
    val pulse = 0.35f + 0.65f * ((sin(t * 2.8f + s.phase) + 1f) * 0.5f)
    drawFourPointStar(Offset(x, y), s.size * 1.25f * pulse, color.copy(alpha = s.alpha * pulse))
}

private fun DrawScope.drawTwinkleStar(s: ParticleSeed, t: Float, w: Float, h: Float, color: Color) {
    val x = s.xNorm * w + cos(t * 0.35f + s.phase) * (s.driftAmp * 0.4f)
    val y = s.yNorm * h + sin(t * 0.45f + s.phase) * (s.driftAmp * 0.4f)
    val pulse = 0.4f + 0.6f * ((sin(t * 3.2f + s.phase) + 1f) * 0.5f)
    drawCircle(color = color.copy(alpha = s.alpha * 0.22f * pulse), radius = s.size * 1.85f, center = Offset(x, y))
    drawFourPointStar(Offset(x, y), s.size * 1.3f * pulse, Color(0xFFFFFBE6).copy(alpha = s.alpha * pulse))
}

private fun DrawScope.drawFourPointStar(center: Offset, outerR: Float, color: Color) {
    val innerR = outerR * 0.32f
    val path = Path().apply {
        for (i in 0 until 8) {
            val angle = (i * PI / 4.0) - (PI / 2.0)
            val r = if (i % 2 == 0) outerR else innerR
            val px = center.x + (cos(angle) * r).toFloat()
            val py = center.y + (sin(angle) * r).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, color = color)
}
