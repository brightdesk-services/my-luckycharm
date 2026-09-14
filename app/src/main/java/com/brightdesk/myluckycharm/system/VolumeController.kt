package com.brightdesk.myluckycharm.system

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * Maps a pull onto media volume (spec §7). Needs only the normal
 * MODIFY_AUDIO_SETTINGS manifest permission, which is granted at install.
 *
 * The pull is an *offset* from where the volume stood when the charm was
 * grabbed, not a position on the 0..max scale. Treating it as a position means
 * reaching for the charm part-way through a video drops the sound to nothing
 * and winds it back up from there.
 */
class VolumeController(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var baseline: Int? = null
    private var lastValue = -1

    /** Called when the charm is taken hold of, to anchor the adjustment. */
    fun beginAdjust() {
        baseline = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
        lastValue = -1
    }

    fun apply(offset: Float) {
        val manager = audioManager ?: return
        val maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // A pull that somehow arrives without a grab still has to do something
        // sensible, so fall back to wherever the volume is right now.
        val from = baseline ?: manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val value = (from + offset.coerceIn(-1f, 1f) * maxVolume)
            .roundToInt()
            .coerceIn(0, maxVolume)
        if (value == lastValue) return

        lastValue = value
        // Flag 0 suppresses the system volume HUD, which would otherwise pop up
        // over the charm on every change.
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
    }

    /** A tap action, unrelated to the pull-based [apply] above — mutes/unmutes outright rather than moving toward an offset. */
    fun toggleMute() {
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
    }
}
