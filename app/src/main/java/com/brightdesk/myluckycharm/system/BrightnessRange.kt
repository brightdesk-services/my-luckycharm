package com.brightdesk.myluckycharm.system

import android.content.res.Resources

/**
 * The range `Settings.System.SCREEN_BRIGHTNESS` actually accepts on this device.
 *
 * 0..255 is only the common case, not a guarantee: the scale comes from the
 * framework config values below, and devices ship with 1023, 2047 or 4095
 * instead. Hardcoding 255 there would peg a full pull at a fraction of the
 * real maximum — a quarter of it on a 1023-scale device — and the bug would be
 * invisible on any phone that happens to use 255.
 *
 * These are framework resources rather than API, so every lookup falls back to
 * the common case instead of trusting that it resolved.
 */
data class BrightnessRange(val min: Int, val max: Int) {

    fun valueFor(fraction: Float): Int =
        (min + fraction.coerceIn(0f, 1f) * (max - min)).toInt().coerceIn(min, max)

    companion object {
        fun ofSystem(): BrightnessRange {
            val max = frameworkInt("config_screenBrightnessSettingMaximum", FALLBACK_MAX)
                .coerceAtLeast(1)
            // Never fully black: the user still has to see the charm to let go
            // of it. The device's own floor can be zero, so a proportional one
            // is applied on top and scales with whatever the range turns out
            // to be.
            val deviceMin = frameworkInt("config_screenBrightnessSettingMinimum", 0)
            val visibleMin = (max * MIN_VISIBLE_FRACTION).toInt()
            return BrightnessRange(
                min = maxOf(deviceMin, visibleMin).coerceIn(0, max),
                max = max,
            )
        }

        private fun frameworkInt(name: String, fallback: Int): Int = try {
            val resources = Resources.getSystem()
            val id = resources.getIdentifier(name, "integer", "android")
            if (id != 0) resources.getInteger(id) else fallback
        } catch (error: Resources.NotFoundException) {
            fallback
        }

        private const val FALLBACK_MAX = 255

        /** About the same dimness as the old hardcoded 8 out of 255. */
        private const val MIN_VISIBLE_FRACTION = 0.03f
    }
}
