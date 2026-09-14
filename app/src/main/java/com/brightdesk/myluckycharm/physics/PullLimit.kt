package com.brightdesk.myluckycharm.physics

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Screen-bound pull clamp (spec §5). Unlike the desktop versions, which capped
 * the swing at a fixed angle because the window was undersized, the charm here
 * is limited only by the viewport: its rendered bounding circle must stay fully
 * on screen. The reachable pull angle therefore widens on larger screens.
 *
 * The inset is not the same on both axes. [charmRadius] is how far the art
 * reaches *below* its attach point, which is a full art size because the charm
 * hangs; sideways it only reaches about half that, since the art straddles the
 * attach point rather than hanging off it. Insetting the left and right edges
 * by the vertical figure costs the anchor roughly half a charm of travel at
 * each edge for no reason, so [charmRadiusX] carries the horizontal half-extent
 * and defaults to [charmRadius] for callers that have only one number.
 *
 * Callers should keep the anchor inside that inset; the component clamp below
 * is a safety net for degenerate layouts, not the primary limit.
 */
object PullLimit {

    fun clampTarget(
        anchorX: Float,
        anchorY: Float,
        targetX: Float,
        targetY: Float,
        bounds: Bounds,
        charmRadius: Float,
        maxRopeLength: Float,
        stretchFraction: Float = DEFAULT_STRETCH_FRACTION,
        charmRadiusX: Float = charmRadius,
    ): Vec2 {
        val minX = bounds.left + charmRadiusX
        val maxX = bounds.right - charmRadiusX
        val minY = bounds.top + charmRadius
        val maxY = bounds.bottom - charmRadius
        if (minX > maxX || minY > maxY) return Vec2(anchorX, anchorY)

        val deltaX = targetX - anchorX
        val deltaY = targetY - anchorY
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (distance < EPSILON) return Vec2(anchorX, anchorY)

        var dirX = deltaX / distance
        var dirY = deltaY / distance

        // A charm hangs: it swings out to the sides but is never lifted above
        // the point it hangs from, so anything aimed upward is flattened onto
        // the horizontal instead.
        if (dirY < 0f) {
            dirY = 0f
            dirX = if (dirX >= 0f) 1f else -1f
        }

        // Rope length is soft: pulling past it stretches with rising resistance
        // and springs back on release. The viewport edge stays a hard stop,
        // because the charm must never be draggable off-screen (spec §2).
        val stretched = if (distance <= maxRopeLength) {
            distance
        } else {
            maxRopeLength + rubberBand(distance - maxRopeLength, maxRopeLength * stretchFraction)
        }

        // Each axis is clamped on its own, so a charm that has run out of room
        // sideways keeps the rest of the pull and slides down the edge.
        //
        // This used to scale the whole direction vector by a single ray-cast
        // distance to the viewport edge, which is right only while the anchor
        // is strictly inside the inset. The anchor's own clamp puts it *exactly
        // on* that inset at either side of the screen, so there the outward
        // ray-cast returned zero — and because the cast took the minimum across
        // both axes, that zero wiped out the downward component too. Every pull
        // toward the near edge collapsed onto the anchor, the rope's seven
        // segments bunched into a loop, and the art span around to follow an end
        // segment that was now pointing upward: the charm appeared to fly up and
        // hang inverted instead of swinging out.
        val x = (anchorX + dirX * stretched).coerceIn(minX, maxX)
        val y = (anchorY + dirY * stretched).coerceIn(minY, maxY)
        // Never above the anchor, whatever the component clamp above did.
        return Vec2(x, maxOf(y, anchorY))
    }

    /**
     * How far a pull has travelled vertically from where it began, as a signed
     * -1..1 fraction of the [travel] that covers a control's full range.
     *
     * Positive is downward — pull the charm down for more, push it up for
     * less. The result is an *offset* to apply to whatever the control was set
     * to when the charm was grabbed, never an absolute position on the scale:
     * mapping 0..1 straight onto 0..max means touching the charm while a video
     * plays drops the volume to nothing and then winds it back up, rather than
     * nudging it from where the user had it.
     *
     * Only the vertical component counts, so swinging the charm sideways
     * leaves the control alone.
     *
     * The caller measures the *finger*, not the charm. The charm is
     * rubber-banded into a short, compressed range — and media volume has only
     * 15 steps on most devices, which would put a step inside every few
     * millimetres of movement. Reading the finger lets [travel] be as long as
     * the control needs without changing how the charm looks or moves.
     */
    fun pullOffsetFraction(positionY: Float, startY: Float, travel: Float): Float {
        if (travel <= 0f) return 0f
        return ((positionY - startY) / travel).coerceIn(-1f, 1f)
    }

    /**
     * Diminishing-returns curve for pulling past the rope's rest length. It
     * approaches [maxStretch] asymptotically, and its slope at zero is exactly
     * 1, so crossing the limit has no perceptible kink — resistance just starts
     * building.
     */
    private fun rubberBand(overshoot: Float, maxStretch: Float): Float {
        if (maxStretch <= 0f || overshoot <= 0f) return 0f
        return maxStretch * (1f - exp(-overshoot / maxStretch))
    }

    private const val EPSILON = 1e-4f

    /** Fallback stretch when a caller doesn't supply the user's setting. */
    const val DEFAULT_STRETCH_FRACTION = 0.7f

    /**
     * A pull smaller than this either way is treated as no pull at all, so
     * merely grabbing the charm or swinging it sideways leaves brightness and
     * volume — and, for brightness, the auto-brightness setting — untouched.
     */
    const val PULL_DEADZONE = 0.02f
}
