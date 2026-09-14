package com.brightdesk.myluckycharm.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.brightdesk.myluckycharm.MainActivity
import com.brightdesk.myluckycharm.R
import com.brightdesk.myluckycharm.render.CharmGeometry
import com.brightdesk.myluckycharm.render.DreamcatcherScene
import com.brightdesk.myluckycharm.render.SurfaceTouchRelay
import com.brightdesk.myluckycharm.settings.AppSettings
import com.brightdesk.myluckycharm.settings.PlacementMode
import com.brightdesk.myluckycharm.settings.SettingsRepository
import com.brightdesk.myluckycharm.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Hosts the charm in a WindowManager overlay so it floats over other apps
 * (spec §9). Must be a foreground service: anything long-running is otherwise
 * killed on modern Android.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var lifecycleOwner: OverlayLifecycleOwner

    /** Full-screen, untouchable, draws everything. */
    private var canvasView: ComposeView? = null

    /**
     * Small touchable windows over the charm and the anchor knob. A window that
     * can be touched swallows every touch inside it, so these are the only
     * places the app underneath goes deaf — everywhere else falls straight
     * through to whatever is on screen.
     */
    private var charmHandle: View? = null
    private var anchorHandle: View? = null

    private val relay = SurfaceTouchRelay()
    private var lastGeometry: CharmGeometry? = null
    private var lastHandleMove = 0L

    /**
     * True between a handle's DOWN and its UP, and handles are frozen for the
     * whole of it.
     *
     * Moving a window out from under a finger that is mid-gesture disturbs the
     * touch stream, and the charm's handle would otherwise chase the charm the
     * finger is dragging — which truncated drags badly, a swipe of several
     * hundred pixels moving the charm only a few dozen. Nothing needs to move
     * anyway: the window that received the DOWN goes on receiving the gesture
     * wherever it travels.
     */
    private var dragging = false

    /**
     * Where the canvas window actually starts on screen. Even asking for the
     * whole display, the window manager can seat the frame below the status
     * bar, and then surface coordinates and screen coordinates differ by that
     * much — which would offset every handle by the height of the status bar.
     */
    private val canvasOrigin = IntArray(2)

    /**
     * How far the charm must move before its handle is dragged along, in
     * pixels. Converted from dp rather than written as a pixel count, so it is
     * the same visible distance on every screen density.
     */
    private val handleMoveSlop by lazy {
        HANDLE_MOVE_SLOP_DP * resources.displayMetrics.density
    }

    /**
     * Spec §14: stop drawing while the screen is off, so a floating charm
     * doesn't quietly burn battery overnight.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    setOverlayVisible(false)
                    lifecycleOwner.pause()
                }

                Intent.ACTION_SCREEN_ON -> {
                    setOverlayVisible(true)
                    lifecycleOwner.resume()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        lifecycleOwner = OverlayLifecycleOwner().apply { create() }
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        // Whatever started us, the charm is on screen again — so the "bring it
        // back" notification a put-away tap left behind is now stale. Cancelled
        // here rather than only in the restore branch, so floating it again
        // from inside the app clears it too.
        getSystemService(NotificationManager::class.java).cancel(BRING_BACK_NOTIFICATION_ID)
        if (intent?.action == ACTION_RESTORE) {
            settingsScope.launch {
                SettingsRepository(settingsDataStore).update {
                    it.copy(placementMode = PlacementMode.FLOATING)
                }
            }
        }
        if (canvasView == null) showOverlay()

        // If the OS kills this under memory pressure the charm simply goes away
        // until reopened, rather than fighting to restart (spec §14).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Destroying the service is not enough on its own: the ongoing
        // notification can outlive it and, being NO_CLEAR, the user is then
        // stuck with a notification for a charm that is no longer there.
        stopForeground(STOP_FOREGROUND_REMOVE)
        unregisterReceiver(screenReceiver)
        removeOverlay()
        lifecycleOwner.destroy()
        super.onDestroy()
    }

    private fun showOverlay() {
        // The grant can be revoked while the service is alive; adding the view
        // without it throws, so bow out cleanly instead (spec §14).
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val repository = SettingsRepository(settingsDataStore)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val settings by repository.settings.collectAsState(initial = AppSettings())
                val scope = rememberCoroutineScope()
                MaterialTheme(colorScheme = darkColorScheme()) {
                    DreamcatcherScene(
                        settings = settings,
                        onAnchorChanged = { x, y ->
                            scope.launch {
                                repository.update { it.copy(anchorXFraction = x, anchorYFraction = y) }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        externalTouch = relay,
                        onPutAway = ::putAway,
                        onGeometryChanged = ::moveHandles,
                    )
                }
            }
        }

        lifecycleOwner.resume()
        windowManager.addView(view, canvasLayoutParams())
        canvasView = view

        // Added after the canvas so they sit above it, though nothing below can
        // compete for the touch anyway — the canvas refuses every one.
        charmHandle = addHandle()
        anchorHandle = addHandle()
    }

    private fun addHandle(): View {
        val view = HandleView(this)
        // Parked off-screen until the first frame reports where the charm is,
        // so a stale handle never blocks a touch at the origin.
        windowManager.addView(view, handleLayoutParams())
        return view
    }

    private fun removeOverlay() {
        listOfNotNull(charmHandle, anchorHandle).forEach(windowManager::removeView)
        charmHandle = null
        anchorHandle = null
        canvasView?.let { view ->
            view.disposeComposition()
            windowManager.removeView(view)
        }
        canvasView = null
        lastGeometry = null
    }

    private fun setOverlayVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        canvasView?.visibility = visibility
        charmHandle?.visibility = visibility
        anchorHandle?.visibility = visibility
    }

    /**
     * Keeps the two handle windows sitting over the things worth grabbing.
     *
     * Called every frame, but a `updateViewLayout` per frame would be far too
     * much traffic for the window manager, so a move has to be both big enough
     * and late enough to be worth making. A swinging charm therefore drags its
     * handle along a little behind it, which the generous grab radius absorbs.
     */
    private fun moveHandles(geometry: CharmGeometry) {
        if (dragging) return
        val previous = lastGeometry
        if (previous != null) {
            val now = SystemClock.uptimeMillis()
            if (now - lastHandleMove < HANDLE_MOVE_INTERVAL_MS) return
            if (geometry.withinSlopOf(previous)) return
            lastHandleMove = now
        }
        lastGeometry = geometry

        canvasView?.getLocationOnScreen(canvasOrigin)
        placeHandle(
            charmHandle, geometry.charmX, geometry.charmY,
            geometry.charmGrabRadius, geometry.charmGrabRadius,
        )
        placeHandle(
            anchorHandle, geometry.anchorX, geometry.anchorY,
            geometry.anchorGrabHalfWidth, geometry.anchorGrabHalfHeight,
        )
    }

    private fun CharmGeometry.withinSlopOf(other: CharmGeometry): Boolean =
        abs(charmX - other.charmX) < handleMoveSlop &&
            abs(charmY - other.charmY) < handleMoveSlop &&
            abs(anchorX - other.anchorX) < handleMoveSlop &&
            abs(anchorY - other.anchorY) < handleMoveSlop &&
            charmGrabRadius == other.charmGrabRadius

    /**
     * Grows the handle that just took a touch to cover the display, for as long
     * as the drag lasts.
     *
     * A window ought to keep receiving a gesture it started even once the
     * finger leaves it, and on an Activity it does. These overlay windows do
     * not: moves stopped arriving the moment the finger crossed the edge, so a
     * long drag moved the charm only about half the handle's height and then
     * stalled. Covering the screen keeps the finger inside for the whole
     * gesture. It does block the app underneath meanwhile, which is acceptable
     * for the length of a deliberate drag and is undone on release.
     */
    private fun expandForDrag(handle: View) {
        val params = handle.layoutParams as? WindowManager.LayoutParams ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        windowManager.updateViewLayout(handle, params)
    }

    private fun placeHandle(
        view: View?,
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
    ) {
        val handle = view ?: return
        val params = handle.layoutParams as? WindowManager.LayoutParams ?: return
        params.width = (halfWidth * 2f).toInt().coerceAtLeast(1)
        params.height = (halfHeight * 2f).toInt().coerceAtLeast(1)
        params.x = (centerX - halfWidth).toInt() + canvasOrigin[0]
        params.y = (centerY - halfHeight).toInt() + canvasOrigin[1]
        windowManager.updateViewLayout(handle, params)
    }

    /**
     * An invisible grab target, translating raw screen coordinates into the
     * canvas window's own space before handing them over.
     *
     * Android delivers a whole gesture to the window that received its DOWN, so
     * this stays small even while the charm is dragged far beyond it — there is
     * no need to grow it mid-drag.
     */
    private inner class HandleView(context: Context) : View(context) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.rawX - canvasOrigin[0]
            val y = event.rawY - canvasOrigin[1]
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    canvasView?.getLocationOnScreen(canvasOrigin)
                    dragging = true
                    relay.onDown?.invoke(event.rawX - canvasOrigin[0], event.rawY - canvasOrigin[1])
                    expandForDrag(this)
                }

                MotionEvent.ACTION_MOVE -> relay.onMove?.invoke(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    relay.onUp?.invoke()
                    // Forget the last placement so the next frame is free to
                    // shrink this handle back onto whatever it grabbed.
                    lastGeometry = null
                }
                else -> return false
            }
            return true
        }
    }

    private fun canvasLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // NOT_TOUCHABLE is the whole point: this window covers the screen, so
        // anything else would make the phone unusable. NOT_FOCUSABLE keeps it
        // from stealing the keyboard. NO_LIMITS makes it span the display
        // including the system bars, so its coordinates are screen coordinates
        // and the handles can be positioned from what the surface reports.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        spanTheWholeDisplay()
    }

    private fun handleLayoutParams() = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = -OFF_SCREEN
        y = -OFF_SCREEN
        // The handles are placed at absolute positions, so their frame has to
        // start where the canvas's does or every one of them is offset.
        spanTheWholeDisplay()
    }

    /**
     * Makes a window's frame the whole display rather than the area left over
     * after the system bars and the camera cutout.
     *
     * Both matter, and the cutout is the one that is easy to miss: asking for
     * MATCH_PARENT and opting out of fitting insets still leaves the frame
     * seated below the cutout, which on a phone with a punch-hole is also the
     * status bar's height. The overlay then measures ~100px shorter than the
     * activity does, and since the anchor is persisted as a *fraction* of the
     * viewport, the same saved charm hangs in a visibly different place in
     * Fixed and Floating.
     */
    private fun WindowManager.LayoutParams.spanTheWholeDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * TapAction.PUT_AWAY: three taps on the charm dismiss it.
     *
     * The placement is written back to FIXED as well as the service stopped,
     * because leaving it on FLOATING would have MainActivity's
     * `LaunchedEffect(floating)` start the overlay straight back up the next
     * time the app is opened — which reads as the gesture not working. That is
     * a deliberate difference from the ongoing notification's "Put away"
     * button, which only stops the service.
     *
     * Having written FIXED, though, the *only* way back would be opening the
     * app, so the way back is left on screen: a dismissible notification whose
     * action floats the charm again from wherever the user happens to be.
     */
    private fun putAway() {
        // Deliberately not the composition's scope: stopSelf() disposes that
        // composition, which would cancel this write along with it.
        settingsScope.launch {
            SettingsRepository(settingsDataStore).update {
                it.copy(placementMode = PlacementMode.FIXED)
            }
        }
        showBringBackNotification()
        stopSelf()
    }

    /**
     * The way back after [putAway], for a user who is in some other app.
     *
     * Not ongoing and auto-cancelling, unlike the foreground service's own
     * notification: it is a one-shot offer the user is free to swipe away, and
     * doing so simply means opening the app is the way back instead. Silent
     * either way — it shares the service's IMPORTANCE_LOW channel.
     *
     * Its intent is a *foreground* service start, and it works from the
     * background only because the user tapped a notification this app posted,
     * which is one of the documented exemptions from the background
     * foreground-service-start restrictions.
     */
    private fun showBringBackNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        ensureChannel(manager)

        val restoreIntent = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_RESTORE),
            PendingIntent.FLAG_IMMUTABLE,
        )

        manager.notify(
            BRING_BACK_NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Charm put away")
                .setContentText("Float it again without opening the app.")
                .setSmallIcon(R.drawable.ic_charm_notification)
                // The body and the button do the same thing: tapping the body
                // to undo what you just dismissed is the obvious guess, and
                // sending it to MainActivity instead would be exactly the trip
                // through the app this notification exists to avoid.
                .setContentIntent(restoreIntent)
                .addAction(Notification.Action.Builder(null, "Bring it back", restoreIntent).build())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Floating charm", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        ensureChannel(manager)

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Charm is floating")
            .setContentText("It stays on top until you put it away.")
            .setSmallIcon(R.drawable.ic_charm_notification)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, "Put away", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.brightdesk.myluckycharm.overlay.STOP"

        /** Fired by the "bring it back" notification; see [showBringBackNotification]. */
        const val ACTION_RESTORE = "com.brightdesk.myluckycharm.overlay.RESTORE"

        /**
         * Outlives any one service instance on purpose: [putAway] stops the
         * service in the same breath as it writes the placement back, and a
         * scope tied to the service (or to its composition) would be cancelled
         * before that write landed.
         */
        private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private const val NOTIFICATION_ID = 1

        /**
         * Separate from [NOTIFICATION_ID] so `stopForeground(STOP_FOREGROUND_REMOVE)`
         * on the way out takes the "charm is floating" notification with it and
         * leaves the one offering to bring it back.
         */
        private const val BRING_BACK_NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "floating_charm"

        private const val HANDLE_MOVE_INTERVAL_MS = 80L
        private const val HANDLE_MOVE_SLOP_DP = 4f
        private const val OFF_SCREEN = 10_000

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
