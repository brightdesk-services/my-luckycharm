package com.brightdesk.myluckycharm

import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * The app's only reason for having an [Application] subclass: PostHog has to be
 * set up once per process, and the overlay service is a second entry point into
 * the process, so doing it in [MainActivity] would leave a Floating-mode-only
 * launch uninstrumented.
 *
 * The project key comes from `posthog.properties`, which is gitignored, via
 * `BuildConfig`. A checkout without that file gets an empty key and simply runs
 * with analytics off — so a fork builds and works without reporting into
 * somebody else's PostHog project, and nothing has to be edited out by hand.
 */
class DreamcatcherApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.POSTHOG_API_KEY.isEmpty()) return

        PostHogAndroid.setup(
            this,
            PostHogAndroidConfig(
                apiKey = BuildConfig.POSTHOG_API_KEY,
                host = BuildConfig.POSTHOG_HOST,
            ),
        )
    }
}
