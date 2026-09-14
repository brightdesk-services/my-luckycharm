package com.brightdesk.myluckycharm.sensors

import com.brightdesk.myluckycharm.physics.Vec2

/**
 * Sensor-to-screen gravity conversions, kept pure and Android-free so the axis
 * conventions can be unit tested. Getting a sign backwards here is easy and
 * otherwise only detectable by physically rotating a phone.
 *
 * Sensor axes are device-space: +x right, +y up the screen. Screen space has +y
 * pointing down, so y is flipped on the way out.
 */
object TiltMath {

    /**
     * At rest an accelerometer reports the reaction to gravity, i.e. roughly the
     * negation of the gravity vector, so x is negated to recover the pull
     * direction and y passes through as the flip cancels the negation.
     */
    fun fromAccelerometer(accelX: Float, accelY: Float): Vec2 =
        Vec2(-accelX, accelY).normalizedOr(Vec2.Down)

    /**
     * [matrix] is the row-major device-to-world rotation from
     * `SensorManager.getRotationMatrixFromVector`. World-down expressed in
     * device coords is the negated bottom row; only x and y matter once the
     * result is flattened onto the screen.
     */
    fun fromRotationMatrix(matrix: FloatArray): Vec2 =
        Vec2(-matrix[6], matrix[7]).normalizedOr(Vec2.Down)
}
