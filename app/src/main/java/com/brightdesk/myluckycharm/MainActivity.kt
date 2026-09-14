package com.brightdesk.myluckycharm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.brightdesk.myluckycharm.overlay.OverlayService
import com.brightdesk.myluckycharm.render.DreamcatcherScene
import com.brightdesk.myluckycharm.settings.AppSettings
import com.brightdesk.myluckycharm.settings.CharmSheet
import com.brightdesk.myluckycharm.settings.CharmType
import com.brightdesk.myluckycharm.settings.PlacementMode
import com.brightdesk.myluckycharm.settings.SettingsRepository
import com.brightdesk.myluckycharm.settings.SettingsScreen
import com.brightdesk.myluckycharm.settings.TuningScreen
import com.brightdesk.myluckycharm.settings.settingsDataStore
import com.brightdesk.myluckycharm.system.CharmImageStore
import com.brightdesk.myluckycharm.system.SpecialPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NightSky = Color(0xFF12101B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() — it installs the window that the
        // platform (API 31+) or the compat library (below it) shows in place
        // of a blank window while this activity's first frame is prepared.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(settingsDataStore)
        enableEdgeToEdge()

        setContent {
            val settings by repository.settings.collectAsState(initial = AppSettings())
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showTuning by rememberSaveable { mutableStateOf(false) }
            var showCharmSheet by rememberSaveable { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            // Special permissions are granted out in system Settings with no
            // result callback, so the only way to notice is to re-check on resume.
            var canWriteSettings by remember {
                mutableStateOf(SpecialPermissions.canWriteSystemSettings(context))
            }
            var canDrawOverlays by remember {
                mutableStateOf(SpecialPermissions.canDrawOverlays(context))
            }
            var canPostNotifications by remember {
                mutableStateOf(SpecialPermissions.canPostNotifications(context))
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, context) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        canWriteSettings = SpecialPermissions.canWriteSystemSettings(context)
                        canDrawOverlays = SpecialPermissions.canDrawOverlays(context)
                        canPostNotifications = SpecialPermissions.canPostNotifications(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val requestNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                // The overlay runs either way; only its notification depends on this.
                canPostNotifications = granted
            }

            val pickPhoto = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        // Copy off the main thread; the picked image can be large.
                        val path = withContext(Dispatchers.IO) { CharmImageStore.save(context, uri) }
                        if (path != null) {
                            repository.update {
                                it.copy(charmType = CharmType.CUSTOM_IMAGE, customImagePath = path)
                            }
                        }
                    }
                }
            }

            // Both the home screen and the floating stand-in offer the strip,
            // so the launch call lives here rather than being duplicated.
            val launchPhotoPicker = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }

            // Tuning draws its own live charm preview in-app, and the overlay's
            // canvas window draws on top of everything else on screen — leaving
            // both up at once shows two charms fighting for the same spot, one
            // stale and one live. Pausing the overlay while Tuning is open (and
            // letting it restart once it closes) keeps only one on screen.
            val floating = settings.placementMode == PlacementMode.FLOATING &&
                canDrawOverlays &&
                !showTuning

            LaunchedEffect(floating) {
                if (floating) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    OverlayService.start(context)
                } else {
                    OverlayService.stop(context)
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(background = NightSky)) {
                Surface(modifier = Modifier.fillMaxSize(), color = NightSky) {
                    when {
                        showTuning -> TuningScreen(
                            settings = settings,
                            onChange = { transform -> scope.launch { repository.update(transform) } },
                            onDone = { showTuning = false },
                        )

                        showSettings -> SettingsScreen(
                            settings = settings,
                            onChange = { transform -> scope.launch { repository.update(transform) } },
                            onDone = { showSettings = false },
                            hasBrightnessPermission = canWriteSettings,
                            onGrantBrightnessPermission = {
                                context.startActivity(SpecialPermissions.writeSystemSettingsIntent(context))
                            },
                            hasOverlayPermission = canDrawOverlays,
                            onGrantOverlayPermission = {
                                context.startActivity(SpecialPermissions.drawOverlaysIntent(context))
                            },
                            hasNotificationPermission = canPostNotifications,
                            onGrantNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.safeDrawingPadding(),
                        )

                        // Drawing the charm here too would leave two of them on screen.
                        floating -> FloatingStatus(
                            onOpenCharmSheet = { showCharmSheet = true },
                            onBringBack = {
                                scope.launch {
                                    repository.update { it.copy(placementMode = PlacementMode.FIXED) }
                                }
                            },
                            onOpenTuning = { showTuning = true },
                            onOpenSettings = { showSettings = true },
                            modifier = Modifier.safeDrawingPadding(),
                        )

                        else -> Box(Modifier.fillMaxSize()) {
                            DreamcatcherScene(
                                settings = settings,
                                onAnchorChanged = { x, y ->
                                    scope.launch {
                                        repository.update {
                                            it.copy(anchorXFraction = x, anchorYFraction = y)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .safeDrawingPadding()
                                    .padding(8.dp),
                            ) {
                                TextButton(onClick = { showCharmSheet = true }) { Text("Charm") }
                                TextButton(onClick = { showTuning = true }) { Text("Tune") }
                                TextButton(onClick = { showSettings = true }) { Text("Settings") }
                            }
                        }
                    }

                    // Outside the `when` because the sheet is a layer over
                    // whichever screen opened it, not a screen of its own —
                    // and both the home screen and the floating stand-in open
                    // it. It stays composed while hidden: it lives in this
                    // window rather than one of its own, so `visible` drives an
                    // ordinary enter/exit transition.
                    CharmSheet(
                        visible = showCharmSheet,
                        settings = settings,
                        onChange = { transform -> scope.launch { repository.update(transform) } },
                        onPickPhoto = launchPhotoPicker,
                        onDismiss = { showCharmSheet = false },
                    )
                }
            }
        }
    }
}

/**
 * Stands in for the home screen while the charm is floating, so it offers the
 * same charm picker. No charm is drawn here — the overlay is already drawing
 * one on top of this window — but the overlay collects the same settings, so a
 * pick in the sheet swaps the floating charm live all the same.
 */
@Composable
private fun FloatingStatus(
    onBringBack: () -> Unit,
    onOpenCharmSheet: () -> Unit,
    onOpenTuning: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("The charm is floating", style = MaterialTheme.typography.headlineSmall)
        Text(
            "It stays on top of your other apps until you put it away.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onBringBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Bring it back into the app")
        }
        TextButton(onClick = onOpenCharmSheet) {
            Text("Charm")
        }
        TextButton(onClick = onOpenTuning) {
            Text("Tune")
        }
        TextButton(onClick = onOpenSettings) {
            Text("Settings")
        }
    }
}
