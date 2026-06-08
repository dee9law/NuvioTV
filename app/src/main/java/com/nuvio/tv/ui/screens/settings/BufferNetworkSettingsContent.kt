package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.data.local.PlayerSettings

/**
 * "Buffer & Network" settings — exposes the bitrate-aware buffer engine's memory
 * target plus the opt-in parallel-range download knobs. Adapted (slimmed) port of
 * upstream's PlaybackBufferNetworkSettings, omitting the VOD-cache / DV7-coupled
 * sections our fork doesn't carry.
 */
@Composable
fun BufferNetworkSettingsContent(
    initialFocusRequester: FocusRequester? = null,
    viewModel: BufferNetworkSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.playerSettings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return

    val connectionSteps = (PlayerSettings.MIN_PARALLEL_CONNECTION_COUNT..PlayerSettings.MAX_PARALLEL_CONNECTION_COUNT).toList()
    val chunkSteps = listOf(8, 16, 32, 64, 128)
    val targetSteps = listOf(0, 100, 150, 300, 600)
    // Max buffer duration target, in seconds (30s–180s, default 50s).
    val bufferDurationSteps = listOf(30, 50, 90, 120, 150, 180)

    fun <T> next(steps: List<T>, current: T): T {
        val i = steps.indexOf(current)
        return steps[(if (i < 0) 0 else i + 1) % steps.size]
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                SettingsDetailHeader(
                    title = "Buffer & Network",
                    subtitle = "Memory-aware buffering and parallel download tuning."
                )
            }

            item(key = "buffer_group") {
                SettingsGroupCard(modifier = Modifier.fillMaxWidth(), title = "Buffer") {
                    SettingsActionRow(
                        title = "Target buffer size",
                        subtitle = "Memory cap for buffered data. Auto sizes from device memory.",
                        value = if (s.bufferSettings.targetBufferSizeMb <= 0) "Auto"
                                else "${s.bufferSettings.targetBufferSizeMb} MB",
                        onClick = { viewModel.setBufferTargetSizeMb(next(targetSteps, s.bufferSettings.targetBufferSizeMb)) }
                    )
                    SettingsActionRow(
                        title = "Max buffer duration",
                        subtitle = "How far ahead to buffer. Higher cushions throughput dips (memory cap still applies).",
                        value = "${s.bufferSettings.maxBufferMs / 1000}s",
                        onClick = {
                            viewModel.setBufferDurationMs(next(bufferDurationSteps, s.bufferSettings.maxBufferMs / 1000) * 1000)
                        }
                    )
                }
            }

            item(key = "parallel_group") {
                SettingsGroupCard(modifier = Modifier.fillMaxWidth(), title = "Parallel download") {
                    SettingsToggleRow(
                        title = "Parallel network download",
                        subtitle = "Fetch progressive streams over multiple connections for higher throughput.",
                        checked = s.parallelNetworkEnabled,
                        onToggle = { viewModel.setParallelNetworkEnabled(!s.parallelNetworkEnabled) }
                    )
                    SettingsActionRow(
                        title = "Connections",
                        subtitle = "Number of parallel HTTP range connections.",
                        value = "${s.parallelConnectionCount}",
                        enabled = s.parallelNetworkEnabled,
                        onClick = { viewModel.setParallelConnectionCount(next(connectionSteps, s.parallelConnectionCount)) }
                    )
                    SettingsActionRow(
                        title = "Chunk size",
                        subtitle = "Per-connection download chunk size.",
                        value = "${s.parallelChunkSizeMb} MB",
                        enabled = s.parallelNetworkEnabled,
                        onClick = { viewModel.setParallelChunkSizeMb(next(chunkSteps, s.parallelChunkSizeMb)) }
                    )
                }
            }
        }
    }
}
