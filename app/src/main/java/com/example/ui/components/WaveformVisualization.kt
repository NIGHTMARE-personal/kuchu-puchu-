package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Subtle waveform visualization rendered on Compose Canvas.
 * Reacts dynamically to microphone RMS input levels while user speaks,
 * modulated with a gentle fluid phase oscillation.
 */
@Composable
fun WaveformVisualization(
    rmsLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    waveformHeight: Dp = 38.dp,
    waveformWidth: Dp = 220.dp,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    AnimatedVisibility(
        visible = isListening,
        enter = fadeIn(tween(200)) + expandVertically(tween(250)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Smoothly animate the target RMS input level
            val animatedRms by animateFloatAsState(
                targetValue = if (isListening) rmsLevel.coerceIn(0.08f, 1f) else 0f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = 380f
                ),
                label = "waveform_rms"
            )

            // Fluid continuous phase for natural organic wave motion
            val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "phase_angle"
            )

            Canvas(
                modifier = Modifier
                    .width(waveformWidth)
                    .height(waveformHeight)
                    .testTag("waveform_canvas")
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalBars = barCount.coerceAtLeast(8)
                val spacingFraction = 0.45f
                val totalSlotWidth = canvasWidth / totalBars
                val barWidth = totalSlotWidth * (1f - spacingFraction)
                val barSpacing = totalSlotWidth * spacingFraction
                val minBarHeight = 4.dp.toPx()
                val cornerRadiusPx = barWidth / 2f

                for (i in 0 until totalBars) {
                    val x = i * (barWidth + barSpacing) + barSpacing / 2f
                    // Distance from center ratio: 1.0 at center, 0.0 at outer edges
                    val centerNorm = abs((i - (totalBars - 1) / 2f) / ((totalBars - 1) / 2f))
                    val centerWeight = 1f - (centerNorm * 0.65f)

                    // Sinusoidal wave ripple offset by bar position
                    val waveOffset = sin(phase + (i * 0.38f)) * 0.3f + 0.7f

                    // Effective bar amplitude modulated by microphone RMS level
                    val amplitude = (animatedRms * 0.9f + 0.1f) * centerWeight * waveOffset.toFloat()
                    val calculatedHeight = (canvasHeight * amplitude).coerceIn(minBarHeight, canvasHeight)
                    val y = (canvasHeight - calculatedHeight) / 2f

                    // Subtle alpha gradient: slightly softer at edges
                    val barAlpha = (0.55f + (0.45f * centerWeight)).coerceIn(0.3f, 1f)

                    drawRoundRect(
                        color = barColor.copy(alpha = barAlpha),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, calculatedHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                }
            }
        }
    }
}
