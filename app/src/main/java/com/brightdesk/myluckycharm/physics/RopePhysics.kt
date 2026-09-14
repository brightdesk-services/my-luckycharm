package com.brightdesk.myluckycharm.physics

import kotlin.math.sqrt

/**
 * Verlet rope ported from the validated Windows/WPF version. Pure Kotlin: no
 * Android dependencies, so it can be unit tested directly.
 *
 * All distances are pixels; the caller converts from dp. Gravity is expressed
 * as an acceleration per fixed step, not per second, so the tuned constants
 * behave identically regardless of display refresh rate (see [FixedTimestep]).
 */
class RopePhysics(
    val segmentCount: Int = DEFAULT_SEGMENT_COUNT,
    var segmentLength: Float = 0f,
    var damping: Float = DEFAULT_DAMPING,
    var constraintIterations: Int = DEFAULT_CONSTRAINT_ITERATIONS,
) {
    val points: List<RopePoint> = List(segmentCount + 1) { RopePoint(0f, 0f) }

    /** The rope's free end, where the charm hangs. */
    val charm: RopePoint get() = points[points.lastIndex]

    /** Fully extended rope length. */
    val maxLength: Float get() = segmentLength * segmentCount

    var isDragging: Boolean = false
        private set

    private var dragVelocityX = 0f
    private var dragVelocityY = 0f

    /** Hangs the rope straight down from the anchor, at rest. */
    fun resetTo(anchorX: Float, anchorY: Float) {
        points.forEachIndexed { index, point ->
            point.teleport(anchorX, anchorY + segmentLength * index)
        }
        isDragging = false
        dragVelocityX = 0f
        dragVelocityY = 0f
    }

    fun beginDrag() {
        isDragging = true
        dragVelocityX = 0f
        dragVelocityY = 0f
    }

    /**
     * Hands the charm back to the simulation, carrying the smoothed drag
     * velocity so the rope whips instead of dropping dead.
     */
    fun endDrag() {
        if (!isDragging) return
        isDragging = false
        charm.prevX = charm.x - dragVelocityX
        charm.prevY = charm.y - dragVelocityY
    }

    /**
     * Advances one fixed step. [dragTarget] is the already-clamped finger
     * position (see [PullLimit]) and is ignored unless a drag is active.
     */
    fun step(anchorX: Float, anchorY: Float, gravity: Vec2, dragTarget: Vec2?) {
        val lastIndex = points.lastIndex
        val heldTarget = if (isDragging) dragTarget else null
        val dragging = heldTarget != null

        if (heldTarget != null) {
            val deltaX = heldTarget.x - charm.x
            val deltaY = heldTarget.y - charm.y
            dragVelocityX += (deltaX - dragVelocityX) * DRAG_VELOCITY_SMOOTHING
            dragVelocityY += (deltaY - dragVelocityY) * DRAG_VELOCITY_SMOOTHING
            charm.teleport(heldTarget.x, heldTarget.y)
        }

        for (index in 1..lastIndex) {
            if (dragging && index == lastIndex) continue
            val point = points[index]
            val velocityX = (point.x - point.prevX) * damping
            val velocityY = (point.y - point.prevY) * damping
            point.prevX = point.x
            point.prevY = point.y
            point.x += velocityX + gravity.x
            point.y += velocityY + gravity.y
        }

        repeat(constraintIterations) {
            points[0].setPosition(anchorX, anchorY)
            for (index in 0 until lastIndex) {
                val a = points[index]
                val b = points[index + 1]
                val anchored = index == 0
                val heldByFinger = dragging && index + 1 == lastIndex
                if (anchored && heldByFinger) continue

                val deltaX = b.x - a.x
                val deltaY = b.y - a.y
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                if (distance < MIN_SEPARATION) {
                    if (!heldByFinger) b.setPosition(a.x, a.y + segmentLength)
                    continue
                }

                val correction = (distance - segmentLength) / distance * 0.5f
                val offsetX = deltaX * correction
                val offsetY = deltaY * correction
                when {
                    anchored -> b.setPosition(b.x - offsetX * 2f, b.y - offsetY * 2f)
                    heldByFinger -> a.setPosition(a.x + offsetX * 2f, a.y + offsetY * 2f)
                    else -> {
                        a.setPosition(a.x + offsetX, a.y + offsetY)
                        b.setPosition(b.x - offsetX, b.y - offsetY)
                    }
                }
            }
        }
        points[0].setPosition(anchorX, anchorY)
    }

    /** Angle of the final segment, used to rotate the charm with the rope. */
    fun endSegmentAngleRadians(): Float {
        val tip = points[points.lastIndex]
        val previous = points[points.lastIndex - 1]
        return kotlin.math.atan2(tip.y - previous.y, tip.x - previous.x)
    }

    companion object {
        const val DEFAULT_SEGMENT_COUNT = 7
        const val DEFAULT_DAMPING = 0.985f
        const val DEFAULT_CONSTRAINT_ITERATIONS = 6

        /** dp per rope segment; 7 segments gives a ~112dp rope. */
        const val DEFAULT_SEGMENT_LENGTH_DP = 16f

        /**
         * Gravity per fixed step, in dp. Chosen so a fully extended rope swings
         * with a period of roughly 1.2s, which reads as a calm charm rather
         * than a physically literal pendulum.
         */
        const val DEFAULT_GRAVITY_DP = 0.9f

        private const val DRAG_VELOCITY_SMOOTHING = 0.35f
        private const val MIN_SEPARATION = 1e-4f
    }
}
