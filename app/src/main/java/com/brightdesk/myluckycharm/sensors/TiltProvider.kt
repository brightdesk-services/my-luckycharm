package com.brightdesk.myluckycharm.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.brightdesk.myluckycharm.physics.Vec2
import com.brightdesk.myluckycharm.settings.TiltSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf

/**
 * Emits the direction gravity should pull the charm, as a unit vector in screen
 * space (+y is down the screen), so the rope hangs "downhill" as the device is
 * tilted (spec §6).
 *
 * Sensor axes are device-space with +y pointing up the screen, hence the sign
 * flips below. Listeners are registered only while a collector is active, so
 * collecting inside `repeatOnLifecycle` is what satisfies the spec's
 * unregister-on-pause requirement.
 */
class TiltProvider(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    fun gravityDirection(source: TiltSource): Flow<Vec2> = when (source) {
        TiltSource.OFF -> flowOf(Vec2.Down)
        TiltSource.ACCELEROMETER -> accelerometerDirection()
        TiltSource.GYROSCOPE -> rotationVectorDirection()
    }

    private fun accelerometerDirection(): Flow<Vec2> = sensorDirection(Sensor.TYPE_ACCELEROMETER) {
        var filteredX = 0f
        var filteredY = 0f
        var primed = false

        fun(event: SensorEvent): Vec2 {
            val rawX = event.values[0]
            val rawY = event.values[1]
            if (primed) {
                filteredX = filteredX * (1f - SMOOTHING) + rawX * SMOOTHING
                filteredY = filteredY * (1f - SMOOTHING) + rawY * SMOOTHING
            } else {
                filteredX = rawX
                filteredY = rawY
                primed = true
            }
            return TiltMath.fromAccelerometer(filteredX, filteredY)
        }
    }

    private fun rotationVectorDirection(): Flow<Vec2> = sensorDirection(Sensor.TYPE_ROTATION_VECTOR) {
        val matrix = FloatArray(9)

        fun(event: SensorEvent): Vec2 {
            // Some devices (Samsung in particular) report 5+ values here, which
            // makes getRotationMatrixFromVector throw; it only ever needs 4.
            val rotation = if (event.values.size > 4) event.values.copyOf(4) else event.values
            SensorManager.getRotationMatrixFromVector(matrix, rotation)
            return TiltMath.fromRotationMatrix(matrix)
        }
    }

    /**
     * [buildMapper] is invoked once per subscription so each collector gets its
     * own filter state rather than sharing a smoothed value across subscribers.
     */
    private fun sensorDirection(
        sensorType: Int,
        buildMapper: () -> (SensorEvent) -> Vec2,
    ): Flow<Vec2> = callbackFlow {
        val sensor = sensorManager?.getDefaultSensor(sensorType)
        if (sensor == null) {
            // No such sensor on this device: fall back to a plain pendulum.
            trySend(Vec2.Down)
            awaitClose {}
            return@callbackFlow
        }

        val mapper = buildMapper()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(mapper(event))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private companion object {
        /** Spec §6: filtered = filtered * 0.9 + raw * 0.1. */
        const val SMOOTHING = 0.1f
    }
}
