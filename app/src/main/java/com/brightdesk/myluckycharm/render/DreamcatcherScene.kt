package com.brightdesk.myluckycharm.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.brightdesk.myluckycharm.sensors.TiltProvider
import com.brightdesk.myluckycharm.settings.AppSettings
import com.brightdesk.myluckycharm.settings.PullAction
import com.brightdesk.myluckycharm.settings.TapAction
import com.brightdesk.myluckycharm.system.BrightnessController
import com.brightdesk.myluckycharm.system.FlashlightController
import com.brightdesk.myluckycharm.system.VolumeController

/**
 * Everything the charm needs, wired up once: tilt, pull actions, tap actions
 * and charm art. Both hosts render this — the activity in Fixed mode and the
 * WindowManager overlay in Floating mode — so the two can't drift apart (spec §4).
 */
@Composable
fun DreamcatcherScene(
    settings: AppSettings,
    onAnchorChanged: (Float, Float) -> Unit,
    /** Off for the settings preview, so tuning the charm can't mute audio, dim the screen, or flick the torch. */
    enableSystemActions: Boolean = true,
    modifier: Modifier = Modifier,
    /** Floating mode's touch path; see [SurfaceTouchRelay]. */
    externalTouch: SurfaceTouchRelay? = null,
    /**
     * Dismisses the floating charm, for [TapAction.PUT_AWAY]. Null in Fixed
     * mode and in Tuning's preview: there is no overlay to put away there, so
     * the action simply does nothing rather than being faked with something else.
     */
    onPutAway: (() -> Unit)? = null,
    onGeometryChanged: ((CharmGeometry) -> Unit)? = null,
) {
    val context = LocalContext.current
    val tiltProvider = remember(context) { TiltProvider(context) }
    val gravityDirection = remember(tiltProvider, settings.tiltSource) {
        tiltProvider.gravityDirection(settings.tiltSource)
    }
    val brightnessController = remember(context) { BrightnessController(context) }
    val volumeController = remember(context) { VolumeController(context) }
    val flashlightController = remember(context) { FlashlightController(context) }
    val charm = rememberCharmVisual(settings)
    val pullAction = settings.pullAction

    fun runTapAction(action: TapAction) {
        if (!enableSystemActions) return
        when (action) {
            TapAction.NONE -> Unit
            TapAction.TOGGLE_MUTE -> volumeController.toggleMute()
            TapAction.TOGGLE_FLASHLIGHT -> flashlightController.toggle()
            TapAction.PUT_AWAY -> onPutAway?.invoke()
        }
    }

    DreamcatcherSurface(
        anchorXFraction = settings.anchorXFraction,
        anchorYFraction = settings.anchorYFraction,
        onAnchorChanged = onAnchorChanged,
        gravityDirection = gravityDirection,
        onPullBegin = {
            if (enableSystemActions) {
                when (pullAction) {
                    PullAction.NONE -> Unit
                    PullAction.BRIGHTNESS -> brightnessController.beginAdjust()
                    PullAction.VOLUME -> volumeController.beginAdjust()
                }
            }
        },
        onPullChanged = { offset ->
            if (enableSystemActions) {
                when (pullAction) {
                    PullAction.NONE -> Unit
                    PullAction.BRIGHTNESS -> brightnessController.apply(offset)
                    PullAction.VOLUME -> volumeController.apply(offset)
                }
            }
        },
        onSingleTap = { runTapAction(settings.singleTapAction) },
        onDoubleTap = { runTapAction(settings.doubleTapAction) },
        onTripleTap = { runTapAction(settings.tripleTapAction) },
        charm = charm,
        ropeLengthDp = settings.ropeLengthDp,
        charmSizeDp = settings.charmSizeDp,
        stretchFraction = settings.stretchFraction,
        opacityFraction = settings.opacityFraction,
        modifier = modifier,
        externalTouch = externalTouch,
        onGeometryChanged = onGeometryChanged,
    )
}
