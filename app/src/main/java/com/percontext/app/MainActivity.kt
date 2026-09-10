package com.percontext.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.percontext.app.feature.app.PerContextApp
import com.percontext.app.feature.record.LEGACY_WRITE_STORAGE_PERMISSION
import com.percontext.app.feature.record.POST_NOTIFICATIONS_PERMISSION
import com.percontext.app.feature.record.RecordViewModel
import com.percontext.app.feature.record.RecordViewModelFactory
import com.percontext.app.feature.record.requiredRecordingPermissions
import com.percontext.app.feature.review.ReviewViewModel
import com.percontext.app.feature.review.ReviewViewModelFactory
import com.percontext.app.feature.settings.SettingsViewModel
import com.percontext.app.feature.settings.SettingsViewModelFactory
import com.percontext.app.ui.theme.PerContextTheme

class MainActivity : ComponentActivity() {
    private val recordViewModel: RecordViewModel by viewModels {
        RecordViewModelFactory(application)
    }
    private val reviewViewModel: ReviewViewModel by viewModels {
        ReviewViewModelFactory(application)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application)
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val storageGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            hasPermission(LEGACY_WRITE_STORAGE_PERMISSION)
        if (hasPermission(Manifest.permission.RECORD_AUDIO) && storageGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(POST_NOTIFICATIONS_PERMISSION)
            ) {
                recordViewModel.notificationPermissionDenied()
            }
            recordViewModel.startRecording()
        } else {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                recordViewModel.microphonePermissionDenied()
            } else {
                recordViewModel.recordingStoragePermissionDenied()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PerContextTheme {
                PerContextApp(
                    recordViewModel = recordViewModel,
                    reviewViewModel = reviewViewModel,
                    settingsViewModel = settingsViewModel,
                    onStartRecording = ::requestRecordingPermissions,
                )
            }
        }
    }

    private fun requestRecordingPermissions() {
        val permissions = requiredRecordingPermissions(
            sdkInt = Build.VERSION.SDK_INT,
            microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = hasPermission(POST_NOTIFICATIONS_PERMISSION),
            legacyStorageGranted = hasPermission(LEGACY_WRITE_STORAGE_PERMISSION),
        )
        if (permissions.isEmpty()) {
            recordViewModel.startRecording()
            return
        }

        permissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
