package com.droidbubble.app.fold

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

@Composable
fun rememberActiveDisplayRole(
    seenFoldingFeature: Boolean,
    onSeenFoldingFeature: () -> Unit = {}
): DisplayRole {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var folding by remember { mutableStateOf(false) }
    val activity = context as? Activity
    if (activity != null) {
        LaunchedEffect(activity) {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
                if (info.displayFeatures.any { it is FoldingFeature }) {
                    folding = true
                    onSeenFoldingFeature()
                }
            }
        }
    }
    val metrics = context.resources.displayMetrics
    val displayId = when {
        activity == null -> 0
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> activity.display?.displayId ?: 0
        else -> {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.displayId
        }
    }
    val snapshot = DisplaySnapshot(
        displayId = displayId,
        stateOn = true,
        smallestWidthDp = configuration.smallestScreenWidthDp,
        widthPx = metrics.widthPixels,
        heightPx = metrics.heightPixels,
        hasFoldingFeature = folding
    )
    val roles = assignRoles(
        deviceReportsHinge = deviceReportsHinge(context),
        seenFoldingFeature = seenFoldingFeature || folding,
        displays = listOf(snapshot)
    )
    return roles[displayId] ?: DisplayRole.SINGLE
}
