# Foldable bubble position and opt-in location weather

Status: design only. Do not implement until Adam approves this document.
Date: 2026-11-06
Repo: `androidfilesharing/droidtools` (fork of `Velocity081/droidtools`)
HEAD investigated: `f243508` on `main`
No upstream contact, no pull request, no release, and no device changes are part of this design.

## Findings

Droidtools is a Kotlin / Jetpack Compose overlay (minSdk 29, targetSdk 36, compileSdk 36). Version in source is `versionName` 2.0.0, `versionCode` 13. The bubble is one `WindowManager` view owned by `IslandOverlayService`. Its place on screen comes from a single DataStore record: `topOffsetDp` (default 12), `horizontalOffsetDp` (default 0), `isLeftCutout`, and `isTeardropCutout`. The Dimensions page writes those fields directly. Nothing in the repo reads hinge state, display role, `DisplayCutout`, or a second display.

`IslandOverlayService.onConfigurationChanged` only resizes that same window. It does not choose a display and it does not load a different placement. The compact window is top-centered; left-cutout mode switches it to `MATCH_PARENT`. Folding the device therefore keeps one placement. A position lined up with the cover punch-hole cannot also line up with the inner camera, and a window left on the display that just turned off is off the panel the user is looking at. Re-adding without removing first would stack a second bubble. `handleAccessibilityServiceChanged` already removes and recreates the view when the accessibility service connects, using `TYPE_ACCESSIBILITY_OVERLAY` when that service is up. That path is the right shape for a display change. It is not called on fold.

Weather is off by default (`showWeather`). When on, `PersistentWidgetTelemetryManager` polls Open-Meteo every 20 minutes. A non-blank `weatherCity` is geocoded with `geocoding-api.open-meteo.com`. A blank city uses a hard-coded time-zone table, and an unmatched zone falls through to Athens (37.98, 23.72) labeled "Local Weather". The forecast request does not set `temperature_unit` or `wind_speed_unit`, so the API returns Celsius and km/h. The bubble prints `temperatureC` with a bare `°` when compact and a hard-coded `°C` when expanded. Humidity, wind, and UV are stored. UV is always written as 3. The hourly list is invented (`temp+1`, daily high, and so on) and the expanded card does not render it. Any HTTP or parse failure replaces the reading with a fake 22°C "Partly Cloudy". The manifest has `INTERNET` and no location permission.

Open-Meteo can return Fahrenheit (`temperature_unit=fahrenheit`) and mph (`wind_speed_unit=mph`). This design converts from the default metric response instead, so a unit change does not need another request. No other provider is wired up.

There is no fold, weather, or location test. `MainScreenViewModelTest` covers a few settings defaults and media seek math. `./gradlew` is not executable (`Permission denied`); `bash ./gradlew -version` does run. That command failed for real:

`Value 'C:\Program Files\Android\Android Studio\jbr' given for org.gradle.java.home Gradle property is invalid`

`ANDROID_HOME` is unset and there is no `local.properties`. Unit tests were not run. An Android SDK is required before Gradle can configure the app module, including JVM tests.

The README points at an MIT `LICENSE`. That file is not in the tree, and the GitHub license API returns 404. Weather-city entry landed in `a136754`. No commit adds fold handling. The published upstream APK `v2.0.0-beta.2` (commit `b971680` inside the APK) is APK Signature Scheme v2 only, certificate `CN=Android Debug, O=Android, C=US`, SHA-256 `5C:EB:77:7D:5A:7B:C2:37:42:BA:D8:8E:6C:8F:29:8D:02:85:86:51:0D:56:F2:C2:F8:35:42:22:9A:E3:D1:D5`, notBefore 2026-10-17. `release` in `app/build.gradle.kts` uses the debug signing config. That private key is not in the repo. The fork has no releases. `UpdateManager` always checks `Velocity081/droidtools`.

## Recommendation

Persist two placements, cover and inner, and keep a single overlay. Classify the active display at runtime. On fold, unfold, or rotation, remove the old window if its display is no longer the active one, then add one window on the active display with that display's placement. Copy today's offsets into both placements once so an existing setup does not jump.

For weather, keep manual city as the default. Add an explicit opt-in for approximate current location (`ACCESS_COARSE_LOCATION` only), requested when the user turns that mode on. Take a low-power fix at most every 30 minutes while the island service is already running, and reuse a fix newer than 30 minutes. Do not request `ACCESS_BACKGROUND_LOCATION` and do not record a trail. Add a location foreground-service type only while this opt-in is granted and enabled, because targetSdk 36 will not allow location reads from a `specialUse`-only foreground service. Store one weather snapshot, convert Celsius for display, and stop publishing fabricated weather.

## Approaches considered

### 1. Two named placements, one window (recommended)

Store a full placement per role (`COVER`, `INNER`): top offset, horizontal offset, left cutout, teardrop. Width, height, corner radius, and scale stay global. The service keeps one `ComposeView`. A display change rebinds that view; a rotation on the same display only updates layout params.

- Existing users keep their tuned position on both screens until they edit one.
- Settings opened on either screen edit the screen they are on.
- One view field makes a duplicate bubble a logic bug we can test, not a second code path.
- Role is inferred from `smallestScreenWidthDp` and a folding feature, not from Samsung display ids, which are not stable across One UI versions.
- The 600dp breakpoint can mis-label an unusually wide cover. Adam's Fold 8 Ultra has to confirm the label on both panels. If both panels come out the same role, compare physical width and treat the larger as inner.

### 2. Key placements by raw `displayId`

Store offsets under the integer display id.

- Matches a device where cover and inner are always different ids.
- Ids are a poor key. They can swap after a reboot or an OS update, and a phone or an emulator has one id, so the feature looks broken there.
- Does not answer which id is "the inner screen" in the settings copy.

### 3. One fractional anchor for every display

Store position as a fraction of width and height and scale it on each panel.

- Survives resolution changes on a single screen.
- The cover hole and the inner camera are not the same fraction of the panel. One number cannot line both up. Rejected as the primary model.

Background location (`ACCESS_BACKGROUND_LOCATION`, "Allow all the time") was considered and rejected. The island process is already in the foreground for the whole time the bubble is running. An occasional coarse fix from a location-typed foreground service is the lesser privilege that still updates weather while the phone is folded in a pocket with the bubble up. A background permission would also read location when that service is stopped. That is not required. If the location foreground-service type is judged too strong later, the fallback is activity-only fixes plus a stale label. That fallback does not meet "while I am using the phone" and should not be the default.

## Position design

`DisplayRole` is `COVER` or `INNER`.

```
hasFoldingFeature -> INNER
else smallestScreenWidthDp >= 600 -> INNER
else COVER
```

`FoldingFeature` comes from Jetpack WindowManager (`androidx.window`). Add that dependency only. Do not add Play services.

`BubblePlacement` holds `topOffsetDp`, `horizontalOffsetDp`, `isLeftCutout`, `isTeardropCutout`. `IslandSettings` gains `coverPlacement` and `innerPlacement`. The old four fields stay as a read mirror of whichever role was last active, so a downgrade still has a position. New code reads the role-specific copy.

Migration, once, when `position_profiles_initialized` is absent:

- Write the current legacy values into both roles.
- Set the flag.
- Do not move the bubble on first launch after the update.

New installs use today's defaults for both roles (top 12, horizontal 0, center cutout).

The Dimensions page title stays. Under it, show "Editing cover display" or "Editing inner display" from the activity's current configuration. Sliders write only that role. On a phone this is always the cover label; both copies stay in sync if the device never reports an inner role, by writing the same edit to both when the classifier has only ever seen one role. That keeps a later foldable restore from inheriting a blank inner profile. Once two roles have been observed, edits stay independent.

Raise the slider limits to top 0..120 dp and horizontal -120..120 dp. The current caps (60 and ±50) are enough for a cover punch-hole and too small for a large inner panel. Stored values clamp to those limits.

"Align to this display's cutout" reads `DisplayCutout.boundingRects` for the activity's display. It sets the active role's top and horizontal offset so the compact pill center matches the cutout center, using the same coordinate space `IslandView` already uses (`horizontalOffsetDp` from screen center, `topOffsetDp` from the top). No cutout: the control is disabled and says "No cutout reported". It never runs by itself.

Service rebind, debounced about 200ms, triggered by `onConfigurationChanged` and `DisplayManager.DisplayListener`:

1. Resolve the active display. Prefer a display in `STATE_ON` whose bounds match the new configuration. If several are on during the transition, keep the current window until one remains, or after the debounce pick the widest `STATE_ON` display. Never attach a second view.
2. Classify that display.
3. If the existing view's display id differs, or the view's window type can no longer be updated in place, `removeView` and then `addView` once. Use the accessibility window type when `IslandAccessibilityService` is connected, otherwise `TYPE_APPLICATION_OVERLAY`. Build the `WindowManager` from the active display's context.
4. If no display is on yet, set the existing window to the current 1×1 not-touchable state rather than leaving the last coordinates on a dark panel.
5. Apply the active placement. Rotation on the same display and role only updates layout params.
6. `updateWindowState` continues to honor `showInLandscape` and `hideOnLockScreen`.

`IslandView` already consumes `settings.topOffsetDp` and the cutout flags. Feed it the active placement by copying those fields into the settings object the overlay collects, so the large compose tree does not grow a parallel position API.

## Weather and location design

`WeatherLocationMode`: `MANUAL` (default) or `CURRENT` (opt-in).
`WeatherUnits`: `SYSTEM` (default), `METRIC`, `IMPERIAL`.

`SYSTEM` temperature is Fahrenheit for region codes `US`, `BS`, `KY`, `LR`, `PW`, `FM`, `MH`, and Celsius otherwise. Wind, if a later UI shows the stored value, is mph for `US`, `GB`, and `LR`, otherwise km/h. The bubble shows temperature only. Compact text becomes `22°C` or `72°F` (the letter is the unit). Expanded text uses the same suffix and drops the hard-coded `°C`.

Canonical storage stays Celsius from Open-Meteo. Formatting is local: `F = C * 9/5 + 32`, half-up to an integer. Toggling units does not refetch.

`WeatherSnapshot` in memory and one DataStore cache of the last successful reading (not a list):

- temperature Celsius, condition, place label, high, low, humidity, wind km/h
- `fetchedAtEpochMs`
- `source`: `MANUAL_CITY`, `CURRENT_LOCATION`, or `TIMEZONE_ESTIMATE`
- `status`: `OK`, `STALE`, `OFFLINE`, `DENIED`, `UNAVAILABLE`

Do not persist coordinates. Do not persist a sequence of fixes. Clear the in-memory fix when the user leaves `CURRENT` or revokes permission. UV is omitted (the code writes a constant 3 today). The hourly list is omitted (it is synthetic and not drawn).

Refresh loop, only while `showWeather` is true:

- Interval 30 minutes, plus the existing manual refresh button, plus once when the mode, city, or permission changes.
- `MANUAL` and a non-blank city: geocode, then forecast. Geocoder miss: `UNAVAILABLE` with "City not found", keep the last good snapshot on screen, and do not call the forecast with the Athens coordinates.
- `MANUAL` and a blank city: time-zone table, source `TIMEZONE_ESTIMATE`, place name from the table, status `OK`. Unknown zone: `UNAVAILABLE`. Remove the Athens default.
- `CURRENT`: described below. If it cannot produce a fix and the city is non-blank, use the city path and keep status `DENIED` or `UNAVAILABLE` so the UI does not pretend the fix worked.

Location, only after the user selects current location:

1. The General page explains, before the system dialog: approximate location is sent to Open-Meteo when weather refreshes; Droidtools does not keep a location history; a city name still works if this is declined.
2. Request `ACCESS_COARSE_LOCATION` with the activity result API. Do not request fine location. On API 31+ that dialog is approximate by nature.
3. On deny: stay on `MANUAL` behavior, status `DENIED`, no further prompts until the user taps the mode again.
4. On grant: add `android:foregroundServiceType` value `location` together with the existing `specialUse`, and add the normal permission `FOREGROUND_SERVICE_LOCATION`. Call `startForeground` again with `SPECIAL_USE | LOCATION` only when mode is `CURRENT` and coarse location is granted. Otherwise start with `SPECIAL_USE` alone. minSdk 29 needs the type APIs guarded; the permission dialog itself is runtime from API 23 up.
5. Fix: if `LocationManager.getLastKnownLocation` from the passive or network provider is under 30 minutes old, use it. Otherwise one `getCurrentLocation` (API 30+) with low-power / balanced quality and a 10 second timeout, cancelled when the timeout fires. API 29 uses a single `requestSingleUpdate` on `NETWORK_PROVIDER` with the same timeout. No pending-intent listener, no geofence, no history.
6. Send that latitude and longitude to `api.open-meteo.com` with the existing current and daily fields. On success, replace the snapshot and set `fetchedAtEpochMs`.

The 20 minute loop becomes 30 minutes for every weather mode, including manual city. City weather does not need a faster poll.

## Related quality-of-life work

Only these three, all grounded in the current code:

1. Honest weather. Failures keep the last good snapshot and set `OFFLINE` or `UNAVAILABLE`. They do not write 22°C, a fake hourly list, UV 3, or Athens. A snapshot older than 2 hours is `STALE` while its numbers stay visible.
2. The Dimensions subtitle names the display being edited, and "Align to this display's cutout" seeds the active placement. Both are specified above.
3. The General weather row shows mode, resolved unit, status, and age ("Updated 12m ago", "Location off", "Offline, last updated 3h ago", "Time zone estimate"). The user can see what left the phone without logcat.

Out of scope: Droidbar's single vertical offset, a real hourly forecast UI, analytics, a new weather provider, and Play-services fused location.

## State and data flow

```
Dimensions page (active role) --> DataStore cover|inner placement
        |
        v
settingsFlow --> IslandOverlayService
        |                 |
        |                 +--> classify(active display) --> copy that placement into the
        |                       settings object IslandView already reads
        |
        +--> one overlay on the active display

General page --> mode, units, city --> DataStore
        |
        v
PersistentWidgetTelemetryManager
        |-- MANUAL: geocode or time zone --> Open-Meteo --> snapshot
        +-- CURRENT: coarse fix (memory) --> Open-Meteo --> snapshot
                          |
                          v
                    IslandStateManager.weatherInfo --> bubble text
```

DataStore keys to add: `position_profiles_initialized`, `cover_top_offset_dp`, `cover_horizontal_offset_dp`, `cover_is_left_cutout`, `cover_is_teardrop_cutout`, the same four with the `inner_` prefix, `weather_location_mode`, `weather_units`, and one cached snapshot (`weather_cache_json` or individual keys). `SettingsRepository` duplicates the read path and the update path by hand. New keys have to be added in both, or the two copies drift.

## Errors

| Situation | Behavior |
| --- | --- |
| Fold transition, two displays briefly on | Keep the current single view until debounce settles. Do not add another. |
| Active display has no placement yet | Use the migrated copy. Both roles exist after migration. |
| Classifier unsure because both panels share a role | Pick the physically wider `STATE_ON` display as inner and note it for the device test. |
| Cutout align with no cutout | Leave offsets unchanged. Disable the control. |
| City geocoder has no result | `UNAVAILABLE`, "City not found", keep last good numbers if any. |
| Current-location permission denied or later revoked | Stop fixes, drop the in-memory coordinate, fall back to city or time zone, status `DENIED`. |
| Fix times out or providers are off | Same fallback with `UNAVAILABLE`. Do not start a second request until the next interval. |
| Radio off or HTTP non-200 | Keep last good snapshot, status `OFFLINE`. If none exists, show an em dash, not 22°C. |
| Snapshot older than 2 hours | Keep numbers, status `STALE`. |
| User turns weather off | Cancel the job. Leave the last snapshot in memory but the bubble already hides the row via `showWeather`. |
| User turns current location off | `MANUAL`, clear the in-memory fix, drop the location foreground-service type on the next `startForeground`. |

## Tests

JVM, with fakes, no network and no location hardware:

- Classifier: width 599 dp and no hinge is cover; 600 dp is inner; a folding feature forces inner at any width; two same-role displays pick the wider one as inner.
- Migration: legacy top 20 and horizontal -8 land on both roles, and a later cover edit does not change inner. Flag prevents a second copy.
- Units: `US` + `SYSTEM` is °F; `DE` + `SYSTEM` is °C; `METRIC` and `IMPERIAL` override the locale. 0°C and 100°C round-trip the conversion used by the formatter.
- Weather states: an HTTP exception does not replace a good snapshot with 22°C; a denied mode does not call the location client; blank city and an unknown zone do not produce Athens; a 3 hour old snapshot is `STALE`; an offline failure with no cache is an em dash.
- URL builder still requests Celsius and does not send coordinates in `MANUAL` mode.
- Rebind helper: given "display changed", the recorded calls are remove then a single add; given "same display", there is no add.

Emulator, after implementation, not in this run:

- Phone AVD: permission dialog appears only after opt-in, deny path, manual city, airplane mode, unit toggle.
- Foldable AVD (Pixel Fold or the resizable foldable): `adb emu fold` / unfold moves one bubble, rotation keeps it on screen, Dimensions edits the open panel only.

Adam's Galaxy Fold 8 Ultra, after he installs a build he is willing to trust:

- Cover and inner labels match the panel he is looking at.
- A cover offset survives an unfold, and the inner offset is the one he set on the inner panel.
- Unfold and fold during an expanded bubble leave one bubble on the lit panel and none on the dark panel.
- Rotation on each panel.
- Cutout align, if the inner under-display camera is reported as a cutout at all. It may not be. The sliders still have to work.
- Opt-in location: approximate grant, weather refresh, revoke from system settings, and a check that no "all the time" prompt appears.
- Battery: one fix, then no further location requests inside 30 minutes (log line or a debug status age, not a new analytics event).

This run did not install an emulator image or an APK.

## Build validation

When implementation is approved:

- Do not change `org.gradle.java.home` in `gradle.properties`. That Windows path is what Adam's machine uses. On Linux, pass `-Dorg.gradle.java.home` to the real JDK or the same failure above comes back.
- `bash ./gradlew :app:testDebugUnitTest` needs an Android SDK (`local.properties` `sdk.dir` or `ANDROID_HOME`). This environment has neither, so tests could not be executed here.
- `bash ./gradlew :app:assembleDebug` is the artifact check. `assembleRelease` signs with the machine debug key and will not match upstream.

## Signing and updates

The upstream file `Droidtools.apk` from release `v2.0.0-beta.2` verifies as APK Signature Scheme v2. Certificate fingerprint:

`SHA-256: 5C:EB:77:7D:5A:7B:C2:37:42:BA:D8:8E:6C:8F:29:8D:02:85:86:51:0D:56:F2:C2:F8:35:42:22:9A:E3:D1:D5`

Subject `CN=Android Debug, O=Android, C=US`, created 2026-10-17. There is no v1 `META-INF` signature and no v3 block. `keytool -printcert -jarfile` reports "Not a signed jar file" for that reason; the v2 block is present. The keystore that produced it is not in this repo, and this design does not assume access to it.

An in-place update is possible only if the new APK is signed with that same private key and `versionCode` is greater than the installed app. Source currently says 13. The beta APK's `versionName` string is 2.0.0; its `versionCode` was not decoded because `aapt` is not installed. A different key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Do not uninstall to work around that.

Practical plan once implementation is approved:

- Build `assembleDebug` or an unsigned release for review.
- Print the signing cert SHA-256 next to the fingerprint above.
- If they differ, stop. Hand Adam the APK and say it will not update the installed app. Do not uninstall, do not clear data, and do not change his device.
- An in-place update happens only if he later supplies the keystore that matches the fingerprint. Put that file outside the repo and outside the chat log.
- Any fork build that keeps `UpdateManager`'s `Velocity081/droidtools` URL will offer the upstream APK. Installing that over a differently signed fork fails; installing it over the original app replaces the fork's code with upstream. Point update checks at this fork, or disable them, in the same change that ships a fork build. That is a release constraint, not a new feature.

## Blockers

- Adam has not approved implementation.
- Linux Gradle fails on the Windows `org.gradle.java.home` until it is overridden for that invocation.
- No Android SDK in this environment, so the app module cannot be configured or tested here.
- No upstream signing key. In-place update is impossible without it.
- Cover versus inner on the Fold 8 Ultra cannot be proved here. The 600dp rule is a hypothesis until he checks both panels.
- The inner camera may not be exposed as `DisplayCutout`. Alignment then depends on the sliders.
- `LICENSE` is missing while the README claims MIT. This design does not add one.
