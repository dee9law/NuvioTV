package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.ChannelRailDataStore
import com.nuvio.tv.data.local.ChannelRailPill
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.components.ChannelTab
import com.nuvio.tv.ui.screens.collection.CollectionRailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One row in the + button's folder-pill dropdown. The dropdown groups rows by
 * [collectionTitle]; consumers render a header per group.
 */
data class FolderPillOption(
    val collectionId: String,
    val collectionTitle: String,
    val folderId: String,
    val folderTitle: String,
    val enabled: Boolean,
    /** Optional title-treatment logo URL from collections.json. When
     *  set, the picker row renders it alongside [folderTitle] just like
     *  the channel pills on the TopBar (Task G-A parity). */
    val titleLogoUrl: String? = null,
)

private val PillBrandColor = Color(0xFF4A90D9)

/**
 * Drives the top-bar channel rail. Each pill is a folder from one of the
 * user's collections. On first launch (DataStore empty), every folder from
 * every collection is seeded as an enabled pill — the user can then toggle
 * individual ones off via the + button dropdown.
 *
 * Pill ids reuse [CollectionRailViewModel.encodeFolderTabId] so MainActivity's
 * existing folder-tab decode path picks them up unchanged.
 */
@HiltViewModel
class ChannelRailViewModel @Inject constructor(
    private val railDataStore: ChannelRailDataStore,
    private val collectionsDataStore: CollectionsDataStore,
) : ViewModel() {

    /**
     * Enabled pills only — what the top bar should render in its default mode.
     * Pills are ordered to match the user's collection order, then folder
     * order within each collection. Folders deleted from collections are
     * dropped here even if still in the persisted pill list (they'd lead
     * nowhere on tap).
     */
    val enabledPills: StateFlow<List<ChannelTab>> = combine(
        railDataStore.pills,
        collectionsDataStore.collections,
    ) { savedPills, collections ->
        val savedByKey = savedPills.associateBy { it.key }
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<ChannelTab>()
        collections.forEach { collection ->
            collection.folders.forEach { folder ->
                val key = pillKey(collection.id, folder.id)
                seenKeys += key
                val saved = savedByKey[key]
                // Default: enabled. (When DataStore is empty, the seeder
                // below also primes this same default to disk.)
                if (saved?.enabled ?: true) {
                    result += ChannelTab(
                        id = CollectionRailViewModel.encodeFolderTabId(collection.id, folder.id),
                        name = folder.title,
                        brandColor = PillBrandColor,
                        // Always prefer the live JSON value over the cached
                        // pill snapshot — a user-edited logo URL in
                        // collections.json should take effect immediately.
                        titleLogoUrl = folder.titleLogoUrl,
                    )
                }
            }
        }
        result.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Every folder from every collection, paired with the user's enabled flag.
     * Powers the + button dropdown. Folders not yet in the persisted set are
     * treated as enabled by default (matches the seed behavior).
     */
    val allFolderOptions: StateFlow<List<FolderPillOption>> = combine(
        railDataStore.pills,
        collectionsDataStore.collections,
    ) { savedPills, collections ->
        val savedByKey = savedPills.associateBy { it.key }
        collections.flatMap { collection ->
            collection.folders.map { folder ->
                val saved = savedByKey[pillKey(collection.id, folder.id)]
                FolderPillOption(
                    collectionId = collection.id,
                    collectionTitle = collection.title,
                    folderId = folder.id,
                    folderTitle = folder.title,
                    enabled = saved?.enabled ?: true,
                    titleLogoUrl = folder.titleLogoUrl,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // First-launch seed. If the rail has never been seeded for this
        // profile and collections exist, write the full enabled set to disk
        // so the dropdown has explicit state to toggle.
        viewModelScope.launch {
            val alreadySeeded = railDataStore.seeded.first()
            if (alreadySeeded) return@launch
            val collections = collectionsDataStore.collections.first()
            if (collections.isEmpty()) return@launch
            val seedPills = collections.flatMap { c ->
                c.folders.map { f ->
                    ChannelRailPill(
                        collectionId = c.id,
                        folderId = f.id,
                        folderTitle = f.title,
                        enabled = true,
                        titleLogoUrl = f.titleLogoUrl,
                    )
                }
            }
            railDataStore.save(seedPills)
        }
    }

    fun togglePill(option: FolderPillOption) {
        viewModelScope.launch {
            railDataStore.toggle(option.collectionId, option.folderId, option.folderTitle)
        }
    }

    private fun pillKey(collectionId: String, folderId: String) = "$collectionId|$folderId"
    private val ChannelRailPill.key: String get() = pillKey(collectionId, folderId)
}
