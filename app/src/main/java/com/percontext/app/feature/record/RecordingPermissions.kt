package com.percontext.app.feature.record

import android.Manifest
import android.os.Build

internal const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
internal const val LEGACY_WRITE_STORAGE_PERMISSION =
    "android.permission.WRITE_EXTERNAL_STORAGE"

internal fun requiredRecordingPermissions(
    sdkInt: Int,
    microphoneGranted: Boolean,
    notificationsGranted: Boolean,
    legacyStorageGranted: Boolean,
): List<String> = buildList {
    if (!microphoneGranted) add(Manifest.permission.RECORD_AUDIO)
    if (sdkInt <= Build.VERSION_CODES.P && !legacyStorageGranted) {
        add(LEGACY_WRITE_STORAGE_PERMISSION)
    }
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
        add(POST_NOTIFICATIONS_PERMISSION)
    }
}
