package com.percontext.app.feature.record

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPermissionPolicyTest {
    @Test
    fun android13RequestsBothMissingPermissions() {
        assertEquals(
            listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            requiredRecordingPermissions(
                sdkInt = 33,
                microphoneGranted = false,
                notificationsGranted = false,
                legacyStorageGranted = false,
            ),
        )
    }

    @Test
    fun android13StillRequestsNotificationsWhenMicrophoneIsGranted() {
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            requiredRecordingPermissions(
                sdkInt = 33,
                microphoneGranted = true,
                notificationsGranted = false,
                legacyStorageGranted = false,
            ),
        )
    }

    @Test
    fun android12DoesNotRequestNotificationRuntimePermission() {
        assertEquals(
            emptyList<String>(),
            requiredRecordingPermissions(
                sdkInt = 32,
                microphoneGranted = true,
                notificationsGranted = false,
                legacyStorageGranted = true,
            ),
        )
    }

    @Test
    fun android9RequestsLegacyStoragePermission() {
        assertEquals(
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            requiredRecordingPermissions(
                sdkInt = 28,
                microphoneGranted = true,
                notificationsGranted = false,
                legacyStorageGranted = false,
            ),
        )
    }
}
