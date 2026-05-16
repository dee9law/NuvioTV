package com.nuvio.tv.domain.model

/**
 * Per-row configuration used by the new "Layout & Rows" settings screen.
 * Each row reference points at a catalog source ([kind] + [id]) and carries
 * its own card style + width override.
 *
 * [viewContext] tags the row with the scope it was added under. Used by
 * [LayoutPreferenceDataStore.rowsForScope] to filter at read time so each
 * screen pill (HOME / MOVIES / TV) renders only its own rows. Legacy rows
 * stored without a viewContext default to HOME.
 *
 * [metadata] carries kind-specific payload that doesn't fit on the canonical
 * row id. TMDB_DISCOVER stores its query params here (media_type, sort_by,
 * with_genres, year). TMDB_NETWORK stores network_id + media_type. COLLECTION
 * stores collection_id + folder_id when a specific folder is targeted.
 */
data class LayoutRowConfig(
    val id: String,
    val kind: LayoutRowKind,
    val name: String,
    val cardStyle: LayoutCardStyle = LayoutCardStyle.POSTER,
    val cardWidthDp: Int = 126,
    val enabled: Boolean = true,
    val viewContext: LayoutScreenScope = LayoutScreenScope.HOME,
    val metadata: Map<String, String> = emptyMap(),
)

enum class LayoutRowKind { ADDON, COLLECTION, TRAKT, TMDB_DISCOVER, TMDB_NETWORK }
enum class LayoutCardStyle { POSTER, LANDSCAPE }

/**
 * Canonical row IDs used as the persistence key for [LayoutRowConfig] and for
 * the per-row config lookup performed by row renderers. The format must match
 * what the layout settings screen writes — see [NewLayoutSettingsViewModel].
 */
object LayoutRowKey {
    fun forAddon(addonId: String, apiType: String, catalogId: String): String =
        "addon|$addonId|$apiType|$catalogId"

    fun forCollection(collectionId: String): String = "collection|$collectionId"

    fun forCollectionFolder(collectionId: String, folderId: String): String =
        "collection|$collectionId|$folderId"

    fun forTrakt(slug: String): String = "trakt|$slug"

    /**
     * TMDB Discover row — keyed on the query so distinct queries produce
     * distinct rows. Used by the "+ TMDB Source" picker's Discover section.
     */
    fun forTmdbDiscover(
        mediaType: String,
        sortBy: String,
        genre: String? = null,
        year: String? = null,
    ): String =
        "tmdb_discover|$mediaType|$sortBy|${genre.orEmpty()}|${year.orEmpty()}"

    /**
     * TMDB Network / streaming-provider row. Used by the "+ TMDB Source"
     * picker's Networks section.
     */
    fun forTmdbNetwork(networkId: Int, mediaType: String): String =
        "tmdb_network|$networkId|$mediaType"
}

/**
 * Identifies which "screen" a set of layout settings applies to. The new
 * settings screen scopes the layout picker, corner radius, fullscreen hero
 * toggle, and rows list per screen.
 *
 * [scopeKey] is the storage suffix used by [LayoutPreferenceDataStore] to key
 * per-screen preferences. The HOME scope reuses the existing app-global keys
 * so the old "Layout & Rows" screen and the new one stay in sync for Home.
 */
enum class LayoutScreenScope(val scopeKey: String, val displayName: String) {
    HOME("home", "Home"),
    MOVIES("movies", "Movies"),
    TV("tv", "TV Shows"),
    COLLECTIONS("collections", "Collections"),
    DETAIL("detail", "Detail Page"),
}

/**
 * Three-tier resolution for any per-screen layout setting: per-row override
 * wins; else per-screen value; else global fallback. The global tier is the
 * floor — it's typed non-null and supplies the default when neither narrower
 * tier opts in.
 */
fun <T : Any> resolveLayoutSetting(perRow: T?, perScreen: T?, global: T): T =
    perRow ?: perScreen ?: global
