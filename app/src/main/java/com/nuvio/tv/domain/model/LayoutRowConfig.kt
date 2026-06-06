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
    /**
     * Per-row expand-to-backdrop override. Resolved against the per-scope
     * global (`focusedPosterBackdropExpandEnabledForScope`) via
     * [resolveLayoutSetting]:
     *  - `true`  → this row always expands, regardless of the scope setting
     *  - `false` → this row never expands, regardless of the scope setting
     *  - `null`  → follow the per-scope global expand setting
     *
     * Defaults to `null` so existing saved rows deserialize unchanged.
     */
    val expandEnabled: Boolean? = null,
)

enum class LayoutRowKind {
    ADDON, COLLECTION, TRAKT, TMDB_DISCOVER, TMDB_NETWORK,
    // CONTINUE_WATCHING is the legacy single-CW kind, kept so old persisted
    // rows still deserialize; a one-shot migration rewrites them to
    // CONTINUE_WATCHING_SERIES. New CW rows use the split kinds below.
    CONTINUE_WATCHING,
    CONTINUE_WATCHING_SERIES, CONTINUE_WATCHING_MOVIES,
    // Trakt "Up Next" (next unwatched episode per show) — reuses the Continue
    // Watching render path (NextUp items) rather than the Trakt catalog fetch.
    TRAKT_UP_NEXT,
}

/**
 * The three Continue-Watching-derived row variants. All three render through
 * the shared ContinueWatchingCard, differing only in which slice of the CW
 * item list they show:
 *  - [SERIES]  — in-progress + next-up shows/episodes (non-movie).
 *  - [MOVIES]  — in-progress movies.
 *  - [UP_NEXT] — next-unwatched-episode (NextUp) items only.
 */
enum class ContinueWatchingFilter { SERIES, MOVIES, UP_NEXT }

/** Maps a row kind to its CW filter, or null if the kind is not CW-derived. */
val LayoutRowKind.continueWatchingFilter: ContinueWatchingFilter?
    get() = when (this) {
        LayoutRowKind.CONTINUE_WATCHING_SERIES -> ContinueWatchingFilter.SERIES
        LayoutRowKind.CONTINUE_WATCHING_MOVIES -> ContinueWatchingFilter.MOVIES
        LayoutRowKind.TRAKT_UP_NEXT -> ContinueWatchingFilter.UP_NEXT
        // Legacy single CW kind behaves as Series.
        LayoutRowKind.CONTINUE_WATCHING -> ContinueWatchingFilter.SERIES
        else -> null
    }

/**
 * Card shape for a row's posters.
 *  - [POSTER]    — tall 2:3 portrait, size-adjustable.
 *  - [LANDSCAPE] — wide 16:9-ish, size-adjustable.
 *  - [CINEMA]    — fixed-size 16:9 cards (420dp × 236dp). One size only: the
 *    size picker is hidden for CINEMA rows. Corner radius still follows the
 *    global card corner-radius setting. Expand still works (poster → backdrop,
 *    same 16:9 ratio, larger).
 */
enum class LayoutCardStyle { POSTER, LANDSCAPE, CINEMA }

/**
 * Orientation options for a Continue Watching row. These are CW-specific and
 * deliberately separate from [LayoutCardStyle] — CW has no CINEMA, and its
 * "Wide" is a horizontal artwork-left + text-right strip that has no generic
 * card-style equivalent. Stored on the CW row's [LayoutRowConfig.metadata]
 * under [CW_STYLE_METADATA_KEY] so the generic cardStyle/CINEMA size-hiding
 * logic is untouched.
 *  - [POSTER] — tall portrait card, progress-bar pill at the bottom of the image.
 *  - [CARD]   — 16:9 landscape card, progress bar along the bottom edge (default).
 *  - [WIDE]   — horizontal strip: artwork left, title/episode/progress + % right.
 */
enum class ContinueWatchingCardStyle { POSTER, CARD, WIDE }

/** Metadata key under which a CONTINUE_WATCHING row stores its orientation. */
const val CW_STYLE_METADATA_KEY = "cw_style"

/**
 * Default poster-scale width for a Continue Watching row ("Medium" on the
 * Compact→Large size scale — the 120dp "Standard" step). The actual card
 * footprint is derived from this base per orientation by the CW card.
 */
const val CW_DEFAULT_CARD_WIDTH_DP = 120

/**
 * Resolved Continue Watching orientation for a row, read from [metadata].
 * Defaults to [ContinueWatchingCardStyle.CARD] for legacy / unset rows.
 */
val LayoutRowConfig.continueWatchingStyle: ContinueWatchingCardStyle
    get() = metadata[CW_STYLE_METADATA_KEY]
        ?.let { raw -> runCatching { ContinueWatchingCardStyle.valueOf(raw) }.getOrNull() }
        ?: ContinueWatchingCardStyle.CARD

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

    /** Legacy single CW row id (pre-split). Kept for migration matching. */
    fun forContinueWatching(): String = "continue_watching"

    fun forContinueWatchingSeries(): String = "continue_watching_series"
    fun forContinueWatchingMovies(): String = "continue_watching_movies"
    fun forTraktUpNext(): String = "trakt_up_next"

    /** Canonical id (== rowConfigLookup key) for a given CW filter variant. */
    fun forContinueWatchingFilter(filter: ContinueWatchingFilter): String = when (filter) {
        ContinueWatchingFilter.SERIES -> forContinueWatchingSeries()
        ContinueWatchingFilter.MOVIES -> forContinueWatchingMovies()
        ContinueWatchingFilter.UP_NEXT -> forTraktUpNext()
    }
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
