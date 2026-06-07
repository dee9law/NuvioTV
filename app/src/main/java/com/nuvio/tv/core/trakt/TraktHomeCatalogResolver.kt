package com.nuvio.tv.core.trakt

import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktMovieDto
import com.nuvio.tv.data.remote.dto.trakt.TraktShowDto
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.data.repository.normalizeContentId
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the six Trakt "catalog" home rows (recommendations / watchlist /
 * calendars) into [CatalogRow]s of [MetaPreview]s, mapped directly from Trakt's
 * own `extended=full,images` payload (no extra image fetching).
 *
 * Modeled on [TraktPublicListSourceResolver]; auth-gated (returns null when the
 * user isn't signed in) with a per-kind TTL cache: 30 min for calendars, 60 min
 * for watchlist + recommendations. Deliberately separate from the Continue
 * Watching / Up Next pipeline — this never touches playback/progress data.
 */
@Singleton
class TraktHomeCatalogResolver @Inject constructor(
    private val traktApi: TraktApi,
    private val traktAuthService: TraktAuthService,
    private val profileManager: ProfileManager,
) {
    private data class Cached(val row: CatalogRow, val fetchedAtMs: Long)

    // Keyed by "<profileId>:<kind>" so cached rows never leak across profiles.
    private val cache = mutableMapOf<String, Cached>()
    private val mutex = Mutex()

    private fun cacheKey(kind: LayoutRowKind): String =
        "${profileManager.activeProfileId.value}:${kind.name}"

    /**
     * Returns the [CatalogRow] for [kind], honoring the TTL cache. Returns the
     * last good cached row on a transient failure, or null when not authenticated
     * / never successfully fetched.
     */
    suspend fun resolve(kind: LayoutRowKind, nowMs: Long = System.currentTimeMillis()): CatalogRow? {
        if (!traktAuthService.getCurrentAuthState().isAuthenticated) return null
        val ck = cacheKey(kind)
        mutex.withLock {
            cache[ck]?.let { cached ->
                if (nowMs - cached.fetchedAtMs <= ttlFor(kind)) return cached.row
            }
        }
        val fresh = runCatching { withContext(Dispatchers.IO) { fetch(kind) } }.getOrNull()
        if (fresh != null) {
            mutex.withLock { cache[ck] = Cached(fresh, nowMs) }
            return fresh
        }
        // Fall back to a stale-but-present row rather than dropping it on a blip.
        return mutex.withLock { cache[ck]?.row }
    }

    /** Drops all cached rows (e.g. on profile switch / sign-out). */
    suspend fun clear() = mutex.withLock { cache.clear() }

    private fun ttlFor(kind: LayoutRowKind): Long = when (kind) {
        LayoutRowKind.TRAKT_NEW_EPISODES, LayoutRowKind.TRAKT_NEW_MOVIES -> CALENDAR_TTL_MS
        else -> LIST_TTL_MS
    }

    private suspend fun fetch(kind: LayoutRowKind): CatalogRow? = when (kind) {
        LayoutRowKind.TRAKT_RECOMMENDED_SHOWS -> {
            val items = authed { traktApi.getRecommendedShows(it, extended = EXTENDED) }
                ?.mapNotNull { dto -> dto.toPreview() }.dedup()
            row(kind, "Recommended Shows", ContentType.SERIES, items)
        }
        LayoutRowKind.TRAKT_RECOMMENDED_MOVIES -> {
            val items = authed { traktApi.getRecommendedMovies(it, extended = EXTENDED) }
                ?.mapNotNull { dto -> dto.toPreview() }.dedup()
            row(kind, "Recommended Movies", ContentType.MOVIE, items)
        }
        LayoutRowKind.TRAKT_WATCHLIST_SHOWS -> {
            val items = authed { traktApi.getWatchlist(it, type = "shows", extended = EXTENDED) }
                ?.mapNotNull { dto -> dto.show?.toPreview() }.dedup()
            row(kind, "Watchlist Shows", ContentType.SERIES, items)
        }
        LayoutRowKind.TRAKT_WATCHLIST_MOVIES -> {
            val items = authed { traktApi.getWatchlist(it, type = "movies", extended = EXTENDED) }
                ?.mapNotNull { dto -> dto.movie?.toPreview() }.dedup()
            row(kind, "Watchlist Movies", ContentType.MOVIE, items)
        }
        LayoutRowKind.TRAKT_NEW_EPISODES -> {
            val start = LocalDate.now(ZoneId.systemDefault()).minusDays(CALENDAR_SHOWS_BACK_DAYS)
            val items = authed {
                traktApi.getMyShowsCalendar(it, start.format(ISO), CALENDAR_SHOWS_SPAN_DAYS, EXTENDED)
            }?.mapNotNull { dto -> dto.show?.toPreview() }.dedup()
            row(kind, "New Episodes", ContentType.SERIES, items)
        }
        LayoutRowKind.TRAKT_NEW_MOVIES -> {
            val start = LocalDate.now(ZoneId.systemDefault()).minusDays(CALENDAR_MOVIES_BACK_DAYS)
            val items = authed {
                traktApi.getMyMoviesCalendar(it, start.format(ISO), CALENDAR_MOVIES_SPAN_DAYS, EXTENDED)
            }?.mapNotNull { dto -> dto.movie?.toPreview() }.dedup()
            row(kind, "New Movies", ContentType.MOVIE, items)
        }
        else -> null
    }

    private suspend fun <T> authed(call: suspend (String) -> Response<List<T>>): List<T>? {
        val response = traktAuthService.executeAuthorizedRequest(call) ?: return null
        if (!response.isSuccessful) return null
        return response.body().orEmpty()
    }

    private fun List<MetaPreview>?.dedup(): List<MetaPreview> =
        this.orEmpty().distinctBy { "${it.apiType}:${it.id}" }

    private fun row(
        kind: LayoutRowKind,
        title: String,
        type: ContentType,
        items: List<MetaPreview>,
    ): CatalogRow {
        val rawType = if (type == ContentType.MOVIE) "movie" else "series"
        return CatalogRow(
            addonId = "trakt",
            addonName = "Trakt",
            addonBaseUrl = "",
            catalogId = com.nuvio.tv.domain.model.LayoutRowKey.forTraktCatalogKind(kind).orEmpty(),
            catalogName = title,
            type = type,
            rawType = rawType,
            items = items,
            isLoading = false,
            hasMore = false,
        )
    }

    private fun TraktMovieDto.toPreview(): MetaPreview? {
        val name = title?.takeIf { it.isNotBlank() } ?: return null
        val fallback = when {
            ids?.trakt != null -> "trakt:${ids.trakt}"
            !ids?.slug.isNullOrBlank() -> "movie:${ids.slug}"
            else -> null
        }
        val contentId = normalizeContentId(ids, fallback)
        if (contentId.isBlank()) return null
        return MetaPreview(
            id = contentId,
            type = ContentType.MOVIE,
            rawType = "movie",
            name = name,
            poster = images.traktBestPosterUrl(),
            posterShape = PosterShape.POSTER,
            background = images.traktBestBackdropUrl(),
            logo = images.traktBestLogoUrl(),
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = year?.toString() ?: released?.take(4),
            imdbRating = rating?.toFloat(),
            genres = genres.orEmpty(),
            runtime = runtime?.takeIf { it > 0 }?.let { "$it min" },
            status = status,
            ageRating = certification,
            language = languages?.firstOrNull(),
            released = released,
            country = country,
            imdbId = ids?.imdb?.takeIf { it.isNotBlank() },
            slug = ids?.slug?.takeIf { it.isNotBlank() },
            landscapePoster = images.traktBestBackdropUrl(),
            rawPosterUrl = images.traktPosterUrl(),
        )
    }

    private fun TraktShowDto.toPreview(): MetaPreview? {
        val name = title?.takeIf { it.isNotBlank() } ?: return null
        val fallback = when {
            ids?.trakt != null -> "trakt:${ids.trakt}"
            !ids?.slug.isNullOrBlank() -> "series:${ids.slug}"
            else -> null
        }
        val contentId = normalizeContentId(ids, fallback)
        if (contentId.isBlank()) return null
        return MetaPreview(
            id = contentId,
            type = ContentType.SERIES,
            rawType = "series",
            name = name,
            poster = images.traktBestPosterUrl(),
            posterShape = PosterShape.POSTER,
            background = images.traktBestBackdropUrl(),
            logo = images.traktBestLogoUrl(),
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = year?.toString() ?: firstAired?.take(4),
            imdbRating = rating?.toFloat(),
            genres = genres.orEmpty(),
            runtime = runtime?.takeIf { it > 0 }?.let { "$it min" },
            status = status,
            ageRating = certification,
            language = languages?.firstOrNull(),
            released = firstAired,
            country = country,
            imdbId = ids?.imdb?.takeIf { it.isNotBlank() },
            slug = ids?.slug?.takeIf { it.isNotBlank() },
            landscapePoster = images.traktBestBackdropUrl(),
            rawPosterUrl = images.traktPosterUrl(),
        )
    }

    companion object {
        private const val EXTENDED = "full,images"
        private const val LIST_TTL_MS = 60L * 60_000L      // 60 min — watchlist + recommendations
        private const val CALENDAR_TTL_MS = 30L * 60_000L  // 30 min — calendars
        // ±7 days for shows, ±14 for movies (back-window + total span).
        private const val CALENDAR_SHOWS_BACK_DAYS = 7L
        private const val CALENDAR_SHOWS_SPAN_DAYS = 14
        private const val CALENDAR_MOVIES_BACK_DAYS = 14L
        private const val CALENDAR_MOVIES_SPAN_DAYS = 28
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
