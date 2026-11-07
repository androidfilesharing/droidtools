package com.droidbubble.app.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.droidbubble.app.fold.BubblePlacement
import com.droidbubble.app.fold.DisplayRole
import com.droidbubble.app.fold.clamped
import com.droidbubble.app.model.DEFAULT_FEATURE_PRIORITIES
import com.droidbubble.app.model.DEFAULT_MEDIA_LAYERS
import com.droidbubble.app.model.DesignLanguage
import com.droidbubble.app.model.IslandSettings
import com.droidbubble.app.weather.WeatherLocationMode
import com.droidbubble.app.weather.WeatherUnits

private object Keys {
    val IS_ENABLED = booleanPreferencesKey("is_enabled")
    val WIDTH_DP = intPreferencesKey("width_dp")
    val HEIGHT_DP = intPreferencesKey("height_dp")
    val CORNER_RADIUS_DP = intPreferencesKey("corner_radius_dp")
    val TOP_OFFSET_DP = intPreferencesKey("top_offset_dp")
    val HORIZONTAL_OFFSET_DP = intPreferencesKey("horizontal_offset_dp")
    val IS_LEFT_CUTOUT = booleanPreferencesKey("is_left_cutout")
    val IS_TEARDROP_CUTOUT = booleanPreferencesKey("is_teardrop_cutout")
    val SHOW_OUTLINE = booleanPreferencesKey("show_outline")
    val OUTLINE_COLOR_ARGB = longPreferencesKey("outline_color_argb")
    val OUTLINE_THICKNESS_DP = floatPreferencesKey("outline_thickness_dp")
    val EXPANDED_SCALE = floatPreferencesKey("expanded_scale")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val SHOW_CALLS = booleanPreferencesKey("show_calls")
    val SHOW_MEDIA = booleanPreferencesKey("show_media")
    val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
    val SHOW_LIVE_PROGRESS = booleanPreferencesKey("show_live_progress")
    val SHOW_SCREEN_RECORDING = booleanPreferencesKey("show_screen_recording")
    val SHOW_FLASHLIGHT = booleanPreferencesKey("show_flashlight")
    val SHOW_BLUETOOTH = booleanPreferencesKey("show_bluetooth")
    val SHOW_CHARGING = booleanPreferencesKey("show_charging")
    val SHOW_RINGER_MODE = booleanPreferencesKey("show_ringer_mode")
    val SHOW_AIRPLANE_MODE = booleanPreferencesKey("show_airplane_mode")
    val SHOW_IN_LANDSCAPE = booleanPreferencesKey("show_in_landscape")
    val ANIMATION_SPEED_MULTIPLIER = floatPreferencesKey("anim_speed_multiplier")
    val LEGACY_ANIMATION_SPEED = stringPreferencesKey("animation_speed")
    val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    val RESTART_ON_BOOT = booleanPreferencesKey("restart_on_boot")
    val BUBBLE_EFFECT_ENABLED = booleanPreferencesKey("bubble_effect_enabled")
    val DISMISS_MEDIA_ON_PAUSE = booleanPreferencesKey("dismiss_media_on_pause")
    val ALWAYS_SHOW_PILL = booleanPreferencesKey("always_show_pill")
    val SHOW_DUAL_ACTIVITY_IN_COMPACT = booleanPreferencesKey("show_dual_activity_in_compact")
    val SHOW_FPS = booleanPreferencesKey("show_fps")
    val SHOW_WEATHER = booleanPreferencesKey("show_weather")
    val SHOW_NETWORK_SPEED = booleanPreferencesKey("show_network_speed")
    val SHOW_ALARM = booleanPreferencesKey("show_alarm")
    val AUTO_DISMISS_NOTIFICATIONS = booleanPreferencesKey("auto_dismiss_notifications")
    val NOTIFICATION_AUTO_DISMISS_SECONDS = intPreferencesKey("notification_auto_dismiss_seconds")
    val PERSISTENT_CHARGING = booleanPreferencesKey("persistent_charging")
    val NOTIFICATION_MAX_LINES = intPreferencesKey("notification_max_lines")
    val WEATHER_CITY = stringPreferencesKey("weather_city")
    val FONT_KEY = stringPreferencesKey("font_key")
    val SWAP_ACTIVITIES_ON_SWIPE = booleanPreferencesKey("swap_activities_on_swipe")
    val BLURRED_ALBUM_ART_BACKGROUND = booleanPreferencesKey("blurred_album_art_background")
    val DESIGN_LANGUAGE = stringPreferencesKey("design_language")
    val GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
    val GLASS_OPACITY = intPreferencesKey("glass_opacity")
    val MEDIA_WAVE_ANIMATION = booleanPreferencesKey("media_wave_animation")
    val SPINNING_ALBUM_ART = booleanPreferencesKey("spinning_album_art")
    val WAVY_PROGRESS_BAR = booleanPreferencesKey("wavy_progress_bar")
    val HIDE_ON_LOCK_SCREEN = booleanPreferencesKey("hide_on_lock_screen")
    val EXPAND_DAMPING = floatPreferencesKey("expand_damping")
    val EXPAND_STIFFNESS = floatPreferencesKey("expand_stiffness")
    val APPEAR_DAMPING = floatPreferencesKey("appear_damping")
    val APPEAR_STIFFNESS = floatPreferencesKey("appear_stiffness")
    val SPLIT_DAMPING = floatPreferencesKey("split_damping")
    val SPLIT_STIFFNESS = floatPreferencesKey("split_stiffness")
    val FEATURE_PRIORITIES = stringPreferencesKey("feature_priorities")
    val MEDIA_CONTROL_ALIGNMENT = stringPreferencesKey("media_control_alignment")
    val MEDIA_LAYERS_ORDER = stringPreferencesKey("media_layers_order")
    val DROIDBAR_ENABLED = booleanPreferencesKey("droidbar_enabled")
    val DROIDBAR_SHOW_FLASHLIGHT = booleanPreferencesKey("droidbar_show_flashlight")
    val DROIDBAR_SHOW_RING_MODE = booleanPreferencesKey("droidbar_show_ring_mode")
    val DROIDBAR_SHOW_SCREENSHOT = booleanPreferencesKey("droidbar_show_screenshot")
    val DROIDBAR_SHOW_WIFI = booleanPreferencesKey("droidbar_show_wifi")
    val DROIDBAR_SHOW_MOBILE_DATA = booleanPreferencesKey("droidbar_show_mobile_data")
    val DROIDBAR_USE_MOST_USED_APPS = booleanPreferencesKey("droidbar_use_most_used_apps")
    val DROIDBAR_CUSTOM_APPS = stringPreferencesKey("droidbar_custom_apps")
    val DROIDBAR_BACKGROUND_OPACITY = floatPreferencesKey("droidbar_background_opacity")
    val DROIDBAR_MAX_HEIGHT_DP = intPreferencesKey("droidbar_max_height_dp")
    val DROIDBAR_Y_OFFSET_DP = intPreferencesKey("droidbar_y_offset_dp")
    val DROIDBAR_HAPTICS_ENABLED = booleanPreferencesKey("droidbar_haptics_enabled")
    val PLACEMENT_PROFILES_INITIALIZED = booleanPreferencesKey("position_profiles_initialized")
    val PLACEMENT_ROLES_SEEN = stringPreferencesKey("placement_roles_seen")
    val SEEN_FOLDING_FEATURE = booleanPreferencesKey("seen_folding_feature")
    val LAST_ACTIVE_ROLE = stringPreferencesKey("last_active_role")
    val WEATHER_LOCATION_MODE = stringPreferencesKey("weather_location_mode")
    val WEATHER_UNITS = stringPreferencesKey("weather_units")
    val WEATHER_LOCATION_DENIED = booleanPreferencesKey("weather_location_denied")
    val WEATHER_CACHE = stringPreferencesKey("weather_cache")
}

fun readIslandSettings(preferences: Preferences): IslandSettings {
    val resolvedSpeed = preferences[Keys.ANIMATION_SPEED_MULTIPLIER] ?: run {
        when (preferences[Keys.LEGACY_ANIMATION_SPEED]) {
            "FAST" -> 1.5f
            "SLOW" -> 0.7f
            else -> 0.7f
        }
    }
    val legacy = BubblePlacement(
        topOffsetDp = preferences[Keys.TOP_OFFSET_DP] ?: 12,
        horizontalOffsetDp = preferences[Keys.HORIZONTAL_OFFSET_DP] ?: 0,
        isLeftCutout = preferences[Keys.IS_LEFT_CUTOUT] ?: false,
        isTeardropCutout = preferences[Keys.IS_TEARDROP_CUTOUT] ?: false
    ).clamped()
    val initialized = preferences[Keys.PLACEMENT_PROFILES_INITIALIZED] ?: false
    val cover = if (initialized) readPlacement(preferences, "cover", legacy) else legacy
    val inner = if (initialized) readPlacement(preferences, "inner", legacy) else legacy
    val mirror = when (enumOrNull<DisplayRole>(preferences[Keys.LAST_ACTIVE_ROLE])) {
        DisplayRole.INNER -> inner
        else -> cover
    }
    return IslandSettings(
        isEnabled = preferences[Keys.IS_ENABLED] ?: true,
        widthDp = preferences[Keys.WIDTH_DP] ?: 180,
        heightDp = preferences[Keys.HEIGHT_DP] ?: 36,
        cornerRadiusDp = preferences[Keys.CORNER_RADIUS_DP] ?: 22,
        topOffsetDp = mirror.topOffsetDp,
        horizontalOffsetDp = mirror.horizontalOffsetDp,
        isLeftCutout = mirror.isLeftCutout,
        isTeardropCutout = mirror.isTeardropCutout,
        showOutline = preferences[Keys.SHOW_OUTLINE] ?: true,
        outlineColorArgb = preferences[Keys.OUTLINE_COLOR_ARGB] ?: 0x33FFFFFF,
        outlineThicknessDp = preferences[Keys.OUTLINE_THICKNESS_DP] ?: 1.5f,
        expandedScale = preferences[Keys.EXPANDED_SCALE] ?: 1.1f,
        hapticsEnabled = preferences[Keys.HAPTICS_ENABLED] ?: true,
        showCalls = preferences[Keys.SHOW_CALLS] ?: true,
        showMedia = preferences[Keys.SHOW_MEDIA] ?: true,
        showNotifications = preferences[Keys.SHOW_NOTIFICATIONS] ?: false,
        showLiveProgress = preferences[Keys.SHOW_LIVE_PROGRESS] ?: true,
        showScreenRecording = preferences[Keys.SHOW_SCREEN_RECORDING] ?: true,
        showFlashlight = preferences[Keys.SHOW_FLASHLIGHT] ?: true,
        showBluetooth = preferences[Keys.SHOW_BLUETOOTH] ?: true,
        showCharging = preferences[Keys.SHOW_CHARGING] ?: true,
        showRingerMode = preferences[Keys.SHOW_RINGER_MODE] ?: true,
        showAirplaneMode = preferences[Keys.SHOW_AIRPLANE_MODE] ?: true,
        showInLandscape = preferences[Keys.SHOW_IN_LANDSCAPE] ?: false,
        animationSpeed = resolvedSpeed,
        blockedPackages = preferences[Keys.BLOCKED_PACKAGES] ?: emptySet(),
        restartOnBoot = preferences[Keys.RESTART_ON_BOOT] ?: true,
        bubbleEffectEnabled = preferences[Keys.BUBBLE_EFFECT_ENABLED] ?: true,
        dismissMediaOnPause = preferences[Keys.DISMISS_MEDIA_ON_PAUSE] ?: false,
        alwaysShowPill = preferences[Keys.ALWAYS_SHOW_PILL] ?: false,
        showDualActivityInCompact = preferences[Keys.SHOW_DUAL_ACTIVITY_IN_COMPACT] ?: true,
        showFps = preferences[Keys.SHOW_FPS] ?: false,
        showWeather = preferences[Keys.SHOW_WEATHER] ?: false,
        showNetworkSpeed = preferences[Keys.SHOW_NETWORK_SPEED] ?: false,
        showAlarm = preferences[Keys.SHOW_ALARM] ?: true,
        autoDismissNotifications = preferences[Keys.AUTO_DISMISS_NOTIFICATIONS] ?: false,
        notificationAutoDismissSeconds = preferences[Keys.NOTIFICATION_AUTO_DISMISS_SECONDS] ?: 3,
        persistentCharging = preferences[Keys.PERSISTENT_CHARGING] ?: false,
        notificationMaxLines = preferences[Keys.NOTIFICATION_MAX_LINES] ?: 2,
        weatherCity = preferences[Keys.WEATHER_CITY] ?: "",
        fontKey = preferences[Keys.FONT_KEY] ?: "DEFAULT",
        swapActivitiesOnSwipe = preferences[Keys.SWAP_ACTIVITIES_ON_SWIPE] ?: true,
        blurredAlbumArtBackground = preferences[Keys.BLURRED_ALBUM_ART_BACKGROUND] ?: false,
        designLanguage = preferences[Keys.DESIGN_LANGUAGE]?.let { enumOrNull<DesignLanguage>(it) }
            ?: if (preferences[Keys.GLASS_ENABLED] == true) DesignLanguage.GLASS else DesignLanguage.MATERIAL3,
        glassEnabled = preferences[Keys.GLASS_ENABLED] ?: false,
        glassOpacity = preferences[Keys.GLASS_OPACITY] ?: 25,
        mediaWaveAnimation = preferences[Keys.MEDIA_WAVE_ANIMATION] ?: true,
        spinningAlbumArt = preferences[Keys.SPINNING_ALBUM_ART] ?: false,
        wavyProgressBar = preferences[Keys.WAVY_PROGRESS_BAR] ?: true,
        hideOnLockScreen = preferences[Keys.HIDE_ON_LOCK_SCREEN] ?: false,
        expandDamping = preferences[Keys.EXPAND_DAMPING] ?: 0.74f,
        expandStiffness = preferences[Keys.EXPAND_STIFFNESS] ?: 380f,
        appearDamping = preferences[Keys.APPEAR_DAMPING] ?: 0.6f,
        appearStiffness = preferences[Keys.APPEAR_STIFFNESS] ?: 280f,
        splitDamping = preferences[Keys.SPLIT_DAMPING] ?: 0.63f,
        splitStiffness = preferences[Keys.SPLIT_STIFFNESS] ?: 320f,
        featurePriorities = preferences[Keys.FEATURE_PRIORITIES]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && it != "TIMER" }
            ?.let { list ->
                val set = list.toSet()
                list + DEFAULT_FEATURE_PRIORITIES.filter { it !in set }
            } ?: DEFAULT_FEATURE_PRIORITIES,
        mediaControlAlignment = preferences[Keys.MEDIA_CONTROL_ALIGNMENT] ?: "CENTER",
        mediaLayersOrder = preferences[Keys.MEDIA_LAYERS_ORDER]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.let { list ->
                val set = list.toSet()
                list.filter { it in DEFAULT_MEDIA_LAYERS } + DEFAULT_MEDIA_LAYERS.filter { it !in set }
            } ?: DEFAULT_MEDIA_LAYERS,
        droidbarEnabled = preferences[Keys.DROIDBAR_ENABLED] ?: false,
        droidbarShowFlashlight = preferences[Keys.DROIDBAR_SHOW_FLASHLIGHT] ?: true,
        droidbarShowRingMode = preferences[Keys.DROIDBAR_SHOW_RING_MODE] ?: true,
        droidbarShowScreenshot = preferences[Keys.DROIDBAR_SHOW_SCREENSHOT] ?: true,
        droidbarShowWifi = preferences[Keys.DROIDBAR_SHOW_WIFI] ?: true,
        droidbarShowMobileData = preferences[Keys.DROIDBAR_SHOW_MOBILE_DATA] ?: true,
        droidbarUseMostUsedApps = preferences[Keys.DROIDBAR_USE_MOST_USED_APPS] ?: true,
        droidbarCustomApps = preferences[Keys.DROIDBAR_CUSTOM_APPS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList(),
        droidbarBackgroundOpacity = preferences[Keys.DROIDBAR_BACKGROUND_OPACITY] ?: 1.0f,
        droidbarMaxHeightDp = preferences[Keys.DROIDBAR_MAX_HEIGHT_DP] ?: 600,
        droidbarYOffsetDp = preferences[Keys.DROIDBAR_Y_OFFSET_DP] ?: 0,
        droidbarHapticsEnabled = preferences[Keys.DROIDBAR_HAPTICS_ENABLED] ?: true,
        coverPlacement = cover,
        innerPlacement = inner,
        placementProfilesInitialized = initialized,
        placementRolesSeen = preferences[Keys.PLACEMENT_ROLES_SEEN]
            ?.split(",")
            ?.mapNotNull { enumOrNull<DisplayRole>(it.trim()) }
            ?.toSet()
            ?: emptySet(),
        seenFoldingFeature = preferences[Keys.SEEN_FOLDING_FEATURE] ?: false,
        lastActiveRole = enumOrNull<DisplayRole>(preferences[Keys.LAST_ACTIVE_ROLE]) ?: DisplayRole.SINGLE,
        weatherLocationMode = enumOrNull<WeatherLocationMode>(preferences[Keys.WEATHER_LOCATION_MODE])
            ?: WeatherLocationMode.MANUAL,
        weatherUnits = enumOrNull<WeatherUnits>(preferences[Keys.WEATHER_UNITS]) ?: WeatherUnits.SYSTEM,
        weatherLocationDenied = preferences[Keys.WEATHER_LOCATION_DENIED] ?: false,
        cachedWeather = preferences[Keys.WEATHER_CACHE] ?: ""
    )
}

fun writeIslandSettings(preferences: MutablePreferences, settings: IslandSettings) {
    val mirror = when (settings.lastActiveRole) {
        DisplayRole.INNER -> settings.innerPlacement
        else -> settings.coverPlacement
    }.clamped()
    preferences[Keys.IS_ENABLED] = settings.isEnabled
    preferences[Keys.WIDTH_DP] = settings.widthDp
    preferences[Keys.HEIGHT_DP] = settings.heightDp
    preferences[Keys.CORNER_RADIUS_DP] = settings.cornerRadiusDp
    preferences[Keys.TOP_OFFSET_DP] = mirror.topOffsetDp
    preferences[Keys.HORIZONTAL_OFFSET_DP] = mirror.horizontalOffsetDp
    preferences[Keys.IS_LEFT_CUTOUT] = mirror.isLeftCutout
    preferences[Keys.IS_TEARDROP_CUTOUT] = mirror.isTeardropCutout
    preferences[Keys.SHOW_OUTLINE] = settings.showOutline
    preferences[Keys.OUTLINE_COLOR_ARGB] = settings.outlineColorArgb
    preferences[Keys.OUTLINE_THICKNESS_DP] = settings.outlineThicknessDp
    preferences[Keys.EXPANDED_SCALE] = settings.expandedScale
    preferences[Keys.HAPTICS_ENABLED] = settings.hapticsEnabled
    preferences[Keys.SHOW_CALLS] = settings.showCalls
    preferences[Keys.SHOW_MEDIA] = settings.showMedia
    preferences[Keys.SHOW_NOTIFICATIONS] = settings.showNotifications
    preferences[Keys.SHOW_LIVE_PROGRESS] = settings.showLiveProgress
    preferences[Keys.SHOW_SCREEN_RECORDING] = settings.showScreenRecording
    preferences[Keys.SHOW_FLASHLIGHT] = settings.showFlashlight
    preferences[Keys.SHOW_BLUETOOTH] = settings.showBluetooth
    preferences[Keys.SHOW_CHARGING] = settings.showCharging
    preferences[Keys.SHOW_RINGER_MODE] = settings.showRingerMode
    preferences[Keys.SHOW_AIRPLANE_MODE] = settings.showAirplaneMode
    preferences[Keys.SHOW_IN_LANDSCAPE] = settings.showInLandscape
    preferences[Keys.ANIMATION_SPEED_MULTIPLIER] = settings.animationSpeed
    preferences.remove(Keys.LEGACY_ANIMATION_SPEED)
    preferences[Keys.BLOCKED_PACKAGES] = settings.blockedPackages
    preferences[Keys.RESTART_ON_BOOT] = settings.restartOnBoot
    preferences[Keys.BUBBLE_EFFECT_ENABLED] = settings.bubbleEffectEnabled
    preferences[Keys.DISMISS_MEDIA_ON_PAUSE] = settings.dismissMediaOnPause
    preferences[Keys.ALWAYS_SHOW_PILL] = settings.alwaysShowPill
    preferences[Keys.SHOW_DUAL_ACTIVITY_IN_COMPACT] = settings.showDualActivityInCompact
    preferences[Keys.SHOW_FPS] = settings.showFps
    preferences[Keys.SHOW_WEATHER] = settings.showWeather
    preferences[Keys.SHOW_NETWORK_SPEED] = settings.showNetworkSpeed
    preferences[Keys.SHOW_ALARM] = settings.showAlarm
    preferences[Keys.AUTO_DISMISS_NOTIFICATIONS] = settings.autoDismissNotifications
    preferences[Keys.NOTIFICATION_AUTO_DISMISS_SECONDS] = settings.notificationAutoDismissSeconds
    preferences[Keys.PERSISTENT_CHARGING] = settings.persistentCharging
    preferences[Keys.NOTIFICATION_MAX_LINES] = settings.notificationMaxLines
    preferences[Keys.WEATHER_CITY] = settings.weatherCity
    preferences[Keys.FONT_KEY] = settings.fontKey
    preferences[Keys.SWAP_ACTIVITIES_ON_SWIPE] = settings.swapActivitiesOnSwipe
    preferences[Keys.BLURRED_ALBUM_ART_BACKGROUND] = settings.blurredAlbumArtBackground
    preferences[Keys.DESIGN_LANGUAGE] = settings.designLanguage.name
    preferences[Keys.GLASS_ENABLED] = settings.designLanguage == DesignLanguage.GLASS
    preferences[Keys.GLASS_OPACITY] = settings.glassOpacity
    preferences[Keys.MEDIA_WAVE_ANIMATION] = settings.mediaWaveAnimation
    preferences[Keys.SPINNING_ALBUM_ART] = settings.spinningAlbumArt
    preferences[Keys.WAVY_PROGRESS_BAR] = settings.wavyProgressBar
    preferences[Keys.HIDE_ON_LOCK_SCREEN] = settings.hideOnLockScreen
    preferences[Keys.EXPAND_DAMPING] = settings.expandDamping
    preferences[Keys.EXPAND_STIFFNESS] = settings.expandStiffness
    preferences[Keys.APPEAR_DAMPING] = settings.appearDamping
    preferences[Keys.APPEAR_STIFFNESS] = settings.appearStiffness
    preferences[Keys.SPLIT_DAMPING] = settings.splitDamping
    preferences[Keys.SPLIT_STIFFNESS] = settings.splitStiffness
    preferences[Keys.FEATURE_PRIORITIES] = settings.featurePriorities.joinToString(",")
    preferences[Keys.MEDIA_CONTROL_ALIGNMENT] = settings.mediaControlAlignment
    preferences[Keys.MEDIA_LAYERS_ORDER] = settings.mediaLayersOrder.joinToString(",")
    preferences[Keys.DROIDBAR_ENABLED] = settings.droidbarEnabled
    preferences[Keys.DROIDBAR_SHOW_FLASHLIGHT] = settings.droidbarShowFlashlight
    preferences[Keys.DROIDBAR_SHOW_RING_MODE] = settings.droidbarShowRingMode
    preferences[Keys.DROIDBAR_SHOW_SCREENSHOT] = settings.droidbarShowScreenshot
    preferences[Keys.DROIDBAR_SHOW_WIFI] = settings.droidbarShowWifi
    preferences[Keys.DROIDBAR_SHOW_MOBILE_DATA] = settings.droidbarShowMobileData
    preferences[Keys.DROIDBAR_USE_MOST_USED_APPS] = settings.droidbarUseMostUsedApps
    preferences[Keys.DROIDBAR_CUSTOM_APPS] = settings.droidbarCustomApps.joinToString(",")
    preferences[Keys.DROIDBAR_BACKGROUND_OPACITY] = settings.droidbarBackgroundOpacity
    preferences[Keys.DROIDBAR_MAX_HEIGHT_DP] = settings.droidbarMaxHeightDp
    preferences[Keys.DROIDBAR_Y_OFFSET_DP] = settings.droidbarYOffsetDp
    preferences[Keys.DROIDBAR_HAPTICS_ENABLED] = settings.droidbarHapticsEnabled
    preferences[Keys.PLACEMENT_PROFILES_INITIALIZED] = settings.placementProfilesInitialized
    preferences[Keys.PLACEMENT_ROLES_SEEN] = settings.placementRolesSeen.joinToString(",") { it.name }
    preferences[Keys.SEEN_FOLDING_FEATURE] = settings.seenFoldingFeature
    preferences[Keys.LAST_ACTIVE_ROLE] = settings.lastActiveRole.name
    preferences[Keys.WEATHER_LOCATION_MODE] = settings.weatherLocationMode.name
    preferences[Keys.WEATHER_UNITS] = settings.weatherUnits.name
    preferences[Keys.WEATHER_LOCATION_DENIED] = settings.weatherLocationDenied
    preferences[Keys.WEATHER_CACHE] = settings.cachedWeather
    writePlacement(preferences, "cover", settings.coverPlacement)
    writePlacement(preferences, "inner", settings.innerPlacement)
}

private fun readPlacement(preferences: Preferences, prefix: String, fallback: BubblePlacement): BubblePlacement {
    return BubblePlacement(
        topOffsetDp = preferences[intPreferencesKey("${prefix}_top_offset_dp")] ?: fallback.topOffsetDp,
        horizontalOffsetDp = preferences[intPreferencesKey("${prefix}_horizontal_offset_dp")] ?: fallback.horizontalOffsetDp,
        isLeftCutout = preferences[booleanPreferencesKey("${prefix}_is_left_cutout")] ?: fallback.isLeftCutout,
        isTeardropCutout = preferences[booleanPreferencesKey("${prefix}_is_teardrop_cutout")] ?: fallback.isTeardropCutout
    ).clamped()
}

private fun writePlacement(preferences: MutablePreferences, prefix: String, placement: BubblePlacement) {
    val clamped = placement.clamped()
    preferences[intPreferencesKey("${prefix}_top_offset_dp")] = clamped.topOffsetDp
    preferences[intPreferencesKey("${prefix}_horizontal_offset_dp")] = clamped.horizontalOffsetDp
    preferences[booleanPreferencesKey("${prefix}_is_left_cutout")] = clamped.isLeftCutout
    preferences[booleanPreferencesKey("${prefix}_is_teardrop_cutout")] = clamped.isTeardropCutout
}

private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? {
    if (raw.isNullOrBlank()) return null
    return runCatching { enumValueOf<T>(raw) }.getOrNull()
}
