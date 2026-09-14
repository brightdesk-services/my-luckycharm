package com.brightdesk.myluckycharm.physics

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    val length: Float get() = sqrt(x * x + y * y)

    fun normalizedOr(fallback: Vec2): Vec2 {
        val len = length
        return if (len < 1e-5f) fallback else Vec2(x / len, y / len)
    }

    operator fun times(scale: Float): Vec2 = Vec2(x * scale, y * scale)

    companion object {
        val Zero = Vec2(0f, 0f)
        val Down = Vec2(0f, 1f)
    }
}

/** Axis-aligned region the charm is allowed to occupy, in pixels. */
data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
