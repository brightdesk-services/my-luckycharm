package com.brightdesk.myluckycharm.physics

/**
 * The rope constants were tuned per frame at 60Hz. Phones ship 90/120/144Hz
 * panels, so stepping once per rendered frame would make the charm swing at a
 * device-dependent speed. This accumulates real elapsed time and runs the tuned
 * step at a fixed rate instead, keeping the ported constants exact.
 */
class FixedTimestep(
    private val stepSeconds: Float = DEFAULT_STEP_SECONDS,
    private val maxStepsPerFrame: Int = DEFAULT_MAX_STEPS_PER_FRAME,
) {
    private var accumulated = 0f

    fun reset() {
        accumulated = 0f
    }

    /**
     * Consumes [deltaSeconds] and reports how many fixed steps are due. A long
     * stall is dropped rather than replayed, so returning to the app doesn't
     * fast-forward the simulation.
     */
    fun stepsFor(deltaSeconds: Float): Int {
        if (deltaSeconds <= 0f) return 0
        if (deltaSeconds > MAX_FRAME_SECONDS) {
            accumulated = 0f
            return 1
        }
        accumulated += deltaSeconds
        var steps = 0
        while (accumulated >= stepSeconds && steps < maxStepsPerFrame) {
            accumulated -= stepSeconds
            steps++
        }
        if (steps == maxStepsPerFrame) accumulated = 0f
        return steps
    }

    companion object {
        const val DEFAULT_STEP_SECONDS = 1f / 60f
        const val DEFAULT_MAX_STEPS_PER_FRAME = 5
        const val MAX_FRAME_SECONDS = 0.25f
    }
}
