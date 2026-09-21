package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun MicRippleButton(
    isListening: Boolean,
    rmsLevel: Float, // 0.0f to 1.0f
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "mic_transition")

    // Idle subtle breathing animation (only when idle)
    val idleBreathingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breathing"
    )

    // Expanding listening ring 1
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_scale"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_alpha"
    )

    // Expanding listening ring 2
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2_scale"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, delayMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2_alpha"
    )

    // Dynamic RMS reactive scale
    val rmsScale = remember { Animatable(1f) }
    LaunchedEffect(rmsLevel, isListening) {
        if (isListening) {
            val target = 1f + (rmsLevel * 0.18f)
            rmsScale.animateTo(target, animationSpec = tween(80, easing = FastOutSlowInEasing))
        } else {
            rmsScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.94f else 1.0f
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        // Expanding rings when listening
        if (isListening) {
            Canvas(modifier = Modifier.size(150.dp)) {
                val centerPt = this.center
                val baseRadius = 36.dp.toPx()

                // Expanding Ring 1
                drawCircle(
                    color = accentColor.copy(alpha = ring1Alpha),
                    radius = baseRadius * ring1Scale,
                    center = centerPt,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Expanding Ring 2
                drawCircle(
                    color = accentColor.copy(alpha = ring2Alpha),
                    radius = baseRadius * ring2Scale,
                    center = centerPt,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Hero 72dp Mic Button
        val baseScale = if (!enabled) 1.0f else if (isListening) rmsScale.value else idleBreathingScale

        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(baseScale * (if (enabled) pressScale else 1f))
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        isListening -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                )
                .testTag("mic_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                contentDescription = when {
                    !enabled -> "Voice unavailable on this device"
                    isListening -> "Stop listening"
                    else -> "Push to talk"
                },
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    isListening -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

