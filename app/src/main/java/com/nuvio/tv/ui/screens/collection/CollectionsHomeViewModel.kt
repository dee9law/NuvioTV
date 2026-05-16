package com.nuvio.tv.ui.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutScreenScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CollectionsHomeViewModel @Inject constructor(
    collectionsDataStore: CollectionsDataStore,
    layoutPrefs: LayoutPreferenceDataStore,
) : ViewModel() {

    val collections: StateFlow<List<Collection>> = collectionsDataStore.collections
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val rowConfigLookup: StateFlow<Map<String, LayoutRowConfig>> =
        layoutPrefs.rowConfigsForScope(LayoutScreenScope.COLLECTIONS)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )
}
