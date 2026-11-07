package com.droidbubble.app.fold

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

fun collectDisplaySnapshots(
    context: Context,
    foldingFeatureDisplayId: Int?
): List<DisplaySnapshot> {
    val manager = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
    return manager.displays.map { display ->
        val configuration = runCatching {
            context.createDisplayContext(display).resources.configuration
        }.getOrElse { context.resources.configuration }
        val mode = display.mode
        DisplaySnapshot(
            displayId = display.displayId,
            stateOn = display.state == Display.STATE_ON,
            smallestWidthDp = configuration.smallestScreenWidthDp,
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            hasFoldingFeature = foldingFeatureDisplayId != null && display.displayId == foldingFeatureDisplayId
        )
    }
}

fun deviceReportsHinge(context: Context): Boolean {
    return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
}
