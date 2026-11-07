package com.droidbubble.app.ui.main

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidbubble.app.BuildConfig
import com.droidbubble.app.data.SettingsRepository
import com.droidbubble.app.util.UpdatePolicy
import com.droidbubble.app.model.IslandSettings
import com.droidbubble.app.service.IslandOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isBlocked: Boolean = false
)

class MainScreenViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<IslandSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = IslandSettings()
        )

    private val _pendingPermissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val pendingPermissions: StateFlow<List<PermissionItem>> = _pendingPermissions.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data class Available(val release: com.droidbubble.app.util.AppReleaseInfo) : UpdateState
        data class Downloading(val release: com.droidbubble.app.util.AppReleaseInfo, val progress: Float) : UpdateState
        data class Error(val message: String) : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    fun dismissUpdateSheet() {
        _showUpdateSheet.value = false
    }

    fun openUpdateSheet() {
        if (_updateState.value is UpdateState.Available || _updateState.value is UpdateState.Downloading) {
            _showUpdateSheet.value = true
        }
    }

    fun checkForUpdates(context: Context) {
        if (!UpdatePolicy.shouldCheckUpstream(BuildConfig.UPSTREAM_UPDATE_CHECKS)) {
            _updateState.value = UpdateState.UpToDate
            return
        }
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            when (val result = com.droidbubble.app.util.UpdateManager.checkGithubUpdates(context)) {
                is com.droidbubble.app.util.UpdateCheckResult.UpdateAvailable -> {
                    _updateState.value = UpdateState.Available(result.release)
                    _showUpdateSheet.value = true
                }
                is com.droidbubble.app.util.UpdateCheckResult.UpToDate -> {
                    _updateState.value = UpdateState.UpToDate
                }
                is com.droidbubble.app.util.UpdateCheckResult.Error -> {
                    _updateState.value = UpdateState.Error(result.message)
                }
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context, release: com.droidbubble.app.util.AppReleaseInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(release, 0.01f)
            val apkFile = com.droidbubble.app.util.UpdateManager.downloadApk(context, release.downloadUrl) { p ->
                _updateState.value = UpdateState.Downloading(release, p)
            }
            if (apkFile != null) {
                com.droidbubble.app.util.UpdateManager.installApk(context, apkFile)
                _showUpdateSheet.value = false
                _updateState.value = UpdateState.UpToDate
            } else {
                _updateState.value = UpdateState.Error("Failed to download update APK")
            }
        }
    }

    fun refreshUpdateStateOnResume(context: Context) {
        val current = com.droidbubble.app.util.UpdateManager.getCurrentVersion(context)
        val state = _updateState.value
        if (state is UpdateState.Available) {
            if (!com.droidbubble.app.util.UpdateManager.isNewerVersion(state.release.version, current)) {
                _updateState.value = UpdateState.UpToDate
                _showUpdateSheet.value = false
            }
        }
    }

    fun checkPermissions(context: Context) {
        _pendingPermissions.value = PermissionHelper.getPendingPermissions(context)
    }

    fun updateSettings(update: (IslandSettings) -> IslandSettings) {
        viewModelScope.launch {
            repository.updateSettings(update)
        }
    }

    fun toggleEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(isEnabled = enabled) }
            if (enabled && PermissionHelper.isOverlayGranted(context)) {
                IslandOverlayService.start(context)
            } else {
                IslandOverlayService.stop(context)
            }
        }
    }

    fun loadInstalledApps(context: Context) {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val currentBlocked = settings.value.blockedPackages

                resolveInfos.mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg == context.packageName) return@mapNotNull null
                    val name = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    AppItem(
                        packageName = pkg,
                        appName = name,
                        icon = icon,
                        isBlocked = currentBlocked.contains(pkg)
                    )
                }.sortedBy { it.appName.lowercase() }
            }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleAppBlocked(packageName: String) {
        viewModelScope.launch {
            val currentBlocked = settings.value.blockedPackages.toMutableSet()
            if (currentBlocked.contains(packageName)) {
                currentBlocked.remove(packageName)
            } else {
                currentBlocked.add(packageName)
            }
            repository.updateSettings { it.copy(blockedPackages = currentBlocked) }
            _installedApps.value = _installedApps.value.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isBlocked = currentBlocked.contains(packageName))
                } else app
            }
        }
    }
}
