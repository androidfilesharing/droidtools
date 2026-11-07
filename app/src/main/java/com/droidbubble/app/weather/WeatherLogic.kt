package com.droidbubble.app.weather

import com.droidbubble.app.model.WeatherInfo
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import kotlin.math.roundToInt

enum class WeatherLocationMode { MANUAL, CURRENT }

enum class WeatherUnits { SYSTEM, METRIC, IMPERIAL }

enum class TempUnit { CELSIUS, FAHRENHEIT }

enum class WeatherSource { MANUAL_CITY, CURRENT_LOCATION, TIMEZONE_ESTIMATE }

enum class WeatherStatus { OK, STALE, OFFLINE, DENIED, UNAVAILABLE }

enum class LocationRequest { NONE, USE_CACHED, FETCH_FRESH }

data class WeatherSnapshot(
    val temperatureC: Int,
    val condition: String,
    val placeLabel: String,
    val highC: Int,
    val lowC: Int,
    val humidityPct: Int,
    val windKmh: Int,
    val fetchedAtEpochMs: Long,
    val source: WeatherSource,
    val status: WeatherStatus,
    val detail: String = "",
    val hasReading: Boolean = true
)

data class ZonePlace(
    val latitude: Double,
    val longitude: Double,
    val name: String
)

data class GeoHit(
    val latitude: Double,
    val longitude: Double,
    val placeLabel: String
)

const val WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1000L
const val LOCATION_FIX_MAX_AGE_MS = 30L * 60L * 1000L
const val WEATHER_STALE_AFTER_MS = 2L * 60L * 60L * 1000L

private val FAHRENHEIT_REGIONS = setOf("US", "BS", "KY", "LR", "PW", "FM", "MH")
private val MPH_REGIONS = setOf("US", "GB", "LR")

fun resolveTempUnit(units: WeatherUnits, region: String): TempUnit {
    return when (units) {
        WeatherUnits.METRIC -> TempUnit.CELSIUS
        WeatherUnits.IMPERIAL -> TempUnit.FAHRENHEIT
        WeatherUnits.SYSTEM -> if (region.uppercase() in FAHRENHEIT_REGIONS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
    }
}

fun celsiusToFahrenheit(celsius: Int): Int {
    return BigDecimal.valueOf(celsius.toLong())
        .multiply(BigDecimal(9))
        .divide(BigDecimal(5), 8, RoundingMode.HALF_UP)
        .add(BigDecimal(32))
        .setScale(0, RoundingMode.HALF_UP)
        .toInt()
}

fun formatTemperature(celsius: Int, unit: TempUnit): String {
    return when (unit) {
        TempUnit.CELSIUS -> "${celsius}°C"
        TempUnit.FAHRENHEIT -> "${celsiusToFahrenheit(celsius)}°F"
    }
}

fun formatWindKmh(kmh: Int, units: WeatherUnits, region: String): Int {
    val useMph = when (units) {
        WeatherUnits.METRIC -> false
        WeatherUnits.IMPERIAL -> true
        WeatherUnits.SYSTEM -> region.uppercase() in MPH_REGIONS
    }
    if (!useMph) return kmh
    return BigDecimal(kmh).divide(BigDecimal("1.609344"), 0, RoundingMode.HALF_UP).toInt()
}

fun ageStatus(snapshot: WeatherSnapshot, nowMs: Long): WeatherSnapshot {
    if (snapshot.status == WeatherStatus.OK && nowMs - snapshot.fetchedAtEpochMs >= WEATHER_STALE_AFTER_MS) {
        return snapshot.copy(status = WeatherStatus.STALE)
    }
    return snapshot
}

fun foldOffline(previous: WeatherSnapshot?): WeatherSnapshot? {
    return previous?.copy(status = WeatherStatus.OFFLINE)
}

fun foldUnavailable(previous: WeatherSnapshot?, detail: String): WeatherSnapshot {
    if (previous == null || !previous.hasReading) {
        return WeatherSnapshot(
            temperatureC = 0,
            condition = "",
            placeLabel = "",
            highC = 0,
            lowC = 0,
            humidityPct = 0,
            windKmh = 0,
            fetchedAtEpochMs = 0,
            source = WeatherSource.MANUAL_CITY,
            status = WeatherStatus.UNAVAILABLE,
            detail = detail,
            hasReading = false
        )
    }
    return previous.copy(status = WeatherStatus.UNAVAILABLE, detail = detail)
}

fun zonePlace(timeZoneId: String): ZonePlace? {
    val lower = timeZoneId.lowercase()
    return when {
        lower.contains("athens") || lower.contains("greece") -> ZonePlace(37.98, 23.72, "Athens")
        lower.contains("london") -> ZonePlace(51.50, -0.12, "London")
        lower.contains("paris") -> ZonePlace(48.85, 2.35, "Paris")
        lower.contains("berlin") -> ZonePlace(52.52, 13.40, "Berlin")
        lower.contains("new_york") -> ZonePlace(40.71, -74.00, "New York")
        lower.contains("los_angeles") -> ZonePlace(34.05, -118.24, "Los Angeles")
        lower.contains("chicago") -> ZonePlace(41.87, -87.62, "Chicago")
        lower.contains("tokyo") -> ZonePlace(35.67, 139.65, "Tokyo")
        lower.contains("sydney") -> ZonePlace(-33.86, 151.20, "Sydney")
        lower.contains("rome") -> ZonePlace(41.90, 12.49, "Rome")
        lower.contains("madrid") -> ZonePlace(40.41, -3.70, "Madrid")
        lower.contains("toronto") -> ZonePlace(43.65, -79.38, "Toronto")
        lower.contains("europe") -> ZonePlace(48.85, 2.35, "Central Europe")
        lower.contains("america") -> ZonePlace(40.71, -74.00, "Eastern Time")
        lower.contains("asia") -> ZonePlace(35.67, 139.65, "Tokyo")
        else -> null
    }
}

fun forecastUrl(latitude: Double, longitude: Double): String {
    return "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
        "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
        "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
}

fun geocodeUrl(city: String): String {
    val encoded = URLEncoder.encode(city.trim(), "UTF-8")
    return "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
}

fun parseGeocode(json: String): GeoHit? {
    if (!json.contains("\"latitude\"")) return null
    val latitude = jsonNumber(json, "latitude") ?: return null
    val longitude = jsonNumber(json, "longitude") ?: return null
    val name = jsonString(json, "name") ?: return null
    val country = jsonString(json, "country_code").orEmpty()
    val place = if (country.isBlank()) name else "$name, ${country.uppercase()}"
    return GeoHit(latitude, longitude, place)
}

fun parseForecast(
    json: String,
    placeLabel: String,
    source: WeatherSource,
    nowMs: Long
): WeatherSnapshot {
    val tempC = jsonNumber(json, "temperature_2m")?.roundToInt() ?: 0
    val humidity = jsonNumber(json, "relative_humidity_2m")?.roundToInt() ?: 0
    val wind = jsonNumber(json, "wind_speed_10m")?.roundToInt() ?: 0
    val code = jsonNumber(json, "weather_code")?.roundToInt() ?: 0
    val high = jsonNumber(json, "temperature_2m_max")?.roundToInt() ?: tempC
    val low = jsonNumber(json, "temperature_2m_min")?.roundToInt() ?: tempC
    return WeatherSnapshot(
        temperatureC = tempC,
        condition = weatherCondition(code),
        placeLabel = placeLabel,
        highC = high,
        lowC = low,
        humidityPct = humidity,
        windKmh = wind,
        fetchedAtEpochMs = nowMs,
        source = source,
        status = WeatherStatus.OK,
        hasReading = true
    )
}

fun locationRequestFor(
    mode: WeatherLocationMode,
    permissionGranted: Boolean,
    cachedFixAgeMs: Long?
): LocationRequest {
    if (mode != WeatherLocationMode.CURRENT || !permissionGranted) return LocationRequest.NONE
    if (cachedFixAgeMs != null && cachedFixAgeMs in 0 until LOCATION_FIX_MAX_AGE_MS) {
        return LocationRequest.USE_CACHED
    }
    return LocationRequest.FETCH_FRESH
}

fun weatherStatusLine(
    mode: WeatherLocationMode,
    units: WeatherUnits,
    region: String,
    snapshot: WeatherSnapshot?,
    denied: Boolean,
    nowMs: Long
): String {
    val unit = if (resolveTempUnit(units, region) == TempUnit.FAHRENHEIT) "°F" else "°C"
    val sourceLabel = when (snapshot?.source) {
        WeatherSource.CURRENT_LOCATION -> "Current location"
        WeatherSource.TIMEZONE_ESTIMATE -> "Time zone estimate"
        WeatherSource.MANUAL_CITY -> "Manual city"
        null -> if (mode == WeatherLocationMode.CURRENT) "Current location" else "Manual city"
    }
    if (denied || snapshot?.status == WeatherStatus.DENIED) {
        return "$sourceLabel · $unit · Location off"
    }
    if (snapshot == null || !snapshot.hasReading) {
        val detail = snapshot?.detail?.ifBlank { "No weather yet" } ?: "No weather yet"
        return "$sourceLabel · $unit · $detail"
    }
    val aged = ageStatus(snapshot, nowMs)
    val age = formatAge(nowMs - snapshot.fetchedAtEpochMs)
    val status = when (aged.status) {
        WeatherStatus.OFFLINE -> "Offline, last updated $age"
        WeatherStatus.UNAVAILABLE -> aged.detail.ifBlank { "Unavailable" }
        WeatherStatus.DENIED -> "Location off"
        WeatherStatus.OK, WeatherStatus.STALE -> "Updated $age"
    }
    return "$sourceLabel · $unit · $status"
}

fun encodeSnapshot(snapshot: WeatherSnapshot): String {
    return listOf(
        "1",
        snapshot.temperatureC.toString(),
        escape(snapshot.condition),
        escape(snapshot.placeLabel),
        snapshot.highC.toString(),
        snapshot.lowC.toString(),
        snapshot.humidityPct.toString(),
        snapshot.windKmh.toString(),
        snapshot.fetchedAtEpochMs.toString(),
        snapshot.source.name,
        snapshot.status.name,
        escape(snapshot.detail),
        snapshot.hasReading.toString()
    ).joinToString("\u001f")
}

fun decodeSnapshot(raw: String): WeatherSnapshot? {
    val parts = raw.split("\u001f")
    if (parts.size != 13 || parts[0] != "1") return null
    return runCatching {
        WeatherSnapshot(
            temperatureC = parts[1].toInt(),
            condition = unescape(parts[2]),
            placeLabel = unescape(parts[3]),
            highC = parts[4].toInt(),
            lowC = parts[5].toInt(),
            humidityPct = parts[6].toInt(),
            windKmh = parts[7].toInt(),
            fetchedAtEpochMs = parts[8].toLong(),
            source = WeatherSource.valueOf(parts[9]),
            status = WeatherStatus.valueOf(parts[10]),
            detail = unescape(parts[11]),
            hasReading = parts[12].toBoolean()
        )
    }.getOrNull()
}

fun WeatherSnapshot.toWeatherInfo(unit: TempUnit): WeatherInfo {
    return WeatherInfo(
        temperatureC = temperatureC,
        condition = condition,
        highC = highC,
        lowC = lowC,
        humidityPct = humidityPct,
        windKmh = windKmh,
        uvIndex = 0,
        cityName = placeLabel,
        hourlyForecast = emptyList(),
        hasReading = hasReading,
        temperatureLabel = if (hasReading) formatTemperature(temperatureC, unit) else "—",
        statusName = status.name,
        sourceName = source.name,
        detail = detail,
        fetchedAtEpochMs = fetchedAtEpochMs
    )
}

fun weatherCondition(code: Int): String {
    return when (code) {
        0 -> "Clear"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rainy"
        71, 73, 75 -> "Snowy"
        80, 81, 82 -> "Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Partly Cloudy"
    }
}

private fun formatAge(deltaMs: Long): String {
    val minutes = (deltaMs.coerceAtLeast(0L) / 60_000L).toInt()
    if (minutes < 1) return "just now"
    if (minutes < 60) return "${minutes}m ago"
    return "${minutes / 60}h ago"
}

private fun jsonNumber(json: String, key: String): Double? {
    val match = Regex(""""$key"\s*:\s*\[?\s*(-?\d+(?:\.\d+)?)""").find(json) ?: return null
    return match.groupValues[1].toDoubleOrNull()
}

private fun jsonString(json: String, key: String): String? {
    val match = Regex(""""$key"\s*:\s*"([^"\\]*)"""").find(json) ?: return null
    return match.groupValues[1]
}

private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\u001f", "\\u001f")

private fun unescape(value: String): String = value.replace("\\u001f", "\u001f").replace("\\\\", "\\")
