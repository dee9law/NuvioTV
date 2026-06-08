package com.nuvio.tv

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.bitmapFactoryMaxParallelism

import okio.Path.Companion.toOkioPath
import com.nuvio.tv.core.runtime.PluginRuntimeHooks
import com.nuvio.tv.core.server.StreamBadgeServerManager
import com.nuvio.tv.core.sync.StartupSyncService
import com.nuvio.tv.core.sync.androidtv.AndroidTvChannelSyncService
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltAndroidApp
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var startupSyncService: StartupSyncService
    @Inject lateinit var androidTvChannelSyncService: AndroidTvChannelSyncService
    @Inject lateinit var streamBadgeServerManager: StreamBadgeServerManager

    companion object {
        /**
         * Shared cookie jar for CloudStream extension HTTP requests.
         * Accessible so the player's OkHttpClient can share cookies
         * obtained during scraping (e.g., session tokens needed for playback).
         */
        val extensionCookieJar: CookieJar = object : CookieJar {
            private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return store[url.host]?.filter { cookie ->
                    cookie.expiresAt > System.currentTimeMillis()
                } ?: emptyList()
            }

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = store.getOrPut(url.host) { mutableListOf() }
                cookies.forEach { newCookie ->
                    hostCookies.removeAll { it.name == newCookie.name }
                    hostCookies.add(newCookie)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        PluginRuntimeHooks.onApplicationCreate(this)
        androidTvChannelSyncService.start()
        // Fusion badge config "gateway" — bind once for the app's lifetime so the
        // config URL stays reachable from a phone/PC regardless of which screen is open.
        streamBadgeServerManager.start()
        registerChannelForegroundTracking()
        // Load locale synchronously so it's available before Activity.attachBaseContext.
        // SharedPreferences reads are fast (cached in memory after first access).
        val tag = getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        LocaleCache.localeTag = tag ?: ""
    }

    /**
     * Tracks app foreground/background via the started-activity count and forwards it to the
     * Android TV channel sync service. The launcher's Continue Watching channel is only visible
     * while we're backgrounded, so the service reconciles it once on the foreground→background
     * transition rather than thrashing the provider on every in-playback progress update.
     */
    private fun registerChannelForegroundTracking() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    androidTvChannelSyncService.onForegroundChanged(true)
                }
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    androidTvChannelSyncService.onForegroundChanged(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                // Use a lean OkHttpClient for image fetching — no HTTP cache (Coil's own
                // DiskCache handles caching), no cookie jar, no logging interceptors.
                add(
                    coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .build()
                        }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    // 0.45 (was 0.33): this is the foreground TV app — there is no
                    // multitasking competing for RAM, so a larger in-memory bitmap
                    // cache keeps more decoded posters/backdrops/logos resident.
                    // Revisiting a row or scrolling back is then instant instead of
                    // re-decoding from disk. The heavier landscape/cinema backdrops
                    // (a fork-specific cost — upstream only ever loads light posters)
                    // benefit most from staying cached.
                    .maxSizePercent(context, 0.45)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .precision(coil3.size.Precision.INEXACT)
            .allowHardware(true)
            .allowRgb565(true)
            // 4 (was 2) — Coil's own default. When a fresh row/screen of cards
            // appears, the decode throughput doubles so images fill in noticeably
            // faster instead of arriving two-at-a-time. ⚠️ If on-device scroll jank
            // appears on weaker panels, step back to 3.
            .bitmapFactoryMaxParallelism(4)
            .build()
    }
}
