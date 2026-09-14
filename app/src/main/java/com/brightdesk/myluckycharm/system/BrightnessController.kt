package com.brightdesk.myluckycharm.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Maps a pull onto screen brightness (spec §7).
 *
 * The pull is an *offset* from the brightness at the moment the charm was
 * grabbed, not a position on the scale — see [VolumeController] for why.
 *
 * Writes go to the system settings provider, which is far too expensive to hit
 * every frame, so they are throttled and de-duplicated.
 */
class BrightnessController(private val context: Context) {

    private var lastWriteAt = 0L
    private var lastValue = -1
    private var baseline: Int? = null

    /** Resolved once: it is a device config value, and it cannot change. */
    private val range by lazy { BrightnessRange.ofSystem() }

    /** Called when the charm is taken hold of, to anchor the adjustment. */
    fun beginAdjust() {
        baseline = currentBrightness()
        lastValue = -1
    }

    fun apply(offset: Float) {
        if (!SpecialPermissions.canWriteSystemSettings(context)) return

        val now = SystemClock.uptimeMillis()
        if (now - lastWriteAt < MIN_WRITE_INTERVAL_MS) return

        val from = baseline ?: currentBrightness() ?: range.min
        val span = range.max - range.min
        val value = (from + offset.coerceIn(-1f, 1f) * span)
            .roundToInt()
            .coerceIn(range.min, range.max)
        if (value == lastValue) return

        lastWriteAt = now
        lastValue = value

        val resolver = context.contentResolver
        // Auto-brightness silently overrides manual writes, so switch it off first.
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }

    private fun currentBrightness(): Int? = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (error: Settings.SettingNotFoundException) {
        null
    }

    private companion object {
        /** ~12 writes per second, within the spec's 10-15 guidance. */
        const val MIN_WRITE_INTERVAL_MS = 80L
    }
}
