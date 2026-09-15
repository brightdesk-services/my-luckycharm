package com.brightdesk.myluckycharm

import android.app.Application

/**
 * The `foss` flavor's analytics: none at all.
 *
 * This is the variant F-Droid builds. The PostHog dependency is declared as
 * `playImplementation`, so it is not merely disabled here — it is absent from
 * the classpath and the APK, and `src/foss` carries no INTERNET permission.
 * That matters because F-Droid scans the built artifact, not the runtime
 * behaviour: a bundled tracking SDK earns the Tracking anti-feature even when
 * no key is configured.
 */
object Analytics {
    fun init(app: Application) = Unit
}
