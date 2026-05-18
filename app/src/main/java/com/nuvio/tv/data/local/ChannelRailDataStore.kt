package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One pill in the top-bar channel rail. Identifies a folder inside a
 * collection plus the user's on/off preference. The pill text is the folder's
 * title (snapshot — refreshed against current collections at render time).
 *
 * [titleLogoUrl] mirrors the folder's `titleLogoUrl` from the collections
 * JSON. When present, the pill renders the logo image with the title text as
 * a fallback / caption beneath it.
 */
data class ChannelRailPill(
    val collectionId: String,
    val folderId: String,
    val folderTitle: String,
    val enabled: Boolean = true,
    val titleLogoUrl: String? = null,
)

/**
 * Per-profile DataStore for the new top-bar channel rail. Replaces the old
 * TMDB-networks rail. The rail's pills come from the user's collection
 * folders: the user can toggle individual folders on/off through the + button
 * dropdown.
 *
 * Seeding: on first read (or whenever the persisted set is empty and
 * collections are non-empty), the rail seeds with EVERY folder from EVERY
 * collection, all enabled. Seeding is performed by [ChannelRailViewModel];
 * this class only persists and exposes the raw pill list.
 *
 * Storage shape: gson-serialized JSON array of [ChannelRailPill]. We also
 * persist a one-shot `seeded` flag so we can distinguish "user has cleared
 * every pill" from "fresh install".
 */
@Singleton
class ChannelRailDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
) {
    companion object {
        private const val FEATURE = "channel_rail"
        private val PILLS_KEY = stringPreferencesKey("pills_json")
        private val SEEDED_KEY = booleanPreferencesKey("seeded")
    }

    private val gson = Gson()

    private fun store(pid: Int = profileManager.activeProfileId.value) =
        factory.get(pid, FEATURE)

    val pills: Flow<List<ChannelRailPill>> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs ->
                parsePills(prefs[PILLS_KEY])
            }
        }

    val seeded: Flow<Boolean> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs -> prefs[SEEDED_KEY] ?: false }
        }

    suspend fun save(pills: List<ChannelRailPill>) {
        store().edit { prefs ->
            prefs[PILLS_KEY] = gson.toJson(pills.map { it.toSerializable() })
            prefs[SEEDED_KEY] = true
        }
    }

    /**
     * Toggles `enabled` for the matching pill. If no matching pill exists yet
     * (e.g. a new folder was just added to a collection), inserts a new
     * enabled pill at the end. This is the primary write path used by the +
     * dropdown.
     */
    suspend fun toggle(
        collectionId: String,
        folderId: String,
        folderTitle: String,
        titleLogoUrl: String? = null,
    ) {
        store().edit { prefs ->
            val current = parsePills(prefs[PILLS_KEY]).toMutableList()
            val idx = current.indexOfFirst { it.collectionId == collectionId && it.folderId == folderId }
            if (idx >= 0) {
                val p = current[idx]
                current[idx] = p.copy(enabled = !p.enabled, titleLogoUrl = titleLogoUrl ?: p.titleLogoUrl)
            } else {
                current += ChannelRailPill(
                    collectionId = collectionId,
                    folderId = folderId,
                    folderTitle = folderTitle,
                    enabled = true,
                    titleLogoUrl = titleLogoUrl,
                )
            }
            prefs[PILLS_KEY] = gson.toJson(current.map { it.toSerializable() })
            prefs[SEEDED_KEY] = true
        }
    }

    @androidx.annotation.Keep
    private data class SerializablePill(
        val collectionId: String,
        val folderId: String,
        val folderTitle: String,
        val enabled: Boolean = true,
        val titleLogoUrl: String? = null,
    )

    private fun ChannelRailPill.toSerializable() = SerializablePill(
        collectionId = collectionId,
        folderId = folderId,
        folderTitle = folderTitle,
        enabled = enabled,
        titleLogoUrl = titleLogoUrl,
    )

    private fun SerializablePill.toDomain() = ChannelRailPill(
        collectionId = collectionId,
        folderId = folderId,
        folderTitle = folderTitle,
        enabled = enabled,
        titleLogoUrl = titleLogoUrl,
    )

    private fun parsePills(json: String?): List<ChannelRailPill> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SerializablePill>>() {}.type
            gson.fromJson<List<SerializablePill>>(json, type).orEmpty().map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
