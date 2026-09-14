package com.brightdesk.myluckycharm.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brightdesk.myluckycharm.BuildConfig

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onDone: () -> Unit,
    hasBrightnessPermission: Boolean,
    onGrantBrightnessPermission: () -> Unit,
    hasOverlayPermission: Boolean,
    onGrantOverlayPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    onGrantNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every change here already applies live (each option writes through
    // onChange as soon as it's picked), so there's nothing left to "submit" —
    // the system back gesture/button is the only way out, rather than a
    // Done button that would misleadingly read as a save step.
    BackHandler(onBack = onDone)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            TextButton(
                onClick = {
                    onChange {
                        it.copy(
                            anchorXFraction = AppSettings.DEFAULT_ANCHOR_X_FRACTION,
                            anchorYFraction = AppSettings.DEFAULT_ANCHOR_Y_FRACTION,
                        )
                    }
                },
            ) {
                Text("Reset position")
            }
        }

        OptionGroup(
            title = "Placement",
            options = PlacementMode.entries,
            selected = settings.placementMode,
            label = { mode ->
                when (mode) {
                    PlacementMode.FIXED -> "Fixed — inside this app"
                    PlacementMode.FLOATING -> "Floating — over other apps"
                }
            },
            onSelect = { mode -> onChange { it.copy(placementMode = mode) } },
        )

        if (settings.placementMode == PlacementMode.FLOATING && !hasOverlayPermission) {
            Text(
                "Floating needs permission to draw over other apps. " +
                    "Until that's granted the charm stays inside this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onGrantOverlayPermission, modifier = Modifier.padding(top = 4.dp)) {
                Text("Grant permission")
            }
        }

        OptionGroup(
            title = "Pull controls",
            options = PullAction.entries,
            selected = settings.pullAction,
            label = { action ->
                when (action) {
                    PullAction.NONE -> "Nothing"
                    PullAction.BRIGHTNESS -> "Screen brightness"
                    PullAction.VOLUME -> "Media volume"
                }
            },
            onSelect = { action -> onChange { it.copy(pullAction = action) } },
        )

        if (settings.pullAction != PullAction.NONE) {
            Text(
                "Pull the charm down to change this. Sideways swings don't count.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (settings.pullAction == PullAction.BRIGHTNESS && !hasBrightnessPermission) {
            Text(
                "Changing brightness needs permission to modify system settings. " +
                    "Until that's granted, pulling the charm won't change anything.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onGrantBrightnessPermission, modifier = Modifier.padding(top = 4.dp)) {
                Text("Grant permission")
            }
        }

        // Which charm hangs on the rope is picked on the home screen instead,
        // in the bottom sheet under the live charm — see CharmSheet. Keeping a
        // second copy here would have invited the two to drift, the same reason
        // "Size and feel" left for Tune.

        // Put away is offered on triple tap only. It is the one action that
        // makes the charm disappear, and a single or double tap on something
        // you also drag is far too easy to land by accident for that.
        val everydayTapActions = TapAction.entries - TapAction.PUT_AWAY

        TapActionGroup(
            title = "Single tap",
            options = everydayTapActions,
            selected = settings.singleTapAction,
            onSelect = { action -> onChange { it.copy(singleTapAction = action) } },
        )
        TapActionGroup(
            title = "Double tap",
            options = everydayTapActions,
            selected = settings.doubleTapAction,
            onSelect = { action -> onChange { it.copy(doubleTapAction = action) } },
        )
        TapActionGroup(
            title = "Triple tap",
            options = TapAction.entries,
            selected = settings.tripleTapAction,
            onSelect = { action -> onChange { it.copy(tripleTapAction = action) } },
            // Nothing to put away while the charm lives inside this app, so the
            // option greys out rather than silently doing nothing when picked.
            enabled = { action ->
                action != TapAction.PUT_AWAY ||
                    settings.placementMode == PlacementMode.FLOATING
            },
        )
        if (settings.singleTapAction != TapAction.NONE ||
            settings.doubleTapAction != TapAction.NONE ||
            settings.tripleTapAction != TapAction.NONE
        ) {
            Text(
                "Tap the charm itself, not the rope.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (settings.tripleTapAction == TapAction.PUT_AWAY) {
            Text(
                "Three taps hide the floating charm. A notification brings it back.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OptionGroup(
            // Tilt-driven gravity hasn't seen the same device coverage as the
            // rest of the app — flagged rather than hidden, since it already works.
            title = "Tilt (Experimental)",
            options = TiltSource.entries,
            selected = settings.tiltSource,
            label = { source ->
                when (source) {
                    TiltSource.OFF -> "Off — gravity points straight down"
                    TiltSource.ACCELEROMETER -> "Accelerometer"
                    TiltSource.GYROSCOPE -> "Gyroscope — drift corrected"
                }
            },
            onSelect = { source -> onChange { it.copy(tiltSource = source) } },
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nothing here is required — each one only unlocks the feature next to it.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )

        PermissionRow(
            title = "Appear on top",
            reason = "Lets the charm float over your other apps.",
            granted = hasOverlayPermission,
            onGrant = onGrantOverlayPermission,
        )
        PermissionRow(
            title = "Modify system settings",
            reason = "Lets pulling the charm change screen brightness.",
            granted = hasBrightnessPermission,
            onGrant = onGrantBrightnessPermission,
        )
        PermissionRow(
            title = "Notifications",
            reason = "Shows the button that puts a floating charm away.",
            granted = hasNotificationPermission,
            onGrant = onGrantNotificationPermission,
        )

        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(reason, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) {
            Text(
                "Granted",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun TapActionGroup(
    title: String,
    options: List<TapAction>,
    selected: TapAction,
    onSelect: (TapAction) -> Unit,
    enabled: (TapAction) -> Boolean = { true },
) {
    OptionGroup(
        title = title,
        options = options,
        selected = selected,
        label = { action ->
            when (action) {
                TapAction.NONE -> "Nothing"
                TapAction.TOGGLE_MUTE -> "Toggle mute"
                TapAction.TOGGLE_FLASHLIGHT -> "Toggle flashlight"
                TapAction.PUT_AWAY -> "Put the floating charm away"
            }
        },
        onSelect = onSelect,
        enabled = enabled,
    )
}

@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: (T) -> Boolean = { true },
) {
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            val isEnabled = enabled(option)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        enabled = isEnabled,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null, enabled = isEnabled)
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
