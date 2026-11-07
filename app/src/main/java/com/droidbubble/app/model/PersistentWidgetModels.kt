package com.droidbubble.app.model

data class FpsInfo(
    val fps: Int = 60,
    val refreshRateHz: Int = 120,
    val frameTimeMs: Float = 16.6f,
    val isSmooth: Boolean = true
)

data class HourlyWeather(
    val timeLabel: String,
    val tempC: Int,
    val condition: String
)

data class WeatherInfo(
    val temperatureC: Int = 0,
    val condition: String = "",
    val highC: Int = 0,
    val lowC: Int = 0,
    val humidityPct: Int = 0,
    val windKmh: Int = 0,
    val uvIndex: Int = 0,
    val cityName: String = "",
    val hourlyForecast: List<HourlyWeather> = emptyList(),
    val hasReading: Boolean = false,
    val temperatureLabel: String = "—",
    val statusName: String = "UNAVAILABLE",
    val sourceName: String = "",
    val detail: String = "",
    val fetchedAtEpochMs: Long = 0L
)

data class NetworkSpeedInfo(
    val downloadSpeedFormatted: String = "0 KB/s",
    val uploadSpeedFormatted: String = "0 KB/s",
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val isWifi: Boolean = true,
    val networkLabel: String = "Wi-Fi",
    val pingMs: Int = 18,
    val sessionTotalMb: Long = 0L
)
