@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.LayoutScreenScope
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Stand-alone wrapper around [NewLayoutSettingsContent] that pre-selects the
 * given [scope] and renders the rows-only mode. Used by the Home / Movies /
 * TV empty-state "Add Your Catalogs" button so users land directly on the
 * rows list for the screen they're trying to populate, rather than having to
 * navigate into Settings → Appearance and switch the scope pill themselves.
 */
@Composable
fun AppearanceRowsScreen(
    scope: LayoutScreenScope,
    onBack: () -> Unit,
) {
    val viewModel: NewLayoutSettingsViewModel = hiltViewModel()
    LaunchedEffect(scope) {
        viewModel.selectScope(scope)
    }
    BackHandler(onBack = onBack)

    val initialFr = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
            .padding(horizontal = 64.dp, vertical = 32.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = scope.displayName + " — Rows",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NuvioColors.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            NewLayoutSettingsContent(
                initialFocusRequester = initialFr,
                viewModel = viewModel,
                mode = NewLayoutContentMode.ROWS_ONLY,
            )
        }
    }
}
