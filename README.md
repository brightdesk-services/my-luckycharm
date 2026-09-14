# My Lucky Charm

A lucky charm that hangs on your Android screen, on a rope that actually
simulates. Pull it, swing it, flick it — and let it hang over your other apps
if you want it there while you work.

Built with Jetpack Compose. The rope is a real verlet physics simulation, not a
looping animation.

<!-- Screenshots go here. Replace these with your own. -->
<!-- <p><img src="branding/screenshot-home.png" width="240"> <img src="branding/screenshot-floating.png" width="240"></p> -->

## What it does

- **A real rope.** Seven segments, verlet integration, distance constraints.
  Drag the charm out and release it and the rope whips back, carrying the
  momentum of your drag. It is drawn as a Catmull-Rom spline so it reads as a
  cord rather than a kinked linkage.
- **Pick your charm.** Seventeen bundled charms — Lucky Cat, Horseshoe, Evil
  Eye, Hamsa, Scarab, Daruma, Oni Mask, Temple Bell, Lucky Knot, Ash Gourd,
  Chilli & Lemon, Om Medallion, Wooden Om, Moon & Star, 786, Wooden Cross and
  Stone Cross — plus a curated palette of named emoji, any emoji you can type,
  or a photo of your own from the system picker.
- **Pull to do something.** A pull can adjust screen brightness or media
  volume. It moves as an *offset* from wherever the setting already is — pull
  down for more, push up for less — so grabbing the charm mid-video nudges the
  volume instead of resetting it. A purely sideways swing changes nothing.
- **Tap to do something.** Single, double, or triple tap can toggle the
  flashlight or mute.
- **Floating mode.** The charm hangs over your other apps. The canvas window is
  pass-through, so every touch except one on the charm itself reaches the app
  underneath — about 2.6% of the screen is "live", not the whole rectangle.
- **Tune it live.** Rope length, charm size, and stretch are sliders that feed
  the running simulation, so the rope eases to a new length under your finger
  rather than restarting.
- **Tilt.** Optional — the gravity vector follows how you hold the phone.

## Install

Grab the APK from the [Releases page](../../releases) and sideload it. You will
need to allow installs from unknown sources for whichever app you download it
with.

Requires **Android 8.0 (API 26)** or newer. The minimum is set by
`TYPE_APPLICATION_OVERLAY`, which Floating mode depends on.

## Permissions, and why each one exists

Every permission below maps to one feature. Denying any of them disables that
feature and nothing else — the app still runs.

| Permission | Why it's needed |
|---|---|
| `INTERNET` | Analytics and update checks — see below. |
| `MODIFY_AUDIO_SETTINGS` | So a pull can change media volume, if you pick volume as the pull action. Granted at install. |
| `WRITE_SETTINGS` | So a pull can change screen brightness. Used only to read and set the brightness value, nothing else in system settings. You grant this yourself through a Settings screen. |
| `SYSTEM_ALERT_WINDOW` | So the charm can be drawn over other apps in Floating mode. You grant this yourself, and only if you want Floating mode. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Floating mode runs as a foreground service so Android doesn't kill it while you use other apps. `specialUse` is declared because no standard service type fits a decorative overlay. |
| `POST_NOTIFICATIONS` | Only to show the Floating mode notification, which carries a stop button. Deny it and Floating mode still works — you stop it from inside the app. |

Hardware: `android.hardware.camera.flash` is declared `required="false"`, so the
app installs fine on devices without a flash. The tap-to-flashlight action
simply no-ops there.

### About the internet permission

The app uses [PostHog](https://posthog.com) to count **how many people have
installed it**. The same connection is intended to carry **update
notifications** in a future version; that part is not built yet.

What the permission means in practice today:

- The analytics are the SDK's default lifecycle events — `Application
  Installed`, `Application Opened`, `Application Updated` — plus the device and
  app metadata PostHog attaches to them (model, OS version, app version,
  locale, and a randomly generated device ID).
- **Session replay is off.** No screen recording, no screenshots.
- No account, no sign-in, no ads, no advertising ID.
- Your settings and any photo you choose **never leave the device**. Photos are
  copied into app-private storage and downscaled to 512px; they are never
  uploaded.
- Events are batched and queued to disk. **With no network the app works
  normally** — events buffer locally and send when connectivity returns.
  Verified on device in airplane mode.

**A build from this repository has analytics switched off.** The project key
lives in `posthog.properties`, which is not committed, so a fresh checkout
compiles with an empty key and `DreamcatcherApp` skips PostHog setup entirely.
Nothing has to be edited out, and a fork never reports into anyone else's
project. To enable it in your own build, copy `posthog.properties.example` to
`posthog.properties` and fill in your own key.

The full privacy policy is published with the app listing rather than kept in
this repository.

## Building

`java` and `gradle` are not assumed to be on your PATH. Android Studio ships a
JDK you can point at:

```bash
export JAVA_HOME="/path/to/Android Studio/jbr"

./gradlew :app:assembleDebug       # build the debug APK
./gradlew :app:testDebugUnitTest   # run the JVM unit tests
./gradlew :app:installDebug        # install to a connected device
```

No configuration is needed. Analytics stay off unless you add a
`posthog.properties` of your own, as described above.

Toolchain: AGP 9.4.0, Gradle 9.7.1, Kotlin 2.4.20, Compose BOM 2026.09.00,
compileSdk/targetSdk 37, minSdk 26. The project targets JVM 17. Versions live
in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## How it's put together

```
app/src/main/java/com/brightdesk/myluckycharm/
├── physics/    rope simulation — pure Kotlin, zero Android imports
├── render/     the Compose surface, scene, and charm drawing
├── sensors/    tilt, and the pure math behind it
├── settings/   DataStore-backed settings, the settings/tuning screens, charm picker
├── overlay/    the Floating mode service and its hand-rolled lifecycle owner
└── system/     brightness, volume, flashlight, image storage
```

Three things worth knowing if you plan to poke at it:

- **`physics/` is deliberately free of Android imports.** Pixels in, pixels
  out, with the caller doing all dp conversion. That is what lets the rope run
  in plain JVM unit tests with no Robolectric or instrumentation.
- **The simulation runs on a fixed 60Hz timestep.** The rope constants were
  tuned per-frame at 60Hz, and phones run at 90/120/144Hz, so `FixedTimestep`
  accumulates real elapsed time and steps at a constant rate. Gravity is an
  acceleration *per step*, not per second.
- **Floating mode is three windows, and that split is the design.** A touchable
  overlay swallows every touch inside its bounds, which would force a choice
  between "the charm can't be grabbed" and "a rectangle of the screen is dead".
  So there is one full-screen, untouchable canvas window that draws, plus two
  tiny invisible windows that exist only to catch touches on the charm and its
  anchor.

## Known limitations

- **Tilt assumes portrait.** Sensor axes are device-fixed, so in landscape the
  tilt direction is rotated 90°. Nothing else in the app assumes portrait.
- **Some apps suppress the overlay, and that is not a bug.** Samsung's Settings
  app sets `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` as clickjacking protection, so the
  charm is hidden over it. Try Floating mode over the launcher or a browser.
- **Sound is not built yet.** `soundEnabled` persists but has no control and
  nothing plays.

## Tested on

Galaxy S25 Ultra, Android 16, 120Hz — render, drag, pull clamp, rubber-band
stretch, settings persistence, tilt, all four bundled charms, the emoji and
photo pickers, Floating mode end to end, and ~0.3% janky frames on the charm
surface.

## License

[GNU General Public License v3.0](LICENSE).

You may use, study, modify and share this app freely. The one condition is
reciprocity: **if you distribute a modified version, you have to publish its
complete source under the GPL as well.** That keeps every derivative of this
project open, and it is why a closed-source reskin of it is not possible.

The licence covers the bundled charm art and the icon along with the code.
