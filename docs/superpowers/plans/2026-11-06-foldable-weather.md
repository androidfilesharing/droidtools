# Foldable weather Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Independent cover and inner bubble placements, opt-in approximate-location weather with honest units and states, and a side-by-side signed `DroidTools Test` APK that cannot replace Adam's installed app or pull an upstream update over itself.

**Architecture:** Pure Kotlin decides display role, overlay rebind, placement migration, temperature units, and weather status. Android classes only collect signals and perform the one overlay add/remove. A `sidetest` flavor suffixes the application id, renames the app, and turns upstream update checks off. Production `com.droidbubble.app` stays unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, JUnit4, Open-Meteo, `LocationManager` (no Play services), Android Gradle Plugin 9, compileSdk 36.

## Global Constraints

- Adam approved `docs/superpowers/specs/2026-11-06-foldable-weather-design.md`. Implement that behavior.
- Implementation request narrows classification: a wide non-foldable (tablet) is `SINGLE`, not an inner foldable panel. `smallestScreenWidthDp >= 600` means inner only when the device is foldable.
- Same `displayId` with a new role updates the existing window in place. A different `displayId` removes the old view, then adds one view. Never two overlays. Never keep a `WindowManager` from a display that is off.
- No `ACCESS_FINE_LOCATION`, no `ACCESS_BACKGROUND_LOCATION`, no location history, no continuous GPS. Coarse permission is requested only when the user turns current location on.
- No fabricated weather: no default 22°C, no synthetic hourly list, no constant UV 3, no Athens fallback for an unknown zone.
- Canonical forecast stays Celsius. Display converts. System Fahrenheit regions are exactly `US`, `BS`, `KY`, `LR`, `PW`, `FM`, `MH`.
- Weather refresh interval is 30 minutes. Reuse a coarse fix newer than 30 minutes.
- Location foreground-service type is included only while current-location weather is enabled and coarse permission is granted.
- Remove `org.gradle.java.home` from `gradle.properties`. Do not commit a Linux or Windows JDK path. Gradle uses the JVM that launches it or `JAVA_HOME`.
- `sidetest` application id is `com.droidbubble.app.test`. Launcher name is `DroidTools Test`. Prod application id stays `com.droidbubble.app`.
- `sidetest` must not query `Velocity081/droidtools` for updates. Prod update URL stays as it is.
- Do not add a `LICENSE` file. Do not print keystore passwords or private keys.
- Do not uninstall or install anything on Adam's phone.
- No analytics and no Droidbar placement changes.

---

### Task 1: Portable JDK property

**Files:**
- Modify: `gradle.properties`

**Interfaces:**
- Consumes: nothing
- Produces: Gradle no longer fails on `C:\Program Files\Android\Android Studio\jbr`

- [ ] **Step 1: Delete the Windows JDK line**

Remove this line from `gradle.properties`:

```properties
org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr
```

Leave a one-line comment in its place:

```properties
# JDK comes from JAVA_HOME or the JVM that launches Gradle. Do not pin a machine path here.
```

- [ ] **Step 2: Confirm Gradle starts**

Run: `bash ./gradlew -version`
Expected: Gradle 9.1.0 prints and the process does not report `Java home supplied is invalid`. SDK configuration errors are acceptable in this step.

- [ ] **Step 3: Commit**

```bash
git add gradle.properties
git commit -m "build: stop pinning Gradle to a Windows JDK path"
```

### Task 2: Display role and overlay rebind

**Files:**
- Create: `app/src/main/java/com/droidbubble/app/fold/DisplayPosture.kt`
- Test: `app/src/test/java/com/droidbubble/app/fold/DisplayPostureTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `enum class DisplayRole { SINGLE, COVER, INNER }`
  - `data class DisplaySnapshot(val displayId: Int, val stateOn: Boolean, val smallestWidthDp: Int, val widthPx: Int, val heightPx: Int, val hasFoldingFeature: Boolean)`
  - `fun deviceIsFoldable(deviceReportsHinge: Boolean, seenFoldingFeature: Boolean, displays: List<DisplaySnapshot>): Boolean`
  - `fun assignRoles(deviceReportsHinge: Boolean, seenFoldingFeature: Boolean, displays: List<DisplaySnapshot>): Map<Int, DisplayRole>`
  - `fun pickSettledDisplay(onDisplays: List<DisplaySnapshot>): DisplaySnapshot?`
  - `enum class OverlayAction { KEEP, UPDATE_IN_PLACE, REBIND, HIDE }`
  - `fun decideOverlayAction(hasView: Boolean, attachedDisplayId: Int?, activeDisplayId: Int?): OverlayAction`

- [ ] **Step 1: Write the failing test**

Create `DisplayPostureTest` covering:

- hinge false, seen false, one display `smallestWidthDp = 800` -> role `SINGLE` (tablet is not a folded phone)
- hinge false, seen false, one display `smallestWidthDp = 411` -> `SINGLE`
- hinge true, `smallestWidthDp = 599`, `hasFoldingFeature = false` -> `COVER`
- hinge true, `smallestWidthDp = 673`, `hasFoldingFeature = false` -> `INNER` (same-display-id unfold)
- hinge false, seen false, `hasFoldingFeature = true`, `smallestWidthDp = 400` -> `INNER` and `deviceIsFoldable` is true
- two `STATE_ON` foldable displays that both have `smallestWidthDp >= 600`: wider id is `INNER`, narrower id is `COVER`
- `pickSettledDisplay` of empty is null; of two on displays returns the larger area
- `decideOverlayAction(hasView = true, attachedDisplayId = 0, activeDisplayId = 0)` is `UPDATE_IN_PLACE`
- `decideOverlayAction(hasView = true, attachedDisplayId = 0, activeDisplayId = 1)` is `REBIND`
- `decideOverlayAction(hasView = true, attachedDisplayId = 0, activeDisplayId = null)` is `HIDE`
- `decideOverlayAction(hasView = false, attachedDisplayId = null, activeDisplayId = 1)` is `REBIND`
- `decideOverlayAction(hasView = false, attachedDisplayId = null, activeDisplayId = null)` is `HIDE`

- [ ] **Step 2: Run the test and confirm the compile failure**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.fold.DisplayPostureTest`
Expected: FAIL because `DisplayPosture` does not exist. If the SDK is missing, install it and rerun. Do not treat a missing SDK as a product failure.

- [ ] **Step 3: Implement `DisplayPosture.kt`**

```kotlin
package com.droidbubble.app.fold

enum class DisplayRole { SINGLE, COVER, INNER }

data class DisplaySnapshot(
    val displayId: Int,
    val stateOn: Boolean,
    val smallestWidthDp: Int,
    val widthPx: Int,
    val heightPx: Int,
    val hasFoldingFeature: Boolean
)

enum class OverlayAction { KEEP, UPDATE_IN_PLACE, REBIND, HIDE }

private const val INNER_SMALLEST_WIDTH_DP = 600

fun deviceIsFoldable(
    deviceReportsHinge: Boolean,
    seenFoldingFeature: Boolean,
    displays: List<DisplaySnapshot>
): Boolean {
    return deviceReportsHinge || seenFoldingFeature || displays.any { it.hasFoldingFeature }
}

fun assignRoles(
    deviceReportsHinge: Boolean,
    seenFoldingFeature: Boolean,
    displays: List<DisplaySnapshot>
): Map<Int, DisplayRole> {
    val foldable = deviceIsFoldable(deviceReportsHinge, seenFoldingFeature, displays)
    if (!foldable) {
        return displays.associate { it.displayId to DisplayRole.SINGLE }
    }
    val initial = displays.associate { display ->
        display.displayId to roleForFoldable(display)
    }
    val on = displays.filter { it.stateOn }
    if (on.size < 2) return initial
    val onRoles = on.map { initial.getValue(it.displayId) }.toSet()
    if (onRoles.size != 1) return initial
    val widest = on.maxWith(compareBy<DisplaySnapshot> { it.area() }.thenBy { it.displayId })
    val adjusted = initial.toMutableMap()
    on.forEach { display ->
        adjusted[display.displayId] =
            if (display.displayId == widest.displayId) DisplayRole.INNER else DisplayRole.COVER
    }
    return adjusted
}

fun pickSettledDisplay(onDisplays: List<DisplaySnapshot>): DisplaySnapshot? {
    if (onDisplays.isEmpty()) return null
    if (onDisplays.size == 1) return onDisplays.first()
    return onDisplays.maxWith(compareBy<DisplaySnapshot> { it.area() }.thenBy { it.displayId })
}

fun decideOverlayAction(
    hasView: Boolean,
    attachedDisplayId: Int?,
    activeDisplayId: Int?
): OverlayAction {
    if (activeDisplayId == null) return OverlayAction.HIDE
    if (!hasView || attachedDisplayId == null) return OverlayAction.REBIND
    if (attachedDisplayId != activeDisplayId) return OverlayAction.REBIND
    return OverlayAction.UPDATE_IN_PLACE
}

private fun roleForFoldable(display: DisplaySnapshot): DisplayRole {
    if (display.hasFoldingFeature || display.smallestWidthDp >= INNER_SMALLEST_WIDTH_DP) {
        return DisplayRole.INNER
    }
    return DisplayRole.COVER
}

private fun DisplaySnapshot.area(): Long = widthPx.toLong() * heightPx.toLong()
```

- [ ] **Step 4: Re-run the test**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.fold.DisplayPostureTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/fold/DisplayPosture.kt app/src/test/java/com/droidbubble/app/fold/DisplayPostureTest.kt
git commit -m "feat: classify foldable cover and inner displays"
```

### Task 3: Placement migration, edits, and cutout align

**Files:**
- Create: `app/src/main/java/com/droidbubble/app/fold/BubblePlacement.kt`
- Test: `app/src/test/java/com/droidbubble/app/fold/BubblePlacementTest.kt`

**Interfaces:**
- Consumes: `DisplayRole`
- Produces:
  - `data class BubblePlacement(val topOffsetDp: Int = 12, val horizontalOffsetDp: Int = 0, val isLeftCutout: Boolean = false, val isTeardropCutout: Boolean = false)`
  - `fun BubblePlacement.clamped(): BubblePlacement`
  - `fun migratePlacements(initialized: Boolean, legacy: BubblePlacement, cover: BubblePlacement, inner: BubblePlacement): Pair<BubblePlacement, BubblePlacement>`
  - `fun editPlacement(cover: BubblePlacement, inner: BubblePlacement, rolesSeen: Set<DisplayRole>, editing: DisplayRole, next: BubblePlacement): Pair<BubblePlacement, BubblePlacement>`
  - `data class CutoutRect(val left: Int, val top: Int, val right: Int, val bottom: Int)`
  - `fun placementFromCutout(rects: List<CutoutRect>, screenWidthPx: Int, pillHeightDp: Int, density: Float, current: BubblePlacement): BubblePlacement?`

- [ ] **Step 1: Write the failing test**

Assert:

- `migratePlacements(false, legacy = top 20 horizontal -8, cover = zeros, inner = zeros)` returns top 20 horizontal -8 for both
- `migratePlacements(true, legacy = top 99, cover = top 20, inner = top 4)` returns cover 20 and inner 4
- values outside 0..120 and -120..120 clamp
- `editPlacement` while `rolesSeen` is only `COVER` writes the new placement to both
- `editPlacement` while `rolesSeen` is `COVER` and `INNER`, editing `COVER`, leaves `INNER` unchanged
- `editPlacement` with `editing = SINGLE` writes both
- empty cutout list returns null
- a cutout centered at x=540 on a 1080px screen, density 3, pill height 36, rect top 30 bottom 90 yields horizontal 0 and top `((30+90)/2/3 - 18)` rounded, clamped

- [ ] **Step 2: Run the test and confirm failure**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.fold.BubblePlacementTest`
Expected: FAIL, type not found

- [ ] **Step 3: Implement `BubblePlacement.kt`**

Clamp top to `0..120` and horizontal to `-120..120`. `migratePlacements` copies clamped legacy into both sides only when `initialized` is false. `editPlacement` syncs both sides unless both `COVER` and `INNER` are in `rolesSeen` and `editing` is not `SINGLE`. `placementFromCutout` uses the topmost rect, sets horizontal offset from screen center in dp, sets top so the pill center matches the cutout center (`centerYdp - pillHeightDp / 2`), sets `isLeftCutout` when the cutout center is left of 30% of the screen width, and keeps `isTeardropCutout` from `current`. Return null when rects are empty, density is not positive, or screen width is not positive.

- [ ] **Step 4: Re-run the test**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/fold/BubblePlacement.kt app/src/test/java/com/droidbubble/app/fold/BubblePlacementTest.kt
git commit -m "feat: migrate and edit per-display bubble placements"
```

### Task 4: Weather units, lookup, and honest status

**Files:**
- Create: `app/src/main/java/com/droidbubble/app/weather/WeatherLogic.kt`
- Test: `app/src/test/java/com/droidbubble/app/weather/WeatherLogicTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `enum class WeatherLocationMode { MANUAL, CURRENT }`
  - `enum class WeatherUnits { SYSTEM, METRIC, IMPERIAL }`
  - `enum class TempUnit { CELSIUS, FAHRENHEIT }`
  - `enum class WeatherSource { MANUAL_CITY, CURRENT_LOCATION, TIMEZONE_ESTIMATE }`
  - `enum class WeatherStatus { OK, STALE, OFFLINE, DENIED, UNAVAILABLE }`
  - `data class WeatherSnapshot(val temperatureC: Int, val condition: String, val placeLabel: String, val highC: Int, val lowC: Int, val humidityPct: Int, val windKmh: Int, val fetchedAtEpochMs: Long, val source: WeatherSource, val status: WeatherStatus, val detail: String = "", val hasReading: Boolean = true)`
  - `fun resolveTempUnit(units: WeatherUnits, region: String): TempUnit`
  - `fun celsiusToFahrenheit(celsius: Int): Int`
  - `fun formatTemperature(celsius: Int, unit: TempUnit): String`
  - `fun formatWindKmh(kmh: Int, units: WeatherUnits, region: String): Int`
  - `fun ageStatus(snapshot: WeatherSnapshot, nowMs: Long): WeatherSnapshot`
  - `fun foldOffline(previous: WeatherSnapshot?): WeatherSnapshot?`
  - `fun foldUnavailable(previous: WeatherSnapshot?, detail: String): WeatherSnapshot?`
  - `data class ZonePlace(val latitude: Double, val longitude: Double, val name: String)`
  - `fun zonePlace(timeZoneId: String): ZonePlace?`
  - `fun forecastUrl(latitude: Double, longitude: Double): String`
  - `fun geocodeUrl(city: String): String`
  - `data class GeoHit(val latitude: Double, val longitude: Double, val placeLabel: String)`
  - `fun parseGeocode(json: String): GeoHit?`
  - `fun parseForecast(json: String, placeLabel: String, source: WeatherSource, nowMs: Long): WeatherSnapshot`
  - `enum class LocationRequest { NONE, USE_CACHED, FETCH_FRESH }`
  - `fun locationRequestFor(mode: WeatherLocationMode, permissionGranted: Boolean, cachedFixAgeMs: Long?): LocationRequest`
  - `fun weatherStatusLine(mode: WeatherLocationMode, units: WeatherUnits, region: String, snapshot: WeatherSnapshot?, denied: Boolean, nowMs: Long): String`
  - `fun encodeSnapshot(snapshot: WeatherSnapshot): String`
  - `fun decodeSnapshot(raw: String): WeatherSnapshot?`
  - `const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1000L`
  - `const val LOCATION_FIX_MAX_AGE_MS = 30L * 60L * 1000L`
  - `const val WEATHER_STALE_AFTER_MS = 2L * 60L * 60L * 1000L`

- [ ] **Step 1: Write the failing test**

Cover the spec cases:

- `US` + `SYSTEM` is Fahrenheit; `DE` + `SYSTEM` is Celsius; `METRIC` forces Celsius for `US`; `IMPERIAL` forces Fahrenheit for `DE`
- `celsiusToFahrenheit(0) == 32` and `celsiusToFahrenheit(100) == 212`
- `formatTemperature(22, CELSIUS) == "22°C"` and `formatTemperature(0, FAHRENHEIT) == "32°F"`
- `formatWindKmh(16, SYSTEM, "GB")` is mph (`(16 / 1.609344).round half-up`) and `formatWindKmh(16, SYSTEM, "DE")` stays 16
- a snapshot 3 hours old becomes `STALE` and keeps `temperatureC`
- `foldOffline(previous good)` keeps temperature and sets `OFFLINE`; `foldOffline(null)` is null
- `zonePlace("Not/AZone")` is null; `zonePlace("Europe/Athens")` is Athens; `zonePlace("America/New_York")` is New York
- `forecastUrl` contains `latitude=` and does not contain `temperature_unit`
- `geocodeUrl("New York")` contains the encoded city and does not contain `latitude=`
- `parseGeocode` of `{"results":[]}` is null
- `parseForecast` of a sample current/daily payload sets temperature, does not invent hourly, and does not set a UV field
- `locationRequestFor(CURRENT, permissionGranted = false, cachedFixAgeMs = null)` is `NONE`
- `locationRequestFor(CURRENT, true, cachedFixAgeMs = 60_000)` is `USE_CACHED`
- `locationRequestFor(MANUAL, true, null)` is `NONE`
- `locationRequestFor(CURRENT, true, cachedFixAgeMs = LOCATION_FIX_MAX_AGE_MS)` is `FETCH_FRESH`
- status line for a denied manual fallback contains `Location off`
- status line for an offline snapshot contains `Offline`
- status line for a time-zone source contains `Time zone estimate`
- encode then decode round-trips the snapshot

- [ ] **Step 2: Run the test and confirm failure**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.weather.WeatherLogicTest`
Expected: FAIL, type not found

- [ ] **Step 3: Implement `WeatherLogic.kt`**

Use `RoundingMode.HALF_UP` for Fahrenheit (`c * 9 / 5 + 32`) and for mph (`kmh / 1.609344`). `ageStatus` changes `OK` to `STALE` at `WEATHER_STALE_AFTER_MS` and leaves other statuses alone. `zonePlace` uses the existing city table in `PersistentWidgetTelemetryManager.approximateCoordsForTimeZone` and returns null for the old `else` Athens branch. `parseForecast` reads `current.temperature_2m`, `relative_humidity_2m`, `weather_code`, `wind_speed_10m`, and daily max/min. Map WMO codes with the existing `when` in `mapWeatherCodeToCondition`. `weatherStatusLine` includes the source label, `°C` or `°F`, and one of `Updated …`, `Offline, last updated …`, `Location off`, or the detail text. `encodeSnapshot` / `decodeSnapshot` use `JSONObject` field names matching the data class. Unknown JSON returns null.

- [ ] **Step 4: Re-run the test**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/weather/WeatherLogic.kt app/src/test/java/com/droidbubble/app/weather/WeatherLogicTest.kt
git commit -m "feat: add honest weather units and status rules"
```

### Task 5: Persist placements and weather settings

**Files:**
- Modify: `app/src/main/java/com/droidbubble/app/model/IslandSettings.kt`
- Modify: `app/src/main/java/com/droidbubble/app/model/PersistentWidgetModels.kt`
- Create: `app/src/main/java/com/droidbubble/app/data/IslandSettingsPreferences.kt`
- Modify: `app/src/main/java/com/droidbubble/app/data/SettingsRepository.kt`
- Test: `app/src/test/java/com/droidbubble/app/data/IslandSettingsPreferencesTest.kt`

**Interfaces:**
- Consumes: `BubblePlacement`, `DisplayRole`, `WeatherLocationMode`, `WeatherUnits`, `WeatherSnapshot`
- Produces: `IslandSettings.coverPlacement`, `innerPlacement`, `placementProfilesInitialized`, `placementRolesSeen`, `seenFoldingFeature`, `weatherLocationMode`, `weatherUnits`, `weatherLocationDenied`, `cachedWeather`. `fun readIslandSettings(preferences: Preferences): IslandSettings`. `fun writeIslandSettings(preferences: MutablePreferences, settings: IslandSettings)`. `fun IslandSettings.withActivePlacement(role: DisplayRole): IslandSettings`.

- [ ] **Step 1: Write the failing round-trip test**

Build a `MutablePreferences`, write an `IslandSettings` whose cover top is 20, inner top is 40, mode is `CURRENT`, units are `IMPERIAL`, `seenFoldingFeature` is true, and a cached snapshot exists. Read it back. Assert those fields. Assert `withActivePlacement(INNER)` copies inner offsets into `topOffsetDp` and `horizontalOffsetDp` without changing `widthDp`. Assert `withActivePlacement(SINGLE)` uses the cover placement.

- [ ] **Step 2: Run the test and confirm failure**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.data.IslandSettingsPreferencesTest`
Expected: FAIL

- [ ] **Step 3: Implement persistence**

Move the existing preference mapping into `readIslandSettings` and `writeIslandSettings` so each key is read once and written once. Add the new keys from the spec plus `placement_roles_seen` (comma-separated `COVER`/`INNER`), `seen_folding_feature`, `weather_location_mode`, `weather_units`, `weather_location_denied`, and `weather_cache`. When `placement_profiles_initialized` is false, `readIslandSettings` fills both placements from the legacy top/horizontal/cutout fields. `writeIslandSettings` writes legacy top/horizontal/cutout from `coverPlacement` when `INNER` is not the only seen role, and from `innerPlacement` when the last active mirror is inner. Store that mirror in `last_active_role`. `SettingsRepository.settingsFlow` and `updateSettings` call these two functions only.

`WeatherInfo` gains `hasReading: Boolean = false`, `temperatureLabel: String = "—"`, `statusName: String = "UNAVAILABLE"`, `sourceName: String = ""`, `detail: String = ""`, `fetchedAtEpochMs: Long = 0`. Default `temperatureC` becomes 0, `condition` and `cityName` become `""`, `hourlyForecast` becomes `emptyList()`, `uvIndex` becomes 0.

- [ ] **Step 4: Re-run the test**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/model/IslandSettings.kt app/src/main/java/com/droidbubble/app/model/PersistentWidgetModels.kt app/src/main/java/com/droidbubble/app/data/IslandSettingsPreferences.kt app/src/main/java/com/droidbubble/app/data/SettingsRepository.kt app/src/test/java/com/droidbubble/app/data/IslandSettingsPreferencesTest.kt
git commit -m "feat: persist per-display placements and weather settings"
```

### Task 6: One overlay follows the active display

**Files:**
- Modify: `app/src/main/java/com/droidbubble/app/service/IslandOverlayService.kt`
- Create: `app/src/main/java/com/droidbubble/app/fold/DisplaySignals.kt`

**Interfaces:**
- Consumes: `assignRoles`, `pickSettledDisplay`, `decideOverlayAction`, `IslandSettings.withActivePlacement`
- Produces: the service keeps one `composeView`. Configuration and display changes call `syncOverlayToActiveDisplay()`.

- [ ] **Step 1: Add `DisplaySignals.collect`**

```kotlin
fun collectDisplaySnapshots(context: Context, hasFoldingFeature: Boolean): List<DisplaySnapshot>
```

Read `DisplayManager.displays`. For each display, `stateOn` is `Display.STATE_ON`. `smallestWidthDp` comes from `context.createDisplayContext(display).resources.configuration.smallestScreenWidthDp` when that call succeeds, otherwise from `context.resources.configuration`. `widthPx` and `heightPx` come from `Display.mode.physicalWidth/Height` on API 23+ (always true here) via `display.mode`. `hasFoldingFeature` is applied only to the snapshot whose id equals `context.display.displayId`, because a service does not get a folding feature per inactive display.

- [ ] **Step 2: Rebind in `IslandOverlayService`**

Add a single-thread gate `overlaySyncInFlight` and a 200ms debounce job. `onConfigurationChanged` and a `DisplayManager.DisplayListener` (`onDisplayAdded`, `onDisplayRemoved`, `onDisplayChanged`) start that job. The job:

1. Collects snapshots.
2. Computes `assignRoles` using `PackageManager.FEATURE_SENSOR_HINGE_ANGLE` on API 30+ and `settings.seenFoldingFeature`.
3. Picks `pickSettledDisplay` of `STATE_ON` snapshots.
4. Calls `decideOverlayAction`.
5. `HIDE`: if a view exists, apply the existing 1×1 not-touchable params and return. Do not add a view.
6. `UPDATE_IN_PLACE`: copy the active role placement into `currentSettings` with `withActivePlacement`, call `updateWindowState`, and if `seenFoldingFeature` is still false while this display has a folding feature, persist it. Do not call `addView`.
7. `REBIND`: call `removeOverlay()` first, set `windowManager` from `activeDisplayContext.getSystemService(WindowManager::class.java)`, then `initOverlay()` once. If `composeView` is already non-null after `removeOverlay`, do not add again.

`initOverlay` must return immediately when `composeView != null`. The Compose content passes `liveSettings.withActivePlacement(activeRole)` into `IslandOverlayRoot`. `activeRole` is a `mutableStateOf` updated by the sync job.

Unregister the display listener in `onDestroy`.

- [ ] **Step 3: Run the posture unit tests again**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.fold.DisplayPostureTest --tests com.droidbubble.app.fold.BubblePlacementTest`
Expected: PASS. The service wiring is covered by those pure decisions plus a later emulator pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/service/IslandOverlayService.kt app/src/main/java/com/droidbubble/app/fold/DisplaySignals.kt
git commit -m "feat: keep one bubble on the active foldable display"
```

### Task 7: Dimensions page edits the active role

**Files:**
- Modify: `app/src/main/java/com/droidbubble/app/ui/main/MainScreen.kt` (`DimensionsPage`)

**Interfaces:**
- Consumes: `DisplayRole`, `editPlacement`, `placementFromCutout`
- Produces: sliders write only the active role, or both roles when they are still synced. Subtitle names the display. Cutout button is disabled with `No cutout reported` when there is no rect.

- [ ] **Step 1: Read the current role in the composition**

Use `LocalContext`, `LocalConfiguration`, and `PackageManager.FEATURE_SENSOR_HINGE_ANGLE`. Build one `DisplaySnapshot` for the activity display. `hasFoldingFeature` is false in the first composition; a `LaunchedEffect` that can see `android.view.DisplayCutout` is separate from folding. For the folding feature, call `androidx.window.layout.WindowInfoTracker.getOrCreate(context)` only when the context is an `Activity`, collect `windowLayoutInfo`, and set `hasFoldingFeature` when `info.displayFeatures.any { it is FoldingFeature }`. Persist `seenFoldingFeature = true` through `onUpdateSettings` when that happens.

Add dependency `androidx.window:window:1.4.0` in `gradle/libs.versions.toml` and `app/build.gradle.kts`.

- [ ] **Step 2: Subtitle and sliders**

Under the top bar title, show:

- `COVER` -> `Editing cover display`
- `INNER` -> `Editing inner display`
- `SINGLE` -> no subtitle

Slider ranges: top `0f..120f`, horizontal `-120f..120f`. `onValueChange` loads `coverPlacement` and `innerPlacement`, runs `editPlacement`, and also writes the legacy mirror fields from the placement returned for the edited role so older call sites keep working.

- [ ] **Step 3: Align to cutout**

Add a button `Align to this display's cutout`. Read `window.decorView.rootWindowInsets.displayCutout?.boundingRects`. Map them to `CutoutRect`. If `placementFromCutout` returns null, show the text `No cutout reported` and do not call `onUpdateSettings`. Otherwise write that placement through `editPlacement`.

- [ ] **Step 4: Compile**

Run: `bash ./gradlew :app:compileProdDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/droidbubble/app/ui/main/MainScreen.kt
git commit -m "feat: edit bubble position for the open display"
```

### Task 8: Weather fetch uses the honest state machine

**Files:**
- Modify: `app/src/main/java/com/droidbubble/app/service/PersistentWidgetTelemetryManager.kt`
- Create: `app/src/main/java/com/droidbubble/app/weather/CoarseLocationClient.kt`
- Modify: `app/src/main/java/com/droidbubble/app/island/IslandView.kt`
- Modify: `app/src/main/java/com/droidbubble/app/service/IslandOverlayService.kt`

**Interfaces:**
- Consumes: `WeatherLogic`, `WeatherSnapshot`, `locationRequestFor`
- Produces: `IslandStateManager.weatherInfo` only from real parses or from `foldOffline` / `foldUnavailable`. Bubble text uses `temperatureLabel`.

- [ ] **Step 1: Replace `fetchWeather` and `useFallbackWeather`**

Delete `useFallbackWeather`. `startWeatherMonitoring` delays `WEATHER_REFRESH_INTERVAL_MS`. Flow:

- `MANUAL` and city not blank: HTTP `geocodeUrl`. `parseGeocode` null -> `foldUnavailable(previous, "City not found")`. Hit -> `forecastUrl` only. Non-200 or exception -> `foldOffline(previous)`.
- `MANUAL` and city blank: `zonePlace`. Null -> `foldUnavailable(previous, "Time zone unavailable")`. Hit -> forecast with source `TIMEZONE_ESTIMATE`.
- `CURRENT`: `locationRequestFor`. `NONE` -> if city is not blank, run the manual city path and then set status `DENIED` when `settings.weatherLocationDenied` or permission is missing; if city is blank, `foldUnavailable(previous, "Location off")` with status `DENIED` when permission is missing. `USE_CACHED` -> forecast with the memory fix, source `CURRENT_LOCATION`. `FETCH_FRESH` -> one coarse fix, then forecast. Null fix -> same unavailable path, status `UNAVAILABLE`, and do not immediately retry.
- Success replaces the snapshot, `hasReading = true`, and writes `encodeSnapshot` through settings.
- Publish `WeatherInfo` with `hourlyForecast = emptyList()`, `uvIndex = 0`, `temperatureLabel = formatTemperature(...)` when `hasReading`, otherwise `—`. Apply `ageStatus` before publish.

Keep one `GeoFix?` in the manager. Clear it when mode is not `CURRENT` or permission is gone.

- [ ] **Step 2: `CoarseLocationClient`**

```kotlin
class CoarseLocationClient(context: Context) {
    suspend fun lastFixAgeMs(nowMs: Long): Long?
    suspend fun singleFix(timeoutMs: Long = 10_000L): GeoFix?
}
```

Use `LocationManager`. `lastFixAgeMs` checks `PASSIVE_PROVIDER` and `NETWORK_PROVIDER` `getLastKnownLocation` and returns the age of the newest fix, or null. `singleFix` on API 30+ uses `getCurrentLocation(NETWORK_PROVIDER or PASSIVE, CancellationSignal, ...)` with a coroutine timeout of 10 seconds that cancels the signal. On API 29 use `requestSingleUpdate(NETWORK_PROVIDER, ...)` and unregister in `finally`. Return null on timeout, missing provider, or `SecurityException`. Do not register a pending intent.

- [ ] **Step 3: Foreground service type**

Manifest permissions, and only these location lines:

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

Service tag `android:foregroundServiceType="specialUse|location"`.

`startForeground` on API 34+ passes `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` or that value or `FOREGROUND_SERVICE_TYPE_LOCATION` when `showWeather && mode == CURRENT && coarse permission granted`. On API 29–33 pass `FOREGROUND_SERVICE_TYPE_LOCATION` only in that same case, otherwise the existing no-type call. Re-call this from the settings collector when the mode or permission changes.

- [ ] **Step 4: Bubble text**

In `IslandView.kt`, replace `"${weatherInfo.temperatureC}°"` and `"${weatherInfo.temperatureC}°C"` with `weatherInfo.temperatureLabel`. Expanded subtitle uses `detail` when `hasReading` is false, and does not substitute `"Clear"` or `"Local Weather"`.

- [ ] **Step 5: Run weather unit tests and compile**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.weather.WeatherLogicTest :app:compileProdDebugKotlin`
Expected: PASS and BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/service/PersistentWidgetTelemetryManager.kt app/src/main/java/com/droidbubble/app/weather/CoarseLocationClient.kt app/src/main/java/com/droidbubble/app/island/IslandView.kt app/src/main/java/com/droidbubble/app/service/IslandOverlayService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: fetch weather without fabricated fallbacks"
```

### Task 9: General page controls and status

**Files:**
- Modify: `app/src/main/java/com/droidbubble/app/ui/main/MainScreen.kt` (`GeneralPage`)

**Interfaces:**
- Consumes: `weatherStatusLine`, `WeatherLocationMode`, `WeatherUnits`
- Produces: the weather row shows the status line. Current location requests `ACCESS_COARSE_LOCATION` only after the user selects it. The dialog states that an approximate position is sent to Open-Meteo and that no location history is kept.

- [ ] **Step 1: Extend the weather dialog**

Keep the city field. Add three unit choices, `System`, `Metric`, and `Imperial`, bound to `weatherUnits`. Add two mode choices, `City` and `Current location`. Choosing `City` sets `MANUAL`, clears `weatherLocationDenied`, and does not launch a permission request. Choosing `Current location` shows this text before the system dialog: `Approximate location is sent to Open-Meteo when weather refreshes. Droidtools does not keep a location history. A city name still works if you decline.` Then launch `ActivityResultContracts.RequestPermission` for `android.permission.ACCESS_COARSE_LOCATION` only. Grant sets `CURRENT` and `weatherLocationDenied = false`. Deny sets `MANUAL` and `weatherLocationDenied = true`.

- [ ] **Step 2: Status line on the row**

Supporting text under the headline is `weatherStatusLine(...)` using `Locale.getDefault().country`, the cached snapshot, and `System.currentTimeMillis()`.

- [ ] **Step 3: Compile**

Run: `bash ./gradlew :app:compileProdDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/droidbubble/app/ui/main/MainScreen.kt
git commit -m "feat: add weather units and opt-in location controls"
```

### Task 10: Side-by-side test flavor and updater lock

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/droidbubble/app/util/UpdateManager.kt`
- Modify: `app/src/main/java/com/droidbubble/app/service/IslandOverlayService.kt`
- Modify: `app/src/main/java/com/droidbubble/app/ui/main/MainScreenViewModel.kt`
- Create: `app/src/sidetest/res/values/strings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/droidbubble/app/ui/main/MainScreen.kt`
- Test: `app/src/test/java/com/droidbubble/app/util/UpdatePolicyTest.kt`

**Interfaces:**
- Consumes: `BuildConfig.UPSTREAM_UPDATE_CHECKS`
- Produces: `fun UpdateManager.upstreamUpdatesEnabled(): Boolean`. Prod true. Sidetest false.

- [ ] **Step 1: Write the failing policy test**

`UpdatePolicy.shouldCheckUpstream(enabled = false)` is false. `shouldCheckUpstream(enabled = true)` is true. The test does not hit the network.

- [ ] **Step 2: Run it and confirm failure**

Run: `bash ./gradlew :app:testProdDebugUnitTest --tests com.droidbubble.app.util.UpdatePolicyTest`
Expected: FAIL

- [ ] **Step 3: Flavors and call sites**

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("prod") {
        dimension = "distribution"
        isDefault = true
        buildConfigField("boolean", "UPSTREAM_UPDATE_CHECKS", "true")
    }
    create("sidetest") {
        dimension = "distribution"
        applicationIdSuffix = ".test"
        versionNameSuffix = "-test"
        buildConfigField("boolean", "UPSTREAM_UPDATE_CHECKS", "false")
    }
}
```

Set `buildFeatures.buildConfig = true`.

`checkGithubUpdates` returns `UpToDate` immediately when `UpdatePolicy.shouldCheckUpstream(BuildConfig.UPSTREAM_UPDATE_CHECKS)` is false, before any URL is opened. `startAutoUpdateChecker` returns before the loop in that case. `MainScreenViewModel.checkForUpdates` returns before setting `Checking` in that case.

`app/src/sidetest/res/values/strings.xml` sets `app_name` to `DroidTools Test` and `side_by_side_warning` to `Test build. Grant overlay and accessibility for DroidTools Test separately. Do not run this bubble and the original Droidtools bubble at the same time.` Main `strings.xml` sets `side_by_side_warning` to empty. The main screen shows the warning text only when it is not blank.

Authorities already use `${applicationId}` for `fileprovider` and `shizuku`, so the test app gets `.test` authorities without a second provider declaration.

- [ ] **Step 4: Re-run the policy test**

Expected: PASS for `testProdDebugUnitTest`. Also run `testSidetestDebugUnitTest --tests com.droidbubble.app.util.UpdatePolicyTest`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/droidbubble/app/util/UpdateManager.kt app/src/main/java/com/droidbubble/app/util/UpdatePolicy.kt app/src/test/java/com/droidbubble/app/util/UpdatePolicyTest.kt app/src/main/java/com/droidbubble/app/service/IslandOverlayService.kt app/src/main/java/com/droidbubble/app/ui/main/MainScreenViewModel.kt app/src/main/res/values/strings.xml app/src/sidetest/res/values/strings.xml app/src/main/java/com/droidbubble/app/ui/main/MainScreen.kt
git commit -m "feat: add a side-by-side test build that skips upstream updates"
```

### Task 11: Verify, sign the test APK, and record results

**Files:**
- Create: `local.properties` (gitignored) with `sdk.dir`
- Output: `/opt/cursor/artifacts/DroidTools-Test.apk`

- [ ] **Step 1: Unit tests**

Run: `bash ./gradlew :app:testProdDebugUnitTest :app:testSidetestDebugUnitTest`
Expected: BUILD SUCCESSFUL. Record the test counts from the XML reports.

- [ ] **Step 2: Lint**

Run: `bash ./gradlew :app:lintProdDebug :app:lintSidetestDebug`
Expected: BUILD SUCCESSFUL, or fix new errors that this change introduced. Do not disable lint globally to hide them.

- [ ] **Step 3: Assemble**

Run: `bash ./gradlew :app:assembleSidetestDebug :app:assembleProdDebug`
Expected: both APKs exist.

- [ ] **Step 4: Check packages**

`aapt dump badging` on the sidetest APK must show `name='com.droidbubble.app.test'`, `versionName='2.0.0-test'`, and application label `DroidTools Test`. The prod APK must show `name='com.droidbubble.app'` and label `Droidtools`. Confirm the sidetest APK contains no string `api.github.com/repos/Velocity081/droidtools` in its dex by checking that update calls are behind the false flag; a string can still be in prod dex. Confirm sidetest manifest has `ACCESS_COARSE_LOCATION` and does not have `ACCESS_BACKGROUND_LOCATION` or `ACCESS_FINE_LOCATION`.

Print the sidetest signer SHA-256 with `apksigner verify --print-certs`. Do not copy the debug keystore into git or into `/opt/cursor/artifacts`.

- [ ] **Step 5: Emulator**

If KVM and an emulator image install, boot a phone AVD and a foldable AVD. Install only the sidetest APK on the emulator, not on a physical phone. Exercise the weather dialog, deny location, and a configuration change. Capture screenshots. If the emulator cannot boot, record the actual error and do not claim the check ran.

- [ ] **Step 6: Copy the APK**

```bash
cp app/build/outputs/apk/sidetest/debug/Droidtools-sidetest-debug.apk /opt/cursor/artifacts/DroidTools-Test.apk
sha256sum /opt/cursor/artifacts/DroidTools-Test.apk
```

The output filename from `archivesName` may differ. Copy the real sidetest debug APK to `/opt/cursor/artifacts/DroidTools-Test.apk`.

- [ ] **Step 7: Commit remaining source and open the fork PR**

Do not commit `local.properties`, the SDK, or the APK. Push `cursor/foldable-weather-design-af3c`. Open a pull request only against `androidfilesharing/droidtools` `main`. Do not merge it and do not open an upstream PR.

## Self-review

Spec coverage:

- Two placements, one window, migration, slider limits, dimensions subtitle, cutout align: Tasks 3, 5, 6, 7.
- Same-display-id update versus display-id rebind, tablet not treated as inner, simultaneous wide panels: Task 2 and Task 6.
- Units, opt-in coarse location, 30 minute refresh, no background permission, honest failures, status line: Tasks 4, 8, 9.
- Foreground service location type only while the opt-in is active: Task 8.
- Test application id, visible name, separate grants, updater lock, no key leakage: Task 10 and Task 11.
- Missing LICENSE stays missing. No task adds one.
- Windows JDK line is removed in Task 1, which supersedes the spec sentence that said to leave the property in place. The implementation request requires that removal.

Placeholder scan: tasks name files, functions, commands, and expected results. Task 3 step 3 and Task 4 step 3 describe the full function contracts; the test step lists the exact assertions the implementation has to satisfy. No TBD.

Type consistency: `DisplayRole`, `BubblePlacement`, `WeatherSnapshot`, `locationRequestFor`, `decideOverlayAction`, and `withActivePlacement` use the same names in later tasks as in the task that defines them.
