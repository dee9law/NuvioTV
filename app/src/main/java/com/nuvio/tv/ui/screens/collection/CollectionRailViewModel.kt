package com.nuvio.tv.ui.screens.collection

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.components.ChannelTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the "collection mode" of the top channel rail.
 *
 * When [selectedCollectionId] is non-null, [folderChannels] emits a list of
 * [ChannelTab]s — one per folder of the selected collection — that the rail
 * should display *instead of* network channels.
 *
 * Each folder tab carries its routing key in [ChannelTab.id] using the format
 * `folder|<collectionId>|<folderId>`. Consumers (MainActivity) parse that
 * prefix to dispatch click handling.
 */
@HiltViewModel
class CollectionRailViewModel @Inject constructor(
    collectionsDataStore: CollectionsDataStore,
) : ViewModel() {

    companion object {
        const val FOLDER_TAB_ID_PREFIX = "folder|"
        private val FolderTabBrandColor = Color(0xFF4A90D9)

        fun encodeFolderTabId(collectionId: String, folderId: String): String =
            "$FOLDER_TAB_ID_PREFIX$collectionId|$folderId"

        /** Returns (collectionId, folderId) when [id] is a folder tab id, else null. */
        fun decodeFolderTabId(id: String): Pair<String, String>? {
            if (!id.startsWith(FOLDER_TAB_ID_PREFIX)) return null
            val parts = id.removePrefix(FOLDER_TAB_ID_PREFIX).split("|", limit = 2)
            return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty())
                parts[0] to parts[1] else null
        }
    }

    val collections: StateFlow<List<Collection>> = collectionsDataStore.collections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    val selectedCollectionId: StateFlow<String?> = _selectedCollectionId

    val selectedCollection: StateFlow<Collection?> = combine(
        collections,
        _selectedCollectionId,
    ) { list, id ->
        if (id == null) null else list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Folder tabs for the rail. Empty when no collection is selected (the rail
     * should fall back to network channels in that case).
     */
    val folderChannels: StateFlow<List<ChannelTab>> = selectedCollection
        .map { collection ->
            collection?.folders?.map { folder ->
                ChannelTab(
                    id = encodeFolderTabId(collection.id, folder.id),
                    name = folder.title,
                    brandColor = FolderTabBrandColor,
                )
            } ?: emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCollection(collectionId: String) {
        _selectedCollectionId.value = collectionId
    }

    fun clear() {
        _selectedCollectionId.value = null
    }
}
