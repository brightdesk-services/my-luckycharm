package com.brightdesk.myluckycharm

import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/**
 * The `play` flavor's analytics: an anonymous install/open count through
 * PostHog.
 *
 * The project key comes from `posthog.properties`, which is gitignored, via
 * `BuildConfig`. A checkout without that file gets an empty key and simply runs
 * with analytics off — so a fork builds and works without reporting into
 * somebody else's PostHog project, and nothing has to be edited out by hand.
 *
 * The `foss` flavor replaces this whole file with a no-op; keep the two
 * signatures identical or the other variant stops compiling.
 */
object Analytics {

    fun init(app: Application) {
        if (BuildConfig.POSTHOG_API_KEY.isEmpty()) return

        PostHogAndroid.setup(
            app,
            PostHogAndroidConfig(
                apiKey = BuildConfig.POSTHOG_API_KEY,
                host = BuildConfig.POSTHOG_HOST,
            ),
        )
    }
}
