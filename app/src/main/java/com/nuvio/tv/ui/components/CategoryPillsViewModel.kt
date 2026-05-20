package com.nuvio.tv.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.CategoryPillOrderDataStore
import com.nuvio.tv.domain.model.CategoryPill
import com.nuvio.tv.domain.model.CategoryPillDisplayMode
import com.nuvio.tv.domain.model.CategoryPillOrderEntry
import com.nuvio.tv.domain.model.PillVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Source of truth for the Modern TopBar's pill order + visibility. F4–F7
 * render a static four-pill list; F10's Edit Mode swaps that for the
 * live flows here so the user can reorder and demote pills.
 *
 * UI-layer policy is enforced here, not in the DataStore:
 *  - [CategoryPill.HOME] is never demoted (silently rejected).
 *  - At least two pills must remain on the TopBar at all times — if a
 *    demotion would drop the topbar count to 1, the demotion is rejected.
 *
 * **Loading semantics:** [order] is nullable and emits `null` until the
 * first read from [CategoryPillOrderDataStore] completes. Consumers must
 * treat `null` as "not yet loaded" and skip rendering pill UI / writing
 * back. This prevents a cold-start race where the StateFlow seed defaults
 * would be observed and persisted, clobbering the user's saved layout.
 */
@HiltViewModel
class CategoryPillsViewModel @Inject constructor(
    private val store: CategoryPillOrderDataStore,
) : ViewModel() {

    /**
     * Full ordered list — every [CategoryPill] exactly once, in user
     * order. `null` until the first DataStore emission lands; consumers
     * should render pill UI only after this becomes non-null.
     */
    val order: StateFlow<List<CategoryPillOrderEntry>?> = store.order
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    /** True once the persisted order has been read at least once. */
    val isLoaded: StateFlow<Boolean> = order
        .map { it != null }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    /**
     * Pills currently shown on the TopBar, in user order. Empty list
     * during the loading window — TopBar should render no pills until
     * [isLoaded] flips true rather than show defaults that may be
     * overwritten.
     */
    val topbarPills: StateFlow<List<CategoryPill>> = order
        .map { entries ->
            entries?.filter { it.visibility == PillVisibility.TOPBAR }?.map { it.pill }
                ?: emptyList()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList(),
        )

    /** Just the demoted pills, in user order. Populates the overlay drawer. */
    val drawerPills: StateFlow<List<CategoryPill>> = order
        .map { entries ->
            entries?.filter { it.visibility == PillVisibility.DRAWER }?.map { it.pill }
                ?: emptyList()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList(),
        )

    /** Swap two pills at the given indices in the persisted full ordering. */
    fun swap(indexA: Int, indexB: Int) = viewModelScope.launch {
        val current = (order.value ?: return@launch).toMutableList()
        if (indexA !in current.indices || indexB !in current.indices) return@launch
        val tmp = current[indexA]
        current[indexA] = current[indexB]
        current[indexB] = tmp
        store.save(current)
    }

    /**
     * Demote [pill] to the drawer. No-ops when:
     *  - the pill is [CategoryPill.HOME] (Home must stay on the TopBar), or
     *  - it would drop the TopBar pill count below 2.
     */
    fun demote(pill: CategoryPill) = viewModelScope.launch {
        if (pill == CategoryPill.HOME) return@launch
        val current = order.value ?: return@launch
        val topbarCount = current.count { it.visibility == PillVisibility.TOPBAR }
        if (topbarCount <= 2) return@launch
        store.save(current.map {
            if (it.pill == pill) it.copy(visibility = PillVisibility.DRAWER) else it
        })
    }

    /** Promote [pill] back to the TopBar at the rightmost category position. */
    fun promote(pill: CategoryPill) = viewModelScope.launch {
        val current = (order.value ?: return@launch).toMutableList()
        val idx = current.indexOfFirst { it.pill == pill }
        if (idx < 0) return@launch
        // Move to the end of the topbar group (just before the first drawer
        // entry, or at the very end if there are none) and flip visibility.
        val updated = current[idx].copy(visibility = PillVisibility.TOPBAR)
        current.removeAt(idx)
        val firstDrawerIdx = current.indexOfFirst { it.visibility == PillVisibility.DRAWER }
        if (firstDrawerIdx < 0) current.add(updated) else current.add(firstDrawerIdx, updated)
        store.save(current)
    }

    /**
     * Cycle [pill] to the next display mode in the rotation
     * (Icon + Text → Icon Only → Text Only → Icon + Text). The Modern
     * TopBar uses the per-pill mode to decide what to render.
     */
    fun cycleDisplayMode(pill: CategoryPill) = viewModelScope.launch {
        val current = order.value ?: return@launch
        if (current.none { it.pill == pill }) return@launch
        store.save(current.map {
            if (it.pill == pill) it.copy(displayMode = it.displayMode.next()) else it
        })
    }

    /** Set an explicit display mode for [pill]. */
    fun setDisplayMode(pill: CategoryPill, mode: CategoryPillDisplayMode) = viewModelScope.launch {
        val current = order.value ?: return@launch
        if (current.none { it.pill == pill }) return@launch
        store.save(current.map {
            if (it.pill == pill) it.copy(displayMode = mode) else it
        })
    }

    /** Move [pill] one position toward the front of the full ordering. */
    fun moveUp(pill: CategoryPill) = viewModelScope.launch {
        val current = order.value ?: return@launch
        val idx = current.indexOfFirst { it.pill == pill }
        if (idx <= 0) return@launch
        swap(idx, idx - 1)
    }

    /** Move [pill] one position toward the end of the full ordering. */
    fun moveDown(pill: CategoryPill) = viewModelScope.launch {
        val current = order.value ?: return@launch
        val idx = current.indexOfFirst { it.pill == pill }
        if (idx < 0 || idx >= current.lastIndex) return@launch
        swap(idx, idx + 1)
    }

    /**
     * Flip [pill]'s visibility flag. Promote/demote rules are enforced by
     * the existing [demote] / [promote] handlers (Home protection, ≥2
     * pills on TopBar). This is the convenience entry point for the
     * "Top Bar" settings screen.
     */
    fun setVisible(pill: CategoryPill, visible: Boolean) {
        if (visible) promote(pill) else demote(pill)
    }
}
