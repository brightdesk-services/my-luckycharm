package com.brightdesk.myluckycharm.render

/**
 * Where the charm and its anchor currently are, in the surface's own pixels.
 *
 * Floating mode draws into an untouchable full-screen window so the apps
 * underneath stay usable, which means that window never receives a pointer
 * event. Touches arrive instead through small handle windows that have to be
 * kept sitting over the two things worth grabbing, and this is what tells the
 * host where to put them.
 */
data class CharmGeometry(
    val charmX: Float,
    val charmY: Float,
    val charmGrabRadius: Float,
    val anchorX: Float,
    val anchorY: Float,
    val anchorGrabHalfWidth: Float,
    val anchorGrabHalfHeight: Float,
)

/**
 * Carries touches from those handle windows back into the composition that owns
 * the physics. Coordinates are in the surface's own space; in Floating mode the
 * drawing window is laid out over the whole display, so screen coordinates and
 * surface coordinates are the same thing.
 *
 * The callbacks are set by [DreamcatcherSurface] while it is composed and
 * cleared when it leaves, so a handle that outlives the composition is inert
 * rather than holding it alive.
 */
class SurfaceTouchRelay {
    var onDown: ((Float, Float) -> Unit)? = null
    var onMove: ((Float, Float) -> Unit)? = null
    var onUp: (() -> Unit)? = null
}
