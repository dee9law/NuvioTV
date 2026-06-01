package com.nuvio.tv.core.sync.androidtv

import android.content.Context
import android.util.Log
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TvChannelSync"
private const val DEBOUNCE_MS = 300L
private const val MAX_CHANNEL_ROWS = 20

/**
 * Keeps the Android TV "Continue Watching" preview channel in sync with the app's
 * in-progress content. No-op on non-leanback (phone/tablet) devices.
 *
 * The Flow-collector approach lets both the Trakt and Nuvio-sync progress backends
 * feed into a single reconcile call without requiring hooks in each write path.
 */
@Singleton
class AndroidTvChannelSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: AndroidTvChannelManager,
    private val watchProgressRepository: WatchProgressRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The launcher channel is only visible while the app is in the background. Reconciling on
    // every in-playback progress emission (~10s during playback) thrashes the TvProvider and
    // stops launchers like Projectivy from repainting. We instead reconcile once when the app
    // goes to background (user returns to the launcher) — see [onForegroundChanged] — plus once
    // on startup and on the periodic job.
    @Volatile private var appInForeground = false
    @Volatile private var latestItems: List<WatchProgress> = emptyList()
    @Volatile private var hasPopulatedOnce = false

    /** Called from the Application's activity-lifecycle callbacks. On the foreground→background
     *  transition we reconcile once so the channel reflects the latest watch progress exactly as
     *  the launcher regains foreground. */
    fun onForegroundChanged(foreground: Boolean) {
        val wasForeground = appInForeground
        appInForeground = foreground
        if (wasForeground && !foreground) {
            scope.launch { manager.reconcile(latestItems) }
        }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (!manager.isSupported()) {
            Log.d(TAG, "Non-leanback device; channel sync skipped")
            return
        }
        TvChannelRefreshJobService.schedulePeriodic(context)
        scope.launch {
            watchProgressRepository.continueWatching
                // Skip the leading empty emitted by traktAllProgressFlow's onStart; any
                // subsequent empty is a real removal and should reconcile (clear) the channel.
                .dropWhile { it.isEmpty() }
                .debounce(DEBOUNCE_MS)
                // No distinctUntilChanged — reconcile on every emission so metadata-enriched
                // artwork and new Trakt entries appear as soon as the Flow re-emits.
                .collect { items ->
                    val capped = items.take(MAX_CHANNEL_ROWS)
                    latestItems = capped
                    // Always populate once on startup so the channel isn't empty until the first
                    // background transition. After that, skip while foregrounded — the background
                    // transition (onForegroundChanged) + periodic job keep it fresh.
                    if (appInForeground && hasPopulatedOnce) return@collect
                    hasPopulatedOnce = true
                    Log.d(TAG, "Reconciling ${capped.size} items: ${capped.take(5).map { "${it.contentId} pct=${it.progressPercent} pos=${it.position} dur=${it.duration}" }}")
                    manager.reconcile(capped)
                }
        }
    }
}
