package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.CategoryPill
import com.nuvio.tv.domain.model.CategoryPillDisplayMode
import com.nuvio.tv.domain.model.CategoryPillOrderEntry
import com.nuvio.tv.domain.model.PillVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-profile storage for the Modern TopBar's category pill order +
 * per-pill visibility (TOPBAR / DRAWER). Used by Edit Mode (F10) to let
 * the user reorder pills and demote some into the Profile Overlay's
 * Hidden Items section.
 *
 * Serialization: JSON array of [SerializableEntry] objects. Unknown pills
 * in the persisted payload are dropped; missing pills are appended at the
 * end with the default visibility so a future build adding a fifth pill
 * doesn't lose user state.
 *
 * The data layer never enforces the "Home must stay on TopBar" or "≥ 2
 * pills on TopBar" rules — Edit Mode's UI layer is responsible for those
 * (it knows about user intent and can show feedback). The DataStore just
 * persists whatever it's given.
 */
@Singleton
class CategoryPillOrderDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
) {
    companion object {
        private const val FEATURE = "category_pill_order"
        private val ORDER_KEY = stringPreferencesKey("order_json")
    }

    private val gson = Gson()

    private fun store(pid: Int = profileManager.activeProfileId.value) =
        factory.get(pid, FEATURE)

    /**
     * Live ordered list of every [CategoryPill] with its current
     * visibility. Always contains every pill exactly once — even on first
     * launch (returns [CategoryPillOrderEntry.defaultOrder]).
     */
    val order: Flow<List<CategoryPillOrderEntry>> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs ->
                reconcileWithDefaults(parseOrder(prefs[ORDER_KEY]))
            }
        }

    /** Persists [order] verbatim. Caller is responsible for validity. */
    suspend fun save(order: List<CategoryPillOrderEntry>) {
        store().edit { prefs ->
            prefs[ORDER_KEY] = gson.toJson(order.map { it.toSerializable() })
        }
    }

    // ── Serialization mirrors ────────────────────────────────────────────

    @androidx.annotation.Keep
    private data class SerializableEntry(
        val pillId: String,
        val visibility: String,
        // `iconsOnly` is the legacy field; older payloads only carry this
        // boolean. Newer payloads write `displayMode` (string). When
        // reading, [displayMode] takes precedence; if absent, [iconsOnly]
        // backfills it (`true` → ICON_ONLY, `false` → ICON_AND_TEXT).
        val iconsOnly: Boolean = false,
        val displayMode: String? = null,
    )

    private fun CategoryPillOrderEntry.toSerializable() = SerializableEntry(
        pillId = pill.storageId,
        visibility = visibility.storageValue,
        iconsOnly = displayMode == CategoryPillDisplayMode.ICON_ONLY,
        displayMode = displayMode.storageValue,
    )

    private fun parseOrder(json: String?): List<CategoryPillOrderEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SerializableEntry>>() {}.type
            val raw: List<SerializableEntry> = gson.fromJson(json, type) ?: emptyList()
            raw.mapNotNull { entry ->
                val pill = CategoryPill.fromStorageId(entry.pillId) ?: return@mapNotNull null
                val mode = when {
                    entry.displayMode != null ->
                        CategoryPillDisplayMode.fromStorageValue(entry.displayMode)
                    entry.iconsOnly -> CategoryPillDisplayMode.ICON_ONLY
                    else -> CategoryPillDisplayMode.ICON_AND_TEXT
                }
                CategoryPillOrderEntry(
                    pill = pill,
                    visibility = PillVisibility.fromStorageValue(entry.visibility),
                    displayMode = mode,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Ensures the returned list contains every [CategoryPill] exactly
     * once. Pills missing from the persisted payload (e.g. after a build
     * that introduces a new pill type) are appended at the end with the
     * pill's [CategoryPill.defaultVisibility] — so first-launch users
     * always see every pill in its intended default slot (TopBar or
     * drawer).
     */
    private fun reconcileWithDefaults(
        persisted: List<CategoryPillOrderEntry>,
    ): List<CategoryPillOrderEntry> {
        if (persisted.isEmpty()) return CategoryPillOrderEntry.defaultOrder()
        val seen = persisted.map { it.pill }.toMutableSet()
        val missing = CategoryPill.entries.filterNot { it in seen }
        return persisted + missing.map {
            CategoryPillOrderEntry(it, it.defaultVisibility)
        }
    }
}
