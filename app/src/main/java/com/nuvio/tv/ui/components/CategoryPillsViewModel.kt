package com.nuvio.tv.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.CategoryPillOrderDataStore
import com.nuvio.tv.domain.model.CategoryPill
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
 */
@HiltViewModel
class CategoryPillsViewModel @Inject constructor(
    private val store: CategoryPillOrderDataStore,
) : ViewModel() {

    /** Full ordered list — every [CategoryPill] exactly once, in user order. */
    val order: StateFlow<List<CategoryPillOrderEntry>> = store.order
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CategoryPillOrderEntry.defaultOrder(),
        )

    /** Just the pills currently shown on the TopBar, in user order. */
    val topbarPills: StateFlow<List<CategoryPill>> = store.order
        .map { entries -> entries.filter { it.visibility == PillVisibility.TOPBAR }.map { it.pill } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CategoryPill.entries.toList(),
        )

    /** Just the demoted pills, in user order. Populates the overlay drawer. */
    val drawerPills: StateFlow<List<CategoryPill>> = store.order
        .map { entries -> entries.filter { it.visibility == PillVisibility.DRAWER }.map { it.pill } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    /** Swap two pills at the given indices in the persisted full ordering. */
    fun swap(indexA: Int, indexB: Int) = viewModelScope.launch {
        val current = order.value.toMutableList()
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
        val current = order.value
        val topbarCount = current.count { it.visibility == PillVisibility.TOPBAR }
        if (topbarCount <= 2) return@launch
        store.save(current.map {
            if (it.pill == pill) it.copy(visibility = PillVisibility.DRAWER) else it
        })
    }

    /** Promote [pill] back to the TopBar at the rightmost category position. */
    fun promote(pill: CategoryPill) = viewModelScope.launch {
        val current = order.value.toMutableList()
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
     * Toggle the icons-only display mode for [pill]. Affects how the
     * Modern TopBar renders this specific pill — true hides the text
     * label and shows only the leading icon.
     */
    fun setIconsOnly(pill: CategoryPill, iconsOnly: Boolean) = viewModelScope.launch {
        val current = order.value
        if (current.none { it.pill == pill }) return@launch
        store.save(current.map {
            if (it.pill == pill) it.copy(iconsOnly = iconsOnly) else it
        })
    }

    /** Move [pill] one position toward the front of the full ordering. */
    fun moveUp(pill: CategoryPill) = viewModelScope.launch {
        val current = order.value
        val idx = current.indexOfFirst { it.pill == pill }
        if (idx <= 0) return@launch
        swap(idx, idx - 1)
    }

    /** Move [pill] one position toward the end of the full ordering. */
    fun moveDown(pill: CategoryPill) = viewModelScope.launch {
        val current = order.value
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
