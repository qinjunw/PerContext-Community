package com.percontext.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.AppearanceSettingsRepository
import com.percontext.app.domain.appearance.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(
    private val repository: AppearanceSettingsRepository,
) : ViewModel() {
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    val settings: StateFlow<AppearanceSettings?> = repository.settings.catch { error ->
        if (error is CancellationException) throw error
        mutableMessages.emit("读取外观设置失败，已使用默认主题")
        emit(AppearanceSettings())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun selectTheme(theme: AppTheme) {
        save { repository.setTheme(theme) }
    }

    fun selectMode(mode: ThemeMode) {
        save { repository.setMode(mode) }
    }

    private fun save(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableMessages.emit("外观设置保存失败，请重试")
            }
        }
    }
}
