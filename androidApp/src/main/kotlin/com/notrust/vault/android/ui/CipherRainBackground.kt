package com.notrust.vault.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.notrust.vault.android.ui.theme.VaultColors
import com.notrust.vault.android.ui.theme.VaultMonoTextStyle
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 26
private const val LOOP_MILLIS = 42_000
private val GLYPHS = "01 23 45 67 89 AB CD EF ◈ ⬡ ∴ ⧉".split(" ")

private data class CipherParticle(
    val x: Float,       // 0..1 fraction of width
    val startY: Float,  // 0..1 fraction of height, initial position
    val speed: Float,   // fraction-of-height per full loop
    val glyph: String,
    val violet: Boolean,
    val scale: Float
)

/**
 * A slow field of drifting cipher glyphs — hex pairs and a handful of
 * geometric marks, not falling green text. This is atmosphere behind an
 * unlock form, not the focal point: low opacity, slow, sparse. See
 * docs/UI_DESIGN.md for why this exists and what it's deliberately not.
 */
@Composable
fun CipherRainBackground(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val particles = remember {
        val random = Random(42) // fixed seed: a stable, considered layout, not literal randomness each recomposition
        List(PARTICLE_COUNT) {
            CipherParticle(
                x = random.nextFloat(),
                startY = random.nextFloat(),
                speed = 0.35f + random.nextFloat() * 0.65f,
                glyph = GLYPHS[random.nextInt(GLYPHS.size)],
                violet = random.nextInt(4) == 0,
                scale = 0.75f + random.nextFloat() * 0.85f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cipherRain")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LOOP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier) {
        particles.forEach { particle ->
            drawParticle(particle, time, textMeasurer)
        }
    }
}

private fun DrawScope.drawParticle(
    particle: CipherParticle,
    time: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    // Position loops top-to-bottom; wraps seamlessly by taking the
    // fractional part, so there's no visible reset/jump.
    val progress = (particle.startY + time * particle.speed).mod(1f)
    val y = progress * size.height

    // Fade in/out near the top and bottom edges rather than popping,
    // and breathe slightly via a sine so the field feels alive, not static.
    val edgeFade = (sin(progress * PI).toFloat()).coerceIn(0f, 1f)
    val breathe = 0.75f + 0.25f * sin((time * 2 * PI + particle.x * 10).toFloat())
    val alpha = (0.09f + 0.10f * breathe) * edgeFade

    val color = if (particle.violet) VaultColors.Depth2 else VaultColors.Signal
    val style: TextStyle = VaultMonoTextStyle.copy(fontSize = (13 * particle.scale).sp, color = color)
    val layout = textMeasurer.measure(particle.glyph, style)

    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = Offset(particle.x * size.width - layout.size.width / 2f, y),
        alpha = alpha.coerceIn(0f, 1f)
    )
}
