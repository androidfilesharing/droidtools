package com.droidbubble.app.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.droidbubble.app.fold.BubblePlacement
import com.droidbubble.app.fold.DisplayRole
import com.droidbubble.app.fold.withActivePlacement
import com.droidbubble.app.model.IslandSettings
import com.droidbubble.app.weather.WeatherLocationMode
import com.droidbubble.app.weather.WeatherSnapshot
import com.droidbubble.app.weather.WeatherSource
import com.droidbubble.app.weather.WeatherStatus
import com.droidbubble.app.weather.WeatherUnits
import com.droidbubble.app.weather.decodeSnapshot
import com.droidbubble.app.weather.encodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandSettingsPreferencesTest {

    @Test
    fun placementsAndWeatherRoundTrip() {
        val snapshot = WeatherSnapshot(
            temperatureC = 11,
            condition = "Clear",
            placeLabel = "Paris",
            highC = 14,
            lowC = 7,
            humidityPct = 40,
            windKmh = 9,
            fetchedAtEpochMs = 50,
            source = WeatherSource.CURRENT_LOCATION,
            status = WeatherStatus.OK
        )
        val settings = IslandSettings(
            widthDp = 200,
            coverPlacement = BubblePlacement(topOffsetDp = 20, horizontalOffsetDp = -8),
            innerPlacement = BubblePlacement(topOffsetDp = 40, horizontalOffsetDp = 6),
            placementProfilesInitialized = true,
            seenFoldingFeature = true,
            weatherLocationMode = WeatherLocationMode.CURRENT,
            weatherUnits = WeatherUnits.IMPERIAL,
            cachedWeather = encodeSnapshot(snapshot)
        )
        val preferences = emptyPreferences().toMutablePreferences()
        writeIslandSettings(preferences, settings)
        val read = readIslandSettings(preferences)
        assertEquals(20, read.coverPlacement.topOffsetDp)
        assertEquals(-8, read.coverPlacement.horizontalOffsetDp)
        assertEquals(40, read.innerPlacement.topOffsetDp)
        assertEquals(WeatherLocationMode.CURRENT, read.weatherLocationMode)
        assertEquals(WeatherUnits.IMPERIAL, read.weatherUnits)
        assertTrue(read.seenFoldingFeature)
        assertEquals(snapshot, decodeSnapshot(read.cachedWeather))
        val inner = read.withActivePlacement(DisplayRole.INNER)
        assertEquals(40, inner.topOffsetDp)
        assertEquals(6, inner.horizontalOffsetDp)
        assertEquals(200, inner.widthDp)
        assertEquals(20, read.withActivePlacement(DisplayRole.SINGLE).topOffsetDp)
    }

    @Test
    fun uninitializedReadCopiesLegacyOffsetsToBothRoles() {
        val preferences = emptyPreferences().toMutablePreferences()
        preferences[intPreferencesKey("top_offset_dp")] = 20
        preferences[intPreferencesKey("horizontal_offset_dp")] = -8
        val read = readIslandSettings(preferences)
        assertFalse(read.placementProfilesInitialized)
        assertEquals(20, read.coverPlacement.topOffsetDp)
        assertEquals(20, read.innerPlacement.topOffsetDp)
        assertEquals(-8, read.coverPlacement.horizontalOffsetDp)
        assertEquals(-8, read.innerPlacement.horizontalOffsetDp)
    }
}
