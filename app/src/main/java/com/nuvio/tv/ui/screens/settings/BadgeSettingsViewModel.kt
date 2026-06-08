package com.nuvio.tv.ui.screens.settings

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.StreamBadgeServerManager
import com.nuvio.tv.core.streams.StreamBadgePlacement
import com.nuvio.tv.core.streams.StreamBadgeSettings
import com.nuvio.tv.data.local.StreamBadgeSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the "Stream Badges" settings screen (Fusion Style/Size badges).
 * Exposes the persisted [StreamBadgeSettings] and renders the QR for the badge
 * config "gateway". The config server itself is owned app-wide by
 * [StreamBadgeServerManager] and stays alive for the app's lifetime — this
 * screen only displays its URL; closing the QR or leaving the screen does NOT
 * stop the server.
 */
@HiltViewModel
class BadgeSettingsViewModel @Inject constructor(
    private val dataStore: StreamBadgeSettingsDataStore,
    private val serverManager: StreamBadgeServerManager
) : ViewModel() {

    val settings: StateFlow<StreamBadgeSettings> =
        dataStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, StreamBadgeSettings())

    private val _uiState = MutableStateFlow(BadgeSettingsUiState())
    val uiState: StateFlow<BadgeSettingsUiState> = _uiState.asStateFlow()

    fun setShowFileSizeBadges(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShowFileSizeBadges(enabled) }
    }

    fun setBadgePlacement(placement: StreamBadgePlacement) {
        viewModelScope.launch { dataStore.setStreamBadgePlacement(placement) }
    }

    fun startConfigQrMode() {
        // Ensure the app-lifetime server is bound (idempotent; retries a bind that
        // may have failed at launch), then show its URL as a QR.
        serverManager.start()
        val url = serverManager.serverUrl()
        if (url == null) {
            _uiState.update { it.copy(serverError = "Connect to Wi-Fi to configure badges on another device.") }
            return
        }
        _uiState.update {
            it.copy(
                isQrModeActive = true,
                qrCodeBitmap = QrCodeGenerator.generate(url, 512),
                serverUrl = url,
                serverError = null
            )
        }
    }

    fun stopConfigQrMode() {
        // Only hides the QR overlay — the server keeps running for the app lifetime.
        _uiState.update {
            it.copy(isQrModeActive = false, qrCodeBitmap = null, serverUrl = null)
        }
    }
}

data class BadgeSettingsUiState(
    val isQrModeActive: Boolean = false,
    val qrCodeBitmap: Bitmap? = null,
    val serverUrl: String? = null,
    val serverError: String? = null
)
