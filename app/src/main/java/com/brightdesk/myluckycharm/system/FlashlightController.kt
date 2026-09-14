package com.brightdesk.myluckycharm.system

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Toggles the rear camera's torch as a tap action.
 *
 * `CameraManager.setTorchMode` needs no runtime permission — unlike opening
 * the camera for capture, it's the API meant for flashlight-style widgets.
 * The camera ID is resolved lazily and cached, since [CameraManager.cameraIdList]
 * is itself a binder call.
 */
class FlashlightController(context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val torchCameraId: String? by lazy { findTorchCameraId() }
    private var isOn = false

    fun toggle() {
        val manager = cameraManager ?: return
        val id = torchCameraId ?: return
        val next = !isOn
        try {
            manager.setTorchMode(id, next)
            isOn = next
        } catch (error: CameraAccessException) {
            // Another app (the camera app itself, most likely) holds the
            // camera — leave our tracked state as it was rather than claim
            // a toggle that didn't happen.
        }
    }

    private fun findTorchCameraId(): String? {
        val manager = cameraManager ?: return null
        return try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (error: CameraAccessException) {
            null
        }
    }
}
