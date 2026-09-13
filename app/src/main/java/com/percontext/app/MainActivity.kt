package com.percontext.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.ThemeMode
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
import com.percontext.app.feature.settings.AppearanceViewModel
import com.percontext.app.feature.settings.AppearanceViewModelFactory
import com.percontext.app.ui.theme.PerContextTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var appearanceApplied = false
    private val recordViewModel: RecordViewModel by viewModels {
        RecordViewModelFactory(application)
    }
    private val reviewViewModel: ReviewViewModel by viewModels {
        ReviewViewModelFactory(application)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(application)
    }
    private val appearanceViewModel: AppearanceViewModel by viewModels {
        AppearanceViewModelFactory(application)
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
        window.decorView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!appearanceApplied) return false
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
        lifecycleScope.launch {
            appearanceViewModel.settings.filterNotNull().first()
            setAppContent()
        }
    }

    private fun setAppContent() {
        setContent {
            val savedAppearance by appearanceViewModel.settings.collectAsStateWithLifecycle()
            val appearance = savedAppearance ?: AppearanceSettings()
            val darkTheme = when (appearance.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PerContextTheme(theme = appearance.theme, darkTheme = darkTheme) {
                val background = PerContextTheme.colors.background
                SideEffect {
                    appearanceApplied = savedAppearance != null
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(
                            background.toArgb(),
                            if (darkTheme) background.toArgb() else android.graphics.Color.DKGRAY,
                        ) { darkTheme },
                    )
                }
                Surface(modifier = Modifier.fillMaxSize(), color = background) {
                    if (savedAppearance != null) {
                        PerContextApp(
                            recordViewModel = recordViewModel,
                            reviewViewModel = reviewViewModel,
                            settingsViewModel = settingsViewModel,
                            appearanceViewModel = appearanceViewModel,
                            onStartRecording = ::requestRecordingPermissions,
                        )
                    }
                }
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
