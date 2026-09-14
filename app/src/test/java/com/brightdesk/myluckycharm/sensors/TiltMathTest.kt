package com.brightdesk.myluckycharm.sensors

import com.brightdesk.myluckycharm.physics.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

private const val G = 9.81f
private const val TOLERANCE = 0.001f

private fun assertDirection(expected: Vec2, actual: Vec2, message: String) {
    assertEquals("$message (x)", expected.x, actual.x, TOLERANCE)
    assertEquals("$message (y)", expected.y, actual.y, TOLERANCE)
}

class AccelerometerTiltTest {

    @Test
    fun `held upright, gravity pulls down the screen`() {
        assertDirection(Vec2(0f, 1f), TiltMath.fromAccelerometer(0f, G), "upright")
    }

    @Test
    fun `rolled so the right edge is lowest, the charm hangs right`() {
        // Right edge down means device +x points at the ground, so the
        // accelerometer reads negative on x.
        assertDirection(Vec2(1f, 0f), TiltMath.fromAccelerometer(-G, 0f), "right edge down")
    }

    @Test
    fun `rolled so the left edge is lowest, the charm hangs left`() {
        assertDirection(Vec2(-1f, 0f), TiltMath.fromAccelerometer(G, 0f), "left edge down")
    }

    @Test
    fun `held upside down, the charm hangs toward the top of the screen`() {
        assertDirection(Vec2(0f, -1f), TiltMath.fromAccelerometer(0f, -G), "upside down")
    }

    @Test
    fun `lying flat, there is no lateral tilt so gravity stays straight down`() {
        // All of gravity is on the z axis, leaving nothing to project onto the screen.
        assertDirection(Vec2(0f, 1f), TiltMath.fromAccelerometer(0f, 0f), "flat")
    }

    @Test
    fun `tilted diagonally, the direction is normalised`() {
        val direction = TiltMath.fromAccelerometer(-G, G)
        assertEquals("unit length", 1f, direction.length, TOLERANCE)
        assertEquals("equal parts right and down", direction.x, direction.y, TOLERANCE)
    }
}

class RotationVectorTiltTest {

    /** Row-major device-to-world rotation, as produced by getRotationMatrixFromVector. */
    private fun matrix(vararg values: Float) = values

    @Test
    fun `held upright, gravity pulls down the screen`() {
        // Device +y maps to world up, device +z maps to world south.
        val upright = matrix(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        assertDirection(Vec2(0f, 1f), TiltMath.fromRotationMatrix(upright), "upright")
    }

    @Test
    fun `rolled so the right edge is lowest, the charm hangs right`() {
        // Device +x now points at the ground.
        val rolled = matrix(
            0f, 1f, 0f,
            0f, 0f, -1f,
            -1f, 0f, 0f,
        )
        assertDirection(Vec2(1f, 0f), TiltMath.fromRotationMatrix(rolled), "right edge down")
    }

    @Test
    fun `lying flat, there is no lateral tilt so gravity stays straight down`() {
        val flat = matrix(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        assertDirection(Vec2(0f, 1f), TiltMath.fromRotationMatrix(flat), "flat")
    }
}
