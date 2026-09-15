package com.brightdesk.myluckycharm

import android.app.Application

/**
 * The app's only reason for having an [Application] subclass: analytics have to
 * be set up once per process, and the overlay service is a second entry point
 * into the process, so doing it in [MainActivity] would leave a
 * Floating-mode-only launch uninstrumented.
 *
 * [Analytics] itself is per-flavor — real in `play`, a no-op in the `foss`
 * variant F-Droid builds — so this file knows nothing about any SDK.
 */
class DreamcatcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Analytics.init(this)
    }
}
