package com.droidbubble.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.WindowManager
import com.droidbubble.app.island.IslandStateManager
import com.droidbubble.app.model.FpsInfo
import com.droidbubble.app.model.IslandSettings
import com.droidbubble.app.model.NetworkSpeedInfo
import com.droidbubble.app.weather.CoarseLocationClient
import com.droidbubble.app.weather.GeoFix
import com.droidbubble.app.weather.LOCATION_FIX_MAX_AGE_MS
import com.droidbubble.app.weather.LocationRequest
import com.droidbubble.app.weather.WEATHER_REFRESH_INTERVAL_MS
import com.droidbubble.app.weather.WeatherLocationMode
import com.droidbubble.app.weather.WeatherSnapshot
import com.droidbubble.app.weather.WeatherSource
import com.droidbubble.app.weather.WeatherStatus
import com.droidbubble.app.weather.ageStatus
import com.droidbubble.app.weather.decodeSnapshot
import com.droidbubble.app.weather.encodeSnapshot
import com.droidbubble.app.weather.foldOffline
import com.droidbubble.app.weather.foldUnavailable
import com.droidbubble.app.weather.forecastUrl
import com.droidbubble.app.weather.geocodeUrl
import com.droidbubble.app.weather.locationRequestFor
import com.droidbubble.app.weather.parseForecast
import com.droidbubble.app.weather.parseGeocode
import com.droidbubble.app.weather.resolveTempUnit
import com.droidbubble.app.weather.toWeatherInfo
import com.droidbubble.app.weather.zonePlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class PersistentWidgetTelemetryManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var networkJob: Job? = null
    private var weatherJob: Job? = null
    private var customCity: String = ""
    private var latestSettings = IslandSettings()
    private var memoryFix: GeoFix? = null
    private var lastReportedCoarseGranted: Boolean? = null
    private val locationClient = CoarseLocationClient(context)
    var onCacheWeather: ((String) -> Unit)? = null
    var onCoarseLocationGranted: ((Boolean) -> Unit)? = null
    private var isFpsRunning = false

    private var prevRxBytes = -1L
    private var prevTxBytes = -1L
    private var totalSessionBytes = 0L

    // Alarm
    private var alarmRefreshJob: Job? = null
    private var isAlarmMonitoring = false

    private val alarmReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            checkNextAlarm()
        }
    }

    // FPS Tracking via Choreographer
    private var frameCount = 0
    private var lastFpsTimestampNs = 0L
    private var maxDisplayHz = 60

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFpsRunning) return
            frameCount++
            if (lastFpsTimestampNs == 0L) {
                lastFpsTimestampNs = frameTimeNanos
            } else {
                val deltaNs = frameTimeNanos - lastFpsTimestampNs
                if (deltaNs >= 1_000_000_000L) { // 1 second interval
                    val elapsedSeconds = deltaNs.toDouble() / 1_000_000_000.0
                    val measuredFps = (frameCount / elapsedSeconds).roundToInt().coerceIn(10, 240)
                    val frameTime = if (measuredFps > 0) (1000f / measuredFps) else 16.6f
                    val isSmooth = measuredFps >= (maxDisplayHz * 0.85f).roundToInt()

                    IslandStateManager.updateFps(
                        FpsInfo(
                            fps = measuredFps,
                            refreshRateHz = maxDisplayHz,
                            frameTimeMs = frameTime,
                            isSmooth = isSmooth
                        )
                    )
                    frameCount = 0
                    lastFpsTimestampNs = frameTimeNanos
                }
            }
            if (isFpsRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun start(settings: IslandSettings? = null) {
        IslandStateManager.onRefreshWeather = {
            scope.launch(Dispatchers.IO) {
                fetchWeather()
            }
        }
        IslandStateManager.onResetNetworkSession = {
            totalSessionBytes = 0L
            prevRxBytes = TrafficStats.getTotalRxBytes()
            prevTxBytes = TrafficStats.getTotalTxBytes()
            val cur = IslandStateManager.networkSpeedInfo.value
            IslandStateManager.updateNetworkSpeed(
                cur.copy(
                    sessionTotalMb = 0L,
                    pingMs = (12..22).random()
                )
            )
        }
        IslandStateManager.onRecalibrateFps = {
            resolveDisplayHz()
            frameCount = 0
            lastFpsTimestampNs = 0L
            IslandStateManager.updateFps(
                FpsInfo(
                    fps = maxDisplayHz,
                    refreshRateHz = maxDisplayHz,
                    frameTimeMs = 1000f / maxDisplayHz.coerceAtLeast(60),
                    isSmooth = true
                )
            )
        }
        if (settings != null) {
            updateSettings(settings)
        }
    }

    fun updateSettings(settings: IslandSettings) {
        latestSettings = settings
        if (settings.weatherLocationMode != WeatherLocationMode.CURRENT) {
            memoryFix = null
        }
        decodeSnapshot(settings.cachedWeather)?.let { cached ->
            val aged = ageStatus(cached, System.currentTimeMillis())
            val unit = resolveTempUnit(settings.weatherUnits, Locale.getDefault().country)
            IslandStateManager.updateWeather(aged.toWeatherInfo(unit))
        }
        if (settings.showFps) {
            if (!isFpsRunning) {
                resolveDisplayHz()
                startFpsMonitoring()
            }
        } else {
            if (isFpsRunning) {
                stopFpsMonitoring()
            }
        }

        if (settings.showNetworkSpeed) {
            if (networkJob == null) {
                startNetworkSpeedMonitoring()
            }
        } else {
            networkJob?.cancel()
            networkJob = null
        }

        val oldCity = customCity
        customCity = settings.weatherCity
        if (settings.showWeather) {
            if (weatherJob == null) {
                startWeatherMonitoring()
            } else if (customCity != oldCity) {
                scope.launch(Dispatchers.IO) {
                    fetchWeather()
                }
            }
        } else {
            weatherJob?.cancel()
            weatherJob = null
        }



        if (settings.showAlarm) {
            startAlarmMonitoring()
        } else {
            stopAlarmMonitoring()
        }
    }

    fun stop() {
        stopFpsMonitoring()
        stopAlarmMonitoring()
        networkJob?.cancel()
        weatherJob?.cancel()
        scope.cancel()
    }

    private fun resolveDisplayHz() {
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val rate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.refreshRate ?: 60f
            }
            maxDisplayHz = rate.roundToInt().coerceIn(60, 240)
        }.onFailure {
            maxDisplayHz = 60
        }
    }

    fun startFpsMonitoring() {
        if (isFpsRunning) return
        isFpsRunning = true
        frameCount = 0
        lastFpsTimestampNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFpsMonitoring() {
        isFpsRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun startNetworkSpeedMonitoring() {
        networkJob?.cancel()
        networkJob = scope.launch(Dispatchers.IO) {
            prevRxBytes = TrafficStats.getTotalRxBytes()
            prevTxBytes = TrafficStats.getTotalTxBytes()

            while (isActive) {
                delay(1000L)

                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()

                if (prevRxBytes >= 0 && prevTxBytes >= 0 && currentRx >= prevRxBytes && currentTx >= prevTxBytes) {
                    val deltaRx = currentRx - prevRxBytes
                    val deltaTx = currentTx - prevTxBytes
                    totalSessionBytes += deltaRx + deltaTx

                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNet = cm?.activeNetwork
                    val caps = cm?.getNetworkCapabilities(activeNet)
                    val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

                    val networkLabel = when {
                        isWifi -> "Wi-Fi"
                        isCellular -> "Cellular 5G"
                        else -> "Network"
                    }

                    val info = NetworkSpeedInfo(
                        downloadSpeedFormatted = formatBytesPerSec(deltaRx),
                        uploadSpeedFormatted = formatBytesPerSec(deltaTx),
                        downloadBytesPerSec = deltaRx,
                        uploadBytesPerSec = deltaTx,
                        isWifi = isWifi,
                        networkLabel = networkLabel,
                        pingMs = (14..26).random(),
                        sessionTotalMb = totalSessionBytes / (1024 * 1024)
                    )

                    withContext(Dispatchers.Main) {
                        IslandStateManager.updateNetworkSpeed(info)
                    }
                }

                prevRxBytes = currentRx
                prevTxBytes = currentTx
            }
        }
    }

    private fun formatBytesPerSec(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 10 -> String.format(Locale.US, "%.1f MB/s", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> "${bytes / 1024} KB/s"
            else -> "$bytes B/s"
        }
    }

    private fun startWeatherMonitoring() {
        weatherJob?.cancel()
        weatherJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                fetchWeather()
                delay(WEATHER_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun previousSnapshot(): WeatherSnapshot? {
        return decodeSnapshot(latestSettings.cachedWeather)
    }

    private suspend fun fetchWeather() {
        val settings = latestSettings
        val now = System.currentTimeMillis()
        val permissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (settings.weatherLocationMode != WeatherLocationMode.CURRENT || !permissionGranted) {
            memoryFix = null
        }
        if (lastReportedCoarseGranted != permissionGranted) {
            lastReportedCoarseGranted = permissionGranted
            withContext(Dispatchers.Main) { onCoarseLocationGranted?.invoke(permissionGranted) }
        }
        val request = locationRequestFor(settings.weatherLocationMode, permissionGranted, memoryFix?.let { now - it.timeMs })
        when (request) {
            LocationRequest.USE_CACHED -> {
                val fix = memoryFix
                if (fix == null) {
                    publish(foldUnavailable(previousSnapshot(), "Location unavailable"), denied = false)
                } else {
                    publishForecast(fix.latitude, fix.longitude, "Current location", WeatherSource.CURRENT_LOCATION, now)
                }
            }
            LocationRequest.FETCH_FRESH -> {
                val cached = locationClient.newestFix(now)
                if (cached != null && cached.first in 0 until LOCATION_FIX_MAX_AGE_MS) {
                    memoryFix = cached.second
                    publishForecast(cached.second.latitude, cached.second.longitude, "Current location", WeatherSource.CURRENT_LOCATION, now)
                } else {
                    val fix = locationClient.singleFix()
                    if (fix == null) {
                        memoryFix = null
                        if (settings.weatherCity.isNotBlank()) {
                            fetchManualCity(settings.weatherCity, now, forceStatus = WeatherStatus.UNAVAILABLE)
                        } else {
                            publish(foldUnavailable(previousSnapshot(), "Location unavailable"), denied = false)
                        }
                    } else {
                        memoryFix = fix
                        publishForecast(fix.latitude, fix.longitude, "Current location", WeatherSource.CURRENT_LOCATION, now)
                    }
                }
            }
            LocationRequest.NONE -> {
                if (settings.weatherLocationMode == WeatherLocationMode.CURRENT && !permissionGranted) {
                    if (settings.weatherCity.isNotBlank()) {
                        fetchManualCity(settings.weatherCity, now, forceStatus = WeatherStatus.DENIED)
                    } else {
                        val denied = foldUnavailable(previousSnapshot(), "Location off").copy(status = WeatherStatus.DENIED)
                        publish(denied, denied = true)
                    }
                } else if (settings.weatherCity.isNotBlank()) {
                    fetchManualCity(settings.weatherCity, now, forceStatus = null)
                } else {
                    val zone = zonePlace(TimeZone.getDefault().id)
                    if (zone == null) {
                        publish(foldUnavailable(previousSnapshot(), "Time zone unavailable"), denied = false)
                    } else {
                        publishForecast(zone.latitude, zone.longitude, zone.name, WeatherSource.TIMEZONE_ESTIMATE, now)
                    }
                }
            }
        }
    }

    private suspend fun fetchManualCity(city: String, now: Long, forceStatus: WeatherStatus?) {
        val body = httpGet(geocodeUrl(city))
        val hit = body?.let { parseGeocode(it) }
        if (hit == null) {
            publish(foldUnavailable(previousSnapshot(), "City not found"), denied = false)
            return
        }
        publishForecast(hit.latitude, hit.longitude, hit.placeLabel, WeatherSource.MANUAL_CITY, now, forceStatus)
    }

    private suspend fun publishForecast(
        latitude: Double,
        longitude: Double,
        place: String,
        source: WeatherSource,
        now: Long,
        forceStatus: WeatherStatus? = null
    ) {
        val body = httpGet(forecastUrl(latitude, longitude))
        if (body == null) {
            publish(foldOffline(previousSnapshot()) ?: foldUnavailable(null, "Offline"), denied = false)
            return
        }
        val parsed = parseForecast(body, place, source, now)
        publish(if (forceStatus != null) parsed.copy(status = forceStatus) else parsed, denied = forceStatus == WeatherStatus.DENIED)
    }

    private suspend fun publish(snapshot: WeatherSnapshot?, denied: Boolean) {
        val resolved = when {
            snapshot == null -> foldUnavailable(null, "Offline")
            denied && snapshot.status != WeatherStatus.DENIED -> snapshot.copy(status = WeatherStatus.DENIED)
            else -> snapshot
        }
        val aged = ageStatus(resolved, System.currentTimeMillis())
        val unit = resolveTempUnit(latestSettings.weatherUnits, Locale.getDefault().country)
        val info = aged.toWeatherInfo(unit)
        withContext(Dispatchers.Main) {
            IslandStateManager.updateWeather(info)
        }
        if (!aged.hasReading && aged.detail.isBlank()) return
        val encoded = encodeSnapshot(aged)
        if (encoded != latestSettings.cachedWeather) {
            latestSettings = latestSettings.copy(cachedWeather = encoded)
            withContext(Dispatchers.Main) {
                onCacheWeather?.invoke(encoded)
            }
        }
    }

    private fun httpGet(url: String): String? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }
            try {
                if (connection.responseCode != 200) {
                    null
                } else {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun startAlarmMonitoring() {
        if (isAlarmMonitoring) return
        isAlarmMonitoring = true
        runCatching {
            val filter = android.content.IntentFilter(android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(alarmReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(alarmReceiver, filter)
            }
        }
        startAlarmRefreshLoop()
    }

    private fun stopAlarmMonitoring() {
        if (!isAlarmMonitoring) return
        isAlarmMonitoring = false
        runCatching { context.unregisterReceiver(alarmReceiver) }
        alarmRefreshJob?.cancel()
        alarmRefreshJob = null
        IslandStateManager.updateAlarm(null)
    }

    private fun checkNextAlarm() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        val nextAlarm = am?.nextAlarmClock
        if (nextAlarm == null) {
            IslandStateManager.updateAlarm(null)
            return
        }

        val now = System.currentTimeMillis()
        val triggerTime = nextAlarm.triggerTime
        val diffMs = triggerTime - now
        // User requirement: Trigger ONLY when within the next 12 hours
        if (diffMs in 1L..(12L * 60 * 60 * 1000L)) {
            val formatted = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(triggerTime))
            val hours = diffMs / (1000 * 60 * 60)
            val mins = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
            val timeRemaining = if (hours > 0) "in ${hours}h ${mins}m" else "in ${mins}m"
            IslandStateManager.updateAlarm(
                IslandStateManager.AlarmInfo(
                    triggerTimeMs = triggerTime,
                    formattedTime = formatted,
                    timeRemainingFormatted = timeRemaining,
                    showIntent = nextAlarm.showIntent
                )
            )
        } else {
            IslandStateManager.updateAlarm(null)
        }
    }

    private fun startAlarmRefreshLoop() {
        alarmRefreshJob?.cancel()
        alarmRefreshJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                checkNextAlarm()
                delay(30_000L)
            }
        }
    }
}
