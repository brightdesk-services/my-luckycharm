package com.brightdesk.myluckycharm.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Both permissions this app needs are "special": they cannot be requested with
 * a runtime dialog and are only granted by sending the user into system
 * Settings. Callers must re-check on resume, since the grant happens outside
 * the app and there is no result callback.
 */
object SpecialPermissions {

    /** Required to drive screen brightness (spec §7). */
    fun canWriteSystemSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSystemSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context))

    /** Required for Floating placement mode (spec §9). */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun drawOverlaysIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    /**
     * Unlike the two above this is an ordinary runtime permission, and only
     * exists from API 33. The floating service runs without it, but its
     * notification — and so the button that stops it — would be hidden.
     */
    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")
}
