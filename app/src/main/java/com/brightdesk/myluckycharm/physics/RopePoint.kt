package com.brightdesk.myluckycharm.physics

/**
 * A single verlet particle. Velocity is implicit in the gap between the current
 * and previous position, so moving a point without touching [prevX]/[prevY]
 * imparts velocity, while [teleport] moves it with none.
 */
class RopePoint(x: Float, y: Float) {
    var x: Float = x
    var y: Float = y
    var prevX: Float = x
    var prevY: Float = y

    fun setPosition(newX: Float, newY: Float) {
        x = newX
        y = newY
    }

    fun teleport(newX: Float, newY: Float) {
        x = newX
        y = newY
        prevX = newX
        prevY = newY
    }
}
