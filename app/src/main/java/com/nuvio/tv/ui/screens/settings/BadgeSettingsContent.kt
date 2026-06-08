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
import com.nuvio.tv.ui.screens.addon.QrCodeOverlay

/**
 * "Stream Badges" settings — Fusion Style/Size badges. Lets the user import
 * badge JSON URLs from a phone/PC via an in-app web config server (QR/URL),
 * and toggle the file-size badge. Style badges from imported rules render
 * automatically on stream rows. Modeled on the slim BufferNetworkSettings port.
 */
@Composable
fun BadgeSettingsContent(
    initialFocusRequester: FocusRequester? = null,
    viewModel: BadgeSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val importCount = settings.rules.imports.size
    val activeBadgeCount = settings.rules.enabledFilterCount

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                SettingsDetailHeader(
                    title = "Stream Badges",
                    subtitle = "Fusion Style/Size badges shown on stream sources."
                )
            }

            item(key = "import_group") {
                SettingsGroupCard(modifier = Modifier.fillMaxWidth(), title = "Fusion badges") {
                    SettingsActionRow(
                        title = "Configure on another device",
                        subtitle = if (importCount > 0) {
                            "$importCount badge URL(s) imported, $activeBadgeCount active. Open the config page to manage."
                        } else {
                            "Open a web page on your phone or PC to import badge JSON URLs."
                        },
                        value = "Open",
                        onClick = { viewModel.startConfigQrMode() }
                    )
                }
            }

            item(key = "display_group") {
                SettingsGroupCard(modifier = Modifier.fillMaxWidth(), title = "Display") {
                    SettingsToggleRow(
                        title = "Show file size badges",
                        subtitle = "Show a size chip (e.g. SIZE 1.2 GB) on streams that report a size.",
                        checked = settings.showFileSizeBadges,
                        onToggle = { viewModel.setShowFileSizeBadges(!settings.showFileSizeBadges) }
                    )
                }
            }
        }
    }

    if (uiState.isQrModeActive) {
        QrCodeOverlay(
            qrBitmap = uiState.qrCodeBitmap,
            serverUrl = uiState.serverUrl,
            instruction = "Open this URL on your phone or PC to import Fusion badge JSON URLs. Keep this screen open while configuring.",
            onClose = { viewModel.stopConfigQrMode() }
        )
    }
}
