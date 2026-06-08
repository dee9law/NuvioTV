package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Isolated ViewModel for the "Buffer & Network" settings screen. Surfaces the
 * bitrate-aware buffer engine's memory target + the opt-in parallel-range
 * download knobs. Kept separate from PlaybackSettingsViewModel so the buffer
 * engine port stays self-contained.
 */
@HiltViewModel
class BufferNetworkSettingsViewModel @Inject constructor(
    private val playerSettingsDataStore: PlayerSettingsDataStore
) : ViewModel() {

    val playerSettings: Flow<PlayerSettings> = playerSettingsDataStore.playerSettings

    fun setParallelNetworkEnabled(enabled: Boolean) {
        viewModelScope.launch { playerSettingsDataStore.setParallelNetworkEnabled(enabled) }
    }

    fun setParallelConnectionCount(count: Int) {
        viewModelScope.launch { playerSettingsDataStore.setParallelConnectionCount(count) }
    }

    fun setParallelChunkSizeMb(mb: Int) {
        viewModelScope.launch { playerSettingsDataStore.setParallelChunkSizeMb(mb) }
    }

    fun setBufferTargetSizeMb(mb: Int) {
        viewModelScope.launch { playerSettingsDataStore.setBufferTargetSizeMb(mb) }
    }
}
