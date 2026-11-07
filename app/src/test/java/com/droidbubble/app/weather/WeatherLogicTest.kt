package com.droidbubble.app.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class WeatherLogicTest {

    @Test
    fun systemUnitsFollowRegionAndExplicitModesOverride() {
        assertEquals(TempUnit.FAHRENHEIT, resolveTempUnit(WeatherUnits.SYSTEM, "US"))
        assertEquals(TempUnit.CELSIUS, resolveTempUnit(WeatherUnits.SYSTEM, "DE"))
        assertEquals(TempUnit.CELSIUS, resolveTempUnit(WeatherUnits.METRIC, "US"))
        assertEquals(TempUnit.FAHRENHEIT, resolveTempUnit(WeatherUnits.IMPERIAL, "DE"))
    }

    @Test
    fun fahrenheitConversionUsesHalfUp() {
        assertEquals(32, celsiusToFahrenheit(0))
        assertEquals(212, celsiusToFahrenheit(100))
    }

    @Test
    fun temperatureLabelIncludesTheUnit() {
        assertEquals("22°C", formatTemperature(22, TempUnit.CELSIUS))
        assertEquals("32°F", formatTemperature(0, TempUnit.FAHRENHEIT))
    }

    @Test
    fun windUsesMphOnlyWhereTheRegionDoes() {
        val expectedMph = BigDecimal(16).divide(BigDecimal("1.609344"), 0, RoundingMode.HALF_UP).toInt()
        assertEquals(expectedMph, formatWindKmh(16, WeatherUnits.SYSTEM, "GB"))
        assertEquals(16, formatWindKmh(16, WeatherUnits.SYSTEM, "DE"))
    }

    @Test
    fun oldReadingBecomesStaleWithoutChangingTemperature() {
        val snapshot = sample(fetchedAtEpochMs = 0)
        val aged = ageStatus(snapshot, nowMs = WEATHER_STALE_AFTER_MS + 60_000)
        assertEquals(WeatherStatus.STALE, aged.status)
        assertEquals(snapshot.temperatureC, aged.temperatureC)
    }

    @Test
    fun offlineKeepsTheLastReadingAndNullStaysEmpty() {
        val kept = foldOffline(sample(temperatureC = 18))
        assertEquals(18, kept!!.temperatureC)
        assertEquals(WeatherStatus.OFFLINE, kept.status)
        assertNull(foldOffline(null))
    }

    @Test
    fun unknownZoneIsNotAthens() {
        assertNull(zonePlace("Not/AZone"))
        assertEquals("Athens", zonePlace("Europe/Athens")!!.name)
        assertEquals("New York", zonePlace("America/New_York")!!.name)
    }

    @Test
    fun forecastUrlStaysCelsiusAndGeocodeDoesNotSendCoordinates() {
        val forecast = forecastUrl(40.0, -74.0)
        assertTrue(forecast.contains("latitude=40.0"))
        assertFalse(forecast.contains("temperature_unit"))
        val geocode = geocodeUrl("New York")
        assertTrue(geocode.contains("New+York") || geocode.contains("New%20York"))
        assertFalse(geocode.contains("latitude="))
    }

    @Test
    fun emptyGeocodeDoesNotInventAPlace() {
        assertNull(parseGeocode("""{"results":[]}"""))
    }

    @Test
    fun forecastParseDoesNotInventHourlyOrUv() {
        val json = """
            {"current":{"temperature_2m":21.6,"relative_humidity_2m":54,"weather_code":1,"wind_speed_10m":12.4},
             "daily":{"temperature_2m_max":[25.2],"temperature_2m_min":[16.1]}}
        """.trimIndent()
        val parsed = parseForecast(json, "Paris", WeatherSource.MANUAL_CITY, nowMs = 1_000)
        assertEquals(22, parsed.temperatureC)
        assertEquals("Mainly Clear", parsed.condition)
        assertEquals(25, parsed.highC)
        assertEquals(16, parsed.lowC)
        assertEquals(12, parsed.windKmh)
        assertTrue(parsed.hasReading)
        assertFalse(encodeSnapshot(parsed).contains("hourly"))
        assertFalse(encodeSnapshot(parsed).contains("\"uv"))
    }

    @Test
    fun locationIsNotRequestedWhenDeniedOrFresh() {
        assertEquals(
            LocationRequest.NONE,
            locationRequestFor(WeatherLocationMode.CURRENT, permissionGranted = false, cachedFixAgeMs = null)
        )
        assertEquals(
            LocationRequest.NONE,
            locationRequestFor(WeatherLocationMode.MANUAL, permissionGranted = true, cachedFixAgeMs = null)
        )
        assertEquals(
            LocationRequest.USE_CACHED,
            locationRequestFor(WeatherLocationMode.CURRENT, permissionGranted = true, cachedFixAgeMs = 60_000)
        )
        assertEquals(
            LocationRequest.FETCH_FRESH,
            locationRequestFor(
                WeatherLocationMode.CURRENT,
                permissionGranted = true,
                cachedFixAgeMs = LOCATION_FIX_MAX_AGE_MS
            )
        )
    }

    @Test
    fun statusLineNamesDenialOfflineAndTimeZone() {
        val denied = weatherStatusLine(
            mode = WeatherLocationMode.MANUAL,
            units = WeatherUnits.METRIC,
            region = "DE",
            snapshot = null,
            denied = true,
            nowMs = 0
        )
        assertTrue(denied.contains("Location off"))
        val offline = weatherStatusLine(
            mode = WeatherLocationMode.MANUAL,
            units = WeatherUnits.METRIC,
            region = "DE",
            snapshot = sample(status = WeatherStatus.OFFLINE, source = WeatherSource.MANUAL_CITY),
            denied = false,
            nowMs = sample().fetchedAtEpochMs + 3 * 60 * 60 * 1000
        )
        assertTrue(offline.contains("Offline"))
        val zone = weatherStatusLine(
            mode = WeatherLocationMode.MANUAL,
            units = WeatherUnits.SYSTEM,
            region = "DE",
            snapshot = sample(source = WeatherSource.TIMEZONE_ESTIMATE),
            denied = false,
            nowMs = sample().fetchedAtEpochMs + 60_000
        )
        assertTrue(zone.contains("Time zone estimate"))
    }

    @Test
    fun snapshotRoundTrips() {
        val original = sample(detail = "City not found", hasReading = false, status = WeatherStatus.UNAVAILABLE)
        assertEquals(original, decodeSnapshot(encodeSnapshot(original)))
    }

    private fun sample(
        temperatureC: Int = 22,
        fetchedAtEpochMs: Long = 5_000,
        status: WeatherStatus = WeatherStatus.OK,
        source: WeatherSource = WeatherSource.MANUAL_CITY,
        detail: String = "",
        hasReading: Boolean = true
    ) = WeatherSnapshot(
        temperatureC = temperatureC,
        condition = "Clear",
        placeLabel = "Paris",
        highC = 25,
        lowC = 16,
        humidityPct = 40,
        windKmh = 10,
        fetchedAtEpochMs = fetchedAtEpochMs,
        source = source,
        status = status,
        detail = detail,
        hasReading = hasReading
    )
}
