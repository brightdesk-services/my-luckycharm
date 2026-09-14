package com.brightdesk.myluckycharm.settings

enum class PlacementMode { FIXED, FLOATING }

enum class TiltSource { OFF, ACCELEROMETER, GYROSCOPE }

enum class PullAction { NONE, BRIGHTNESS, VOLUME }

enum class CharmType { BUILT_IN, CUSTOM_IMAGE, EMOJI }

enum class TapAction { NONE, TOGGLE_MUTE, TOGGLE_FLASHLIGHT }

data class AppSettings(
    val placementMode: PlacementMode = PlacementMode.FIXED,
    val tiltSource: TiltSource = TiltSource.OFF,
    val pullAction: PullAction = PullAction.NONE,
    val charmType: CharmType = CharmType.BUILT_IN,
    val builtInCharmId: String = DEFAULT_BUILT_IN_CHARM_ID,
    /** Path inside app-private internal storage; set when [charmType] is CUSTOM_IMAGE. */
    val customImagePath: String? = null,
    /** Set when [charmType] is EMOJI. */
    val emojiChar: String = DEFAULT_EMOJI,
    val soundEnabled: Boolean = true,
    /**
     * Where the rope hangs from, as a fraction of the drawing area rather than
     * pixels, so the spot survives rotation and means the same thing in the
     * in-app surface and the floating overlay.
     */
    val anchorXFraction: Float = DEFAULT_ANCHOR_X_FRACTION,
    val anchorYFraction: Float = DEFAULT_ANCHOR_Y_FRACTION,
    /** Total rope length in dp; the segment count stays fixed at 7. */
    val ropeLengthDp: Float = DEFAULT_ROPE_LENGTH_DP,
    val charmSizeDp: Float = DEFAULT_CHARM_SIZE_DP,
    /** How far past its rest length the rope stretches, as a fraction of that length. */
    val stretchFraction: Float = DEFAULT_STRETCH_FRACTION,
    /** Opacity of the whole rope+charm element, 0 (nearly invisible) to 1 (opaque). */
    val opacityFraction: Float = DEFAULT_OPACITY_FRACTION,
    /**
     * What tapping the charm itself does — never the rope or the anchor dash.
     * Independent of [pullAction]: a tap and a pull are different gestures on
     * the same target, so both can be wired to something at once.
     */
    val singleTapAction: TapAction = TapAction.NONE,
    val doubleTapAction: TapAction = TapAction.NONE,
    val tripleTapAction: TapAction = TapAction.NONE,
) {
    companion object {
        const val DEFAULT_BUILT_IN_CHARM_ID = "lucky_cat"
        const val DEFAULT_EMOJI = "🪶"
        const val DEFAULT_ANCHOR_X_FRACTION = 0.5f
        const val DEFAULT_ANCHOR_Y_FRACTION = 0.06f

        const val DEFAULT_ROPE_LENGTH_DP = 112f
        const val MIN_ROPE_LENGTH_DP = 56f
        const val MAX_ROPE_LENGTH_DP = 400f

        const val DEFAULT_CHARM_SIZE_DP = 44f
        const val MIN_CHARM_SIZE_DP = 24f
        const val MAX_CHARM_SIZE_DP = 120f

        const val DEFAULT_STRETCH_FRACTION = 0.7f
        const val MIN_STRETCH_FRACTION = 0f
        const val MAX_STRETCH_FRACTION = 2f

        const val DEFAULT_OPACITY_FRACTION = 1f
        // Never lets the slider all the way to 0 — a fully invisible charm
        // would be unfindable to drag back into view or re-tune.
        const val MIN_OPACITY_FRACTION = 0.1f
        const val MAX_OPACITY_FRACTION = 1f
    }
}
