package com.nuvio.tv.core.server

import android.content.Context
import com.nuvio.tv.core.streams.StreamBadgeSettings
import com.nuvio.tv.data.local.StreamBadgeSettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped owner of the [StreamBadgeConfigServer] (the Fusion badge "gateway"
 * web config server). The server binds once for the lifetime of the app process
 * — it is NOT tied to the settings screen or the QR overlay — so the config URL
 * stays reachable from a phone/PC as long as the app is running.
 *
 * Started from [com.nuvio.tv.NuvioApplication.onCreate]. The settings screen only
 * reads [serverUrl] to render the QR; it never starts or stops the server.
 */
@Singleton
class StreamBadgeServerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: StreamBadgeSettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var latestSettings: StreamBadgeSettings = StreamBadgeSettings()

    @Volatile
    private var server: StreamBadgeConfigServer? = null

    @Volatile
    private var collecting = false

    /**
     * Idempotent. Starts the settings collector once and binds the config server
     * if it isn't already bound. Socket binding runs off the main thread. Safe to
     * call from app startup and again from the settings screen (e.g. to retry a
     * bind that failed at launch because the network wasn't ready yet).
     */
    @Synchronized
    fun start() {
        if (!collecting) {
            collecting = true
            scope.launch { dataStore.settings.collect { latestSettings = it } }
        }
        if (server == null) {
            scope.launch {
                if (server == null) {
                    server = StreamBadgeConfigServer.startOnAvailablePort(
                        currentSettingsProvider = { latestSettings },
                        onSettingsChanged = { updated -> scope.launch { dataStore.setSettings(updated) } },
                        context = context
                    )
                }
            }
        }
    }

    /** LAN URL of the running config server, or null if not bound / no network. */
    fun serverUrl(): String? {
        val port = server?.listeningPort ?: return null
        val ip = DeviceIpAddress.get(context) ?: return null
        return "http://$ip:$port"
    }
}
