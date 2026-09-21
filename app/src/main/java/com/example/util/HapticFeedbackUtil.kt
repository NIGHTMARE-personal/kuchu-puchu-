package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Utility for tactile haptic feedback and gentle vibration pulses across
 * Android versions. Provides distinct feedback for sending messages,
 * attaching files, toggling speech, and UI interactions.
 */
object HapticFeedbackUtil {

    /**
     * Triggers a short vibration pulse of the given duration in milliseconds.
     */
    fun vibrateShort(context: Context, durationMs: Long = 30L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (vibrator?.hasVibrator() == true) {
                    val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                    return
                }
            }
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
            // Silently fallback if device lacks vibrator or in restricted context
        }
    }

    /**
     * Short vibration and haptic feedback when user sends a message.
     */
    fun onMessageSent(context: Context, view: View? = null) {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
        vibrateShort(context, 40L)
    }

    /**
     * Short vibration and haptic feedback when a file is successfully attached.
     */
    fun onAttachmentAdded(context: Context, view: View? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Exception) {}
        vibrateShort(context, 35L)
    }

    /**
     * Short vibration when toggling microphone / voice listening.
     */
    fun onMicToggled(context: Context, view: View? = null) {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
        vibrateShort(context, 25L)
    }

    /**
     * Standard subtle tap feedback for button presses.
     */
    fun onTap(context: Context, view: View? = null) {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}
        vibrateShort(context, 20L)
    }
}
