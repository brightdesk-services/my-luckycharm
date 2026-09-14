package com.brightdesk.myluckycharm.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.brightdesk.myluckycharm.render.CharmGeometry
import com.brightdesk.myluckycharm.render.DreamcatcherScene
import kotlin.math.abs
import kotlin.math.roundToInt

/** Latest charm/anchor hit-test geometry, held outside Compose state so a tap gesture can read it without redrawing on every frame it arrives. */
private class LatestGeometry {
    var value: CharmGeometry? = null
}

private fun isEmptyArea(x: Float, y: Float, geometry: CharmGeometry): Boolean {
    val charmDx = x - geometry.charmX
    val charmDy = y - geometry.charmY
    val onCharm = charmDx * charmDx + charmDy * charmDy <= geometry.charmGrabRadius * geometry.charmGrabRadius
    val onAnchor = abs(x - geometry.anchorX) <= geometry.anchorGrabHalfWidth &&
        abs(y - geometry.anchorY) <= geometry.anchorGrabHalfHeight
    return !onCharm && !onAnchor
}

/**
 * Tuning happens over the real charm at real size rather than in a thumbnail,
 * so what you adjust is what you get. Slider movement drives local state for a
 * smooth live preview and only commits to storage when the finger lifts, which
 * keeps a drag from firing a write on every frame.
 */
@Composable
fun TuningScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every slider already applies live to the preview and commits on release
    // (see the class doc), so there's nothing left to "submit" — the system
    // back gesture/button closes tuning, same as Settings.
    BackHandler(onBack = onDone)

    var ropeLength by remember(settings.ropeLengthDp) { mutableFloatStateOf(settings.ropeLengthDp) }
    var charmSize by remember(settings.charmSizeDp) { mutableFloatStateOf(settings.charmSizeDp) }
    var stretch by remember(settings.stretchFraction) { mutableFloatStateOf(settings.stretchFraction) }
    var opacity by remember(settings.opacityFraction) { mutableFloatStateOf(settings.opacityFraction) }
    val geometry = remember { LatestGeometry() }

    val preview = settings.copy(
        ropeLengthDp = ropeLength,
        charmSizeDp = charmSize,
        stretchFraction = stretch,
        opacityFraction = opacity,
    )

    // A column rather than an overlay: a long rope would otherwise hang the
    // charm behind the panel, hiding the very thing being tuned. Giving the
    // scene its own area means the screen-bounds clamp keeps it reachable.
    Column(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // A tap that lands away from the charm and the anchor dash closes
                // tuning — a quicker way out than reaching for the back gesture
                // once you're done dragging sliders. The drag detector inside
                // DreamcatcherScene sees every touch first and consumes the ones
                // it turns into a charm/anchor drag, so this only ever fires for
                // taps that land on genuinely empty canvas.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val current = geometry.value
                            if (current == null || isEmptyArea(offset.x, offset.y, current)) {
                                onDone()
                            }
                        },
                    )
                },
        ) {
            DreamcatcherScene(
                settings = preview,
                onAnchorChanged = { x, y ->
                    onChange { it.copy(anchorXFraction = x, anchorYFraction = y) }
                },
                // Tuning the charm shouldn't dim the screen or blast the volume.
                enableSystemActions = false,
                onGeometryChanged = { geometry.value = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("Tuning", style = MaterialTheme.typography.titleMedium)

                Text(
                    "Drag the charm as you go — this is the real thing, not a preview.",
                    style = MaterialTheme.typography.bodySmall,
                )

                TuningSlider(
                    label = "Rope length",
                    readout = "${ropeLength.roundToInt()} dp",
                    value = ropeLength,
                    valueRange = AppSettings.MIN_ROPE_LENGTH_DP..AppSettings.MAX_ROPE_LENGTH_DP,
                    onValueChange = { ropeLength = it },
                    onCommit = { onChange { it.copy(ropeLengthDp = ropeLength) } },
                )

                TuningSlider(
                    label = "Charm size",
                    readout = "${charmSize.roundToInt()} dp",
                    value = charmSize,
                    valueRange = AppSettings.MIN_CHARM_SIZE_DP..AppSettings.MAX_CHARM_SIZE_DP,
                    onValueChange = { charmSize = it },
                    onCommit = { onChange { it.copy(charmSizeDp = charmSize) } },
                )

                TuningSlider(
                    label = "Stretch",
                    readout = "${(stretch * 100f).roundToInt()}% of rope",
                    value = stretch,
                    valueRange = AppSettings.MIN_STRETCH_FRACTION..AppSettings.MAX_STRETCH_FRACTION,
                    onValueChange = { stretch = it },
                    onCommit = { onChange { it.copy(stretchFraction = stretch) } },
                )

                TuningSlider(
                    label = "Opacity",
                    readout = "${(opacity * 100f).roundToInt()}%",
                    value = opacity,
                    valueRange = AppSettings.MIN_OPACITY_FRACTION..AppSettings.MAX_OPACITY_FRACTION,
                    onValueChange = { opacity = it },
                    onCommit = { onChange { it.copy(opacityFraction = opacity) } },
                )

                TextButton(
                    onClick = {
                        ropeLength = AppSettings.DEFAULT_ROPE_LENGTH_DP
                        charmSize = AppSettings.DEFAULT_CHARM_SIZE_DP
                        stretch = AppSettings.DEFAULT_STRETCH_FRACTION
                        opacity = AppSettings.DEFAULT_OPACITY_FRACTION
                        onChange {
                            it.copy(
                                ropeLengthDp = AppSettings.DEFAULT_ROPE_LENGTH_DP,
                                charmSizeDp = AppSettings.DEFAULT_CHARM_SIZE_DP,
                                stretchFraction = AppSettings.DEFAULT_STRETCH_FRACTION,
                                opacityFraction = AppSettings.DEFAULT_OPACITY_FRACTION,
                            )
                        }
                    },
                ) {
                    Text("Reset to defaults")
                }
            }
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    readout: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(readout, style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onCommit,
        valueRange = valueRange,
    )
}
