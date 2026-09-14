package com.brightdesk.myluckycharm.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.brightdesk.myluckycharm.physics.Bounds
import com.brightdesk.myluckycharm.physics.FixedTimestep
import com.brightdesk.myluckycharm.physics.PullLimit
import com.brightdesk.myluckycharm.physics.RopePhysics
import com.brightdesk.myluckycharm.physics.Vec2
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val ANCHOR_INSET_DP = 28f
private const val GRAB_RADIUS_MULTIPLIER = 1.1f
/**
 * The dash the rope hangs from, and the box you can take hold of it by. The
 * box is wide and shallow to match the dash: a round 64dp target cost far more
 * vertical room than it needed, and every pixel of it is a pixel the app
 * underneath cannot receive in Floating mode.
 */
private const val ANCHOR_DASH_WIDTH_DP = 28f
private const val ANCHOR_DASH_HEIGHT_DP = 4f
private const val ANCHOR_GRAB_HALF_WIDTH_DP = 28f
private const val ANCHOR_GRAB_HALF_HEIGHT_DP = 26f
/**
 * Finger travel that sweeps brightness or volume from end to end.
 *
 * Deliberately much longer than the charm can actually move. Media volume has
 * 15 steps on most devices, so a short throw puts several steps inside a
 * centimetre of movement and the control feels twitchy; this spreads them over
 * roughly a third of a phone screen.
 */
private const val CONTROL_THROW_DP = 220f

/**
 * How far the finger may drift between down and up and still count as a tap
 * on the charm, rather than a tiny drag. Kept separate from the drag/pull
 * machinery entirely — see [DragState.tapStartX].
 */
private const val TAP_MOVEMENT_SLOP_DP = 12f

/**
 * How long to wait after a tap on the charm before deciding no further tap is
 * coming and firing single/double/triple. Matches roughly Android's own
 * double-tap timeout; there's no public Compose API for it.
 */
private const val MULTI_TAP_TIMEOUT_MS = 300L

private const val NANOS_PER_SECOND = 1_000_000_000f

private enum class DragTarget { NONE, CHARM, ANCHOR }

/** Finger state, deliberately outside Compose state so dragging doesn't recompose. */
private class DragState {
    var target = DragTarget.NONE
    var x = 0f
    var y = 0f

    /**
     * Where the finger landed relative to the charm's attach point, held for
     * the length of the drag.
     *
     * Without it the attach point snaps to the finger on contact. The art
     * hangs *below* that point, so grabbing the charm anywhere on its body
     * would jerk it down by however far down the body you touched — enough to
     * read as a deliberate downward pull and move brightness or volume before
     * the drag has gone anywhere.
     */
    var offsetX = 0f
    var offsetY = 0f

    /**
     * Where the finger started, and where it is now. Brightness and volume are
     * measured from these rather than from the charm.
     *
     * The charm is rubber-banded, so its own travel is both short and
     * compressed: a 70dp rope gives it about 160px to move in, and media volume
     * has only 15 steps, which put a whole step inside every 10px of movement.
     * The finger has the screen to move in, so measuring it decouples how
     * finely the control can be set from how big the charm happens to be.
     */
    var fingerStartY = 0f
    var fingerY = 0f

    /**
     * Raw finger position at grab and at the latest move, used only to decide
     * whether a charm grab was a tap or a drag once it ends.
     *
     * Deliberately not the same fields as [fingerStartY]/[fingerY] above:
     * those are a *signed vertical-only* measurement feeding the pull
     * control, while this is a plain 2D distance. Conflating the two was
     * itself a past bug (see [fingerStartY]'s doc) and this would repeat it.
     */
    var tapStartX = 0f
    var tapStartY = 0f
    var tapLastX = 0f
    var tapLastY = 0f
}

/**
 * Debounced single/double/triple tap counter for the charm.
 *
 * Shared by both touch paths: Fixed mode's own sibling tap gesture detector
 * (needed because [detectDragGestures]'s onDragStart only fires once slop is
 * exceeded, so it never sees a plain tap) and Floating mode's relay, which
 * sees every real down/up and so drives this through [DragState]'s tap
 * fields and `finishDrag` instead. Both funnel into the same counter so tap
 * counts mean the same thing in either mode.
 */
private class TapState {
    var pendingCount = 0
    var job: Job? = null
}

/** Live anchor position in pixels; settings hold the persisted fraction. */
private class AnchorState {
    var x = 0f
    var y = 0f
}

/** Latest tilt direction, kept out of Compose state so sensor updates don't recompose. */
private class TiltState {
    var x = 0f
    var y = 1f
}

/**
 * User-tunable values the frame loop reads. Held in a plain object so changing a
 * slider feeds straight into the running simulation instead of restarting it —
 * the rope then eases to its new length rather than snapping.
 */
private class TuningState {
    var segmentLength = 0f
    var charmRadius = 0f
    var charmRadiusX = 0f
    var grabRadius = 0f
    var stretchFraction = 0f

    // The anchor's limits and grab box live here for the same reason the rest
    // does, plus one of its own: the handle windows call into this surface
    // through callbacks registered once, and a callback that closed over these
    // as plain locals would go on using whatever they were in the composition
    // that registered it. Settings arrive from DataStore a frame or two after
    // the first composition, so those values are the *defaults* — which is how
    // a 44dp default charm's radius ended up clamping a 28dp charm's anchor.
    var minAnchorX = 0f
    var maxAnchorX = 0f
    var minAnchorY = 0f
    var maxAnchorY = 0f
    var anchorGrabHalfWidth = 0f
    var anchorGrabHalfHeight = 0f

    /** How far the finger drags to sweep a system control end to end. */
    var controlThrow = 0f

    /** See [TAP_MOVEMENT_SLOP_DP]. */
    var tapSlop = 0f
}

/**
 * The shared physics + render surface. Hosted directly by MainActivity in Fixed
 * mode, and by the WindowManager overlay in Floating mode (spec §4), so it takes
 * its drawing area from the caller and never assumes a full screen.
 *
 * The anchor is draggable rather than pinned to top-center as in spec §5, and
 * its position is reported back as a viewport fraction to persist.
 */
@Composable
fun DreamcatcherSurface(
    anchorXFraction: Float,
    anchorYFraction: Float,
    onAnchorChanged: (Float, Float) -> Unit,
    gravityDirection: Flow<Vec2>,
    /** Fired once when the charm is grabbed, before any [onPullChanged]. */
    onPullBegin: () -> Unit,
    /**
     * A -1..1 offset from wherever the control stood when the charm was
     * grabbed — not a position on the control's own scale. See
     * [com.brightdesk.myluckycharm.physics.PullLimit.pullOffsetFraction].
     */
    onPullChanged: (Float) -> Unit,
    charm: CharmVisual,
    ropeLengthDp: Float,
    charmSizeDp: Float,
    stretchFraction: Float,
    /** Opacity of the whole rope+charm element; drag hit-testing is unaffected. */
    opacityFraction: Float = 1f,
    /** Tapping the charm itself — never the rope or the anchor dash. */
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onTripleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Floating mode's way in. Its drawing window is untouchable so the apps
     * below stay usable, so its touches arrive from separate handle windows
     * rather than from this composable's own gesture detector.
     */
    externalTouch: SurfaceTouchRelay? = null,
    /** Reports where the handle windows need to be. Unused in Fixed mode. */
    onGeometryChanged: ((CharmGeometry) -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val ropeLengthPx = with(density) { ropeLengthDp.dp.toPx() }
        val segmentLengthPx = ropeLengthPx / RopePhysics.DEFAULT_SEGMENT_COUNT
        val gravityPx = with(density) { RopePhysics.DEFAULT_GRAVITY_DP.dp.toPx() }
        val charmArtPx = with(density) { charmSizeDp.dp.toPx() }
        // The art hangs below its attach point, so its size doubles as the
        // bounding radius that keeps the charm on screen — vertically. Sideways
        // it straddles the attach point instead of hanging off it, so it only
        // reaches about half as far: the widest built-in charm needs 0.45 of an
        // art size, and emoji and photos are centred on the point and so need
        // exactly half. Using the vertical figure on both axes is what put a
        // visible margin between the anchor and each edge of the screen — the
        // anchor stopped a whole charm short of the side rather than half of one.
        val charmRadiusPx = charmArtPx
        val charmRadiusXPx = charmArtPx * 0.5f
        val ropeStrokePx = with(density) { 2.dp.toPx() }
        val anchorGrabHalfWidthPx = with(density) { ANCHOR_GRAB_HALF_WIDTH_DP.dp.toPx() }
        val anchorGrabHalfHeightPx = with(density) { ANCHOR_GRAB_HALF_HEIGHT_DP.dp.toPx() }
        val dashWidthPx = with(density) { ANCHOR_DASH_WIDTH_DP.dp.toPx() }
        val dashHeightPx = with(density) { ANCHOR_DASH_HEIGHT_DP.dp.toPx() }
        val grabRadiusPx = charmArtPx * GRAB_RADIUS_MULTIPLIER
        val controlThrowPx = with(density) { CONTROL_THROW_DP.dp.toPx() }
        val tapSlopPx = with(density) { TAP_MOVEMENT_SLOP_DP.dp.toPx() }

        // The ceiling is the *gesture* inset, not the status bar. Anything
        // starting inside the system's top gesture strip is taken as a pull of
        // the notification shade and never reaches the app, and that strip runs
        // well past the bar itself — 130px against the bar's 96px on a Galaxy
        // S25. Seating the dash a half-dash below it keeps the grab box, which
        // extends a good deal further down, comfortably in reachable territory.
        val gestureTopPx = WindowInsets.systemGestures.getTop(density).toFloat()
        val minAnchorY = maxOf(
            with(density) { ANCHOR_INSET_DP.dp.toPx() },
            charmRadiusPx,
            gestureTopPx + dashHeightPx / 2f,
        )
        val bounds = Bounds(0f, 0f, widthPx, heightPx)


        val physics = remember { RopePhysics() }
        val drag = remember { DragState() }
        val anchor = remember { AnchorState() }
        val tilt = remember { TiltState() }
        val tuning = remember { TuningState() }
        val timestep = remember { FixedTimestep() }
        val ropeScratch = remember { RopeScratch() }
        val emojiInk = remember { EmojiInk() }
        var frameTick by remember { mutableLongStateOf(0L) }
        // Anchor and physics both start at (0, 0) until the LaunchedEffect
        // below places them, which runs a moment after the first composition
        // commits — without this, that gap draws one frame of rope and charm
        // sitting at the top-left corner every time this surface (re)mounts,
        // e.g. leaving Tuning back to the home screen.
        var isReady by remember { mutableStateOf(false) }

        SideEffect {
            tuning.segmentLength = segmentLengthPx
            tuning.charmRadius = charmRadiusPx
            tuning.charmRadiusX = charmRadiusXPx
            tuning.grabRadius = grabRadiusPx
            tuning.stretchFraction = stretchFraction
            tuning.minAnchorX = charmRadiusXPx
            tuning.maxAnchorX = maxOf(charmRadiusXPx, widthPx - charmRadiusXPx)
            tuning.minAnchorY = minAnchorY
            tuning.maxAnchorY = maxOf(minAnchorY, heightPx - charmRadiusPx)
            tuning.anchorGrabHalfWidth = anchorGrabHalfWidthPx
            tuning.anchorGrabHalfHeight = anchorGrabHalfHeightPx
            tuning.controlThrow = controlThrowPx
            tuning.tapSlop = tapSlopPx
        }

        // The frame loop outlives any single composition, so it must call the
        // newest lambda rather than the one captured when it started.
        val currentOnPullBegin by rememberUpdatedState(onPullBegin)
        val currentOnPull by rememberUpdatedState(onPullChanged)
        val currentOnGeometry by rememberUpdatedState(onGeometryChanged)
        val currentOnAnchorChanged by rememberUpdatedState(onAnchorChanged)
        val currentOnSingleTap by rememberUpdatedState(onSingleTap)
        val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
        val currentOnTripleTap by rememberUpdatedState(onTripleTap)

        val coroutineScope = rememberCoroutineScope()
        val tap = remember { TapState() }

        // Debounces a run of taps on the charm into a single single/double/
        // triple firing once no further tap arrives within the timeout.
        fun registerTap() {
            tap.pendingCount = (tap.pendingCount + 1).coerceAtMost(3)
            tap.job?.cancel()
            tap.job = coroutineScope.launch {
                delay(MULTI_TAP_TIMEOUT_MS)
                when (tap.pendingCount) {
                    1 -> currentOnSingleTap()
                    2 -> currentOnDoubleTap()
                    else -> currentOnTripleTap()
                }
                tap.pendingCount = 0
            }
        }

        // Sensor listeners live only while the app is started, so a backgrounded
        // charm costs no battery (spec §6).
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(gravityDirection, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                gravityDirection.collect { direction ->
                    tilt.x = direction.x
                    tilt.y = direction.y
                }
            }
        }

        // Read through the holder, never the locals: see [TuningState].
        fun clampAnchorX(value: Float) = value.coerceIn(tuning.minAnchorX, tuning.maxAnchorX)

        fun clampAnchorY(value: Float) = value.coerceIn(tuning.minAnchorY, tuning.maxAnchorY)

        // Dragging is expressed as three plain functions rather than living
        // inside the gesture detector, because Floating mode feeds the same
        // three steps in from a different window entirely.
        fun beginDragAt(x: Float, y: Float) {
            val charmDx = x - physics.charm.x
            val charmDy = y - physics.charm.y
            val anchorDx = x - anchor.x
            val anchorDy = y - anchor.y
            val grab = tuning.grabRadius
            drag.target = when {
                charmDx * charmDx + charmDy * charmDy <= grab * grab -> {
                    drag.offsetX = physics.charm.x - x
                    drag.offsetY = physics.charm.y - y
                    drag.x = x + drag.offsetX
                    drag.y = y + drag.offsetY
                    drag.fingerStartY = y
                    drag.fingerY = y
                    drag.tapStartX = x
                    drag.tapStartY = y
                    drag.tapLastX = x
                    drag.tapLastY = y
                    physics.beginDrag()
                    // Anchors the control's baseline before any pull is
                    // reported, so the first offset lands relative to
                    // wherever the control actually is right now.
                    currentOnPullBegin()
                    DragTarget.CHARM
                }

                // A box, not a circle: the dash is wide and flat, and so is the
                // area you take hold of it by.
                abs(anchorDx) <= tuning.anchorGrabHalfWidth &&
                    abs(anchorDy) <= tuning.anchorGrabHalfHeight ->
                    DragTarget.ANCHOR

                else -> DragTarget.NONE
            }
        }

        fun dragTo(x: Float, y: Float, deltaX: Float, deltaY: Float) {
            when (drag.target) {
                DragTarget.CHARM -> {
                    drag.x = x + drag.offsetX
                    drag.y = y + drag.offsetY
                    drag.fingerY = y
                    drag.tapLastX = x
                    drag.tapLastY = y
                }

                // Move by delta so the knob doesn't snap to the finger.
                DragTarget.ANCHOR -> {
                    anchor.x = clampAnchorX(anchor.x + deltaX)
                    anchor.y = clampAnchorY(anchor.y + deltaY)
                }

                DragTarget.NONE -> Unit
            }
        }

        fun finishDrag() {
            when (drag.target) {
                DragTarget.CHARM -> {
                    // Covers Floating mode, whose relay sees every real
                    // down/up and so never runs the sibling tap detector
                    // below. In Fixed mode this only fires for a drag that
                    // exceeded slop and then wandered back near its start —
                    // the common "never moved" tap there is caught by that
                    // sibling detector instead, since detectDragGestures
                    // never calls beginDragAt for it in the first place.
                    val movedX = drag.tapLastX - drag.tapStartX
                    val movedY = drag.tapLastY - drag.tapStartY
                    if (movedX * movedX + movedY * movedY <= tuning.tapSlop * tuning.tapSlop) {
                        registerTap()
                    }
                    physics.endDrag()
                }
                DragTarget.ANCHOR -> currentOnAnchorChanged(anchor.x / widthPx, anchor.y / heightPx)
                DragTarget.NONE -> Unit
            }
            drag.target = DragTarget.NONE
        }

        // The handle windows report absolute positions, not deltas, so the
        // previous point is tracked here to turn them into one.
        DisposableEffect(externalTouch, widthPx, heightPx) {
            var lastX = 0f
            var lastY = 0f
            externalTouch?.onDown = { x, y ->
                lastX = x
                lastY = y
                beginDragAt(x, y)
            }
            externalTouch?.onMove = { x, y ->
                dragTo(x, y, x - lastX, y - lastY)
                lastX = x
                lastY = y
            }
            externalTouch?.onUp = { finishDrag() }
            onDispose {
                externalTouch?.onDown = null
                externalTouch?.onMove = null
                externalTouch?.onUp = null
            }
        }

        // Settings own the anchor; adopt them on change or resize, but never
        // yank the knob out from under a finger that is currently moving it.
        LaunchedEffect(anchorXFraction, anchorYFraction, widthPx, heightPx) {
            if (drag.target != DragTarget.ANCHOR) {
                anchor.x = clampAnchorX(anchorXFraction * widthPx)
                anchor.y = clampAnchorY(anchorYFraction * heightPx)
            }
        }

        LaunchedEffect(widthPx, heightPx) {
            physics.segmentLength = segmentLengthPx
            anchor.x = clampAnchorX(anchorXFraction * widthPx)
            anchor.y = clampAnchorY(anchorYFraction * heightPx)
            physics.resetTo(anchor.x, anchor.y)
            drag.target = DragTarget.NONE
            timestep.reset()
            isReady = true

            var lastFrame = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val steps = timestep.stepsFor((now - lastFrame) / NANOS_PER_SECOND)
                lastFrame = now
                if (steps == 0) continue

                physics.segmentLength = tuning.segmentLength

                // Spec §6: a held charm ignores tilt entirely, so the drag never
                // fights the sensor mid-pull.
                val holding = drag.target == DragTarget.CHARM
                val gravity = if (holding) {
                    Vec2(0f, gravityPx)
                } else {
                    Vec2(tilt.x * gravityPx, tilt.y * gravityPx)
                }

                repeat(steps) {
                    val target = if (holding) {
                        PullLimit.clampTarget(
                            anchorX = anchor.x,
                            anchorY = anchor.y,
                            targetX = drag.x,
                            targetY = drag.y,
                            bounds = bounds,
                            charmRadius = tuning.charmRadius,
                            charmRadiusX = tuning.charmRadiusX,
                            maxRopeLength = physics.maxLength,
                            stretchFraction = tuning.stretchFraction,
                        )
                    } else {
                        null
                    }
                    physics.step(anchor.x, anchor.y, gravity, target)
                }

                if (holding) {
                    val pull = PullLimit.pullOffsetFraction(
                        positionY = drag.fingerY,
                        startY = drag.fingerStartY,
                        travel = tuning.controlThrow,
                    )
                    // A tiny offset either way is not a deliberate pull, so
                    // merely grabbing the charm or swinging it sideways leaves
                    // the control exactly where it was.
                    if (abs(pull) > PullLimit.PULL_DEADZONE) currentOnPull(pull)
                }

                currentOnGeometry?.invoke(
                    CharmGeometry(
                        charmX = physics.charm.x,
                        charmY = physics.charm.y,
                        charmGrabRadius = tuning.grabRadius,
                        anchorX = anchor.x,
                        anchorY = anchor.y,
                        anchorGrabHalfWidth = anchorGrabHalfWidthPx,
                        anchorGrabHalfHeight = anchorGrabHalfHeightPx,
                    ),
                )

                frameTick = now
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Applied to the whole rope+charm draw, not per-shape, so it
                // fades as one element. Gesture detection below reads physics
                // state directly and is untouched by this.
                .alpha(opacityFraction)
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { offset -> beginDragAt(offset.x, offset.y) },
                        onDrag = { change, dragAmount ->
                            dragTo(change.position.x, change.position.y, dragAmount.x, dragAmount.y)
                            if (drag.target != DragTarget.NONE) change.consume()
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() },
                    )
                }
                // A sibling detector rather than folding this into the drag
                // gestures above: onDragStart there only fires once touch
                // slop is exceeded, so a plain tap on the charm never reaches
                // beginDragAt at all in Fixed mode. This sees the same
                // events independently and is naturally skipped once the
                // drag detector actually consumes a move (see [TapState]).
                //
                // Hand-rolled rather than detectTapGestures: that helper
                // consumes the up event for *any* tap in the whole canvas
                // once it recognizes one, regardless of what onTap's body
                // does — which silently ate every tap Tuning's own ancestor
                // "tap outside to close" detector needs to see. Consuming
                // only for a tap that actually lands on the charm leaves
                // every other tap completely untouched for that ancestor.
                .pointerInput(widthPx, heightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val dx = down.position.x - physics.charm.x
                        val dy = down.position.y - physics.charm.y
                        val onCharm = dx * dx + dy * dy <= tuning.grabRadius * tuning.grabRadius
                        if (onCharm) {
                            // Returns null (and consumes nothing) if the drag
                            // detector above turned this into a real drag.
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                registerTap()
                            }
                        }
                    }
                },
        ) {
            // Reading the frame counter in the draw scope is what invalidates it.
            frameTick.let { }
            if (!isReady) return@Canvas
            drawRope(physics.points, ropeStrokePx, ropeScratch, ropeEndInset(charm, charmArtPx))
            drawAnchorDash(anchor.x, anchor.y, dashWidthPx, dashHeightPx)
            drawCharm(
                visual = charm,
                x = physics.charm.x,
                y = physics.charm.y,
                angleRadians = physics.endSegmentAngleRadians(),
                artSize = charmArtPx,
                textMeasurer = textMeasurer,
                emojiInk = emojiInk,
            )
        }
    }
}
