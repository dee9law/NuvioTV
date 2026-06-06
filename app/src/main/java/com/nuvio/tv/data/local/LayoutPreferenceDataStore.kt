package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.LocalHomeCatalogSettingsState
import com.nuvio.tv.core.sync.SyncHomeCatalogPayload
import com.nuvio.tv.core.sync.buildHomeCatalogSyncPayload
import com.nuvio.tv.core.sync.homeCatalogKey
import com.nuvio.tv.core.sync.homeCollectionKey
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.ContinueWatchingSortMode
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.domain.model.Feel
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.usesModernPresentation
import com.nuvio.tv.domain.model.LayoutCardStyle
import com.nuvio.tv.domain.model.LayoutRowConfig
import com.nuvio.tv.domain.model.LayoutRowKind
import com.nuvio.tv.domain.model.LayoutScreenScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LayoutPreferenceDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "layout_settings"
        private const val DEFAULT_POSTER_CARD_WIDTH_DP = 126
        private const val DEFAULT_POSTER_CARD_HEIGHT_DP = 189
        private const val DEFAULT_POSTER_CARD_CORNER_RADIUS_DP = 12
        val DEFAULT_SIDE_RAIL_ORDER = listOf("profile", "search", "home", "discover", "my_stuff", "pill_channels", "settings")
        private const val DEFAULT_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS = 3
        private const val MIN_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS = 0
    }

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    private val gson = Gson()

    private val layoutKey = stringPreferencesKey("selected_layout")
    private val hasChosenKey = booleanPreferencesKey("has_chosen_layout")
    private val heroCatalogKey = stringPreferencesKey("hero_catalog_key")
    private val heroCatalogKeysKey = stringPreferencesKey("hero_catalog_keys")
    private val homeCatalogOrderKeysKey = stringPreferencesKey("home_catalog_order_keys")
    private val disabledHomeCatalogKeysKey = stringPreferencesKey("disabled_home_catalog_keys")
    private val customCatalogTitlesKey = stringPreferencesKey("custom_catalog_titles")
    private val sidebarCollapsedKey = booleanPreferencesKey("sidebar_collapsed_by_default")
    private val modernSidebarEnabledKey = booleanPreferencesKey("modern_sidebar_enabled")
    private val legacyModernSidebarEnabledKey = booleanPreferencesKey("glass_sidepanel_enabled")
    private val modernSidebarBlurEnabledKey = booleanPreferencesKey("modern_sidebar_blur_enabled")
    private val modernLandscapePostersEnabledKey = booleanPreferencesKey("modern_landscape_posters_enabled")
    private val heroSectionEnabledKey = booleanPreferencesKey("hero_section_enabled")
    private val searchDiscoverEnabledKey = booleanPreferencesKey("search_discover_enabled")
    private val sideRailSearchVisibleKey = booleanPreferencesKey("side_rail_search_visible")
    private val sideRailDiscoverVisibleKey = booleanPreferencesKey("side_rail_discover_visible")
    private val sideRailMyStuffVisibleKey = booleanPreferencesKey("side_rail_my_stuff_visible")
    private val sideRailPillChannelsVisibleKey = booleanPreferencesKey("side_rail_pill_channels_visible")
    private val sideRailSettingsVisibleKey = booleanPreferencesKey("side_rail_settings_visible")
    private val sideRailOrderKey = stringPreferencesKey("side_rail_order")
    private val sideRailDisplayModeKey = stringPreferencesKey("side_rail_display_modes")
    private val posterLabelsEnabledKey = booleanPreferencesKey("poster_labels_enabled")
    private val catalogAddonNameEnabledKey = booleanPreferencesKey("catalog_addon_name_enabled")
    private val catalogTypeSuffixEnabledKey = booleanPreferencesKey("catalog_type_suffix_enabled")
    private val classicFocusGradientEnabledKey = booleanPreferencesKey("classic_focus_gradient_enabled")
    private val focusedPosterBackdropExpandEnabledKey = booleanPreferencesKey("focused_poster_backdrop_expand_enabled")
    private val focusedPosterBackdropExpandDelaySecondsKey = intPreferencesKey("focused_poster_backdrop_expand_delay_seconds")
    private val focusedPosterBackdropTrailerEnabledKey = booleanPreferencesKey("focused_poster_backdrop_trailer_enabled")
    private val focusedPosterBackdropTrailerMutedKey = booleanPreferencesKey("focused_poster_backdrop_trailer_muted")
    private val focusedPosterBackdropTrailerPlaybackTargetKey =
        stringPreferencesKey("focused_poster_backdrop_trailer_playback_target")
    private val posterCardWidthDpKey = intPreferencesKey("poster_card_width_dp")
    private val posterCardHeightDpKey = intPreferencesKey("poster_card_height_dp")
    private val posterCardCornerRadiusDpKey = intPreferencesKey("poster_card_corner_radius_dp")
    private val blurUnwatchedEpisodesKey = booleanPreferencesKey("blur_unwatched_episodes")
    private val useEpisodeThumbnailsInCwKey = booleanPreferencesKey("use_episode_thumbnails_in_cw")
    private val showUnairedNextUpKey = booleanPreferencesKey("show_unaired_next_up")
    private val nextUpFromFurthestEpisodeKey = booleanPreferencesKey("next_up_from_furthest_episode")
    private val blurContinueWatchingNextUpKey = booleanPreferencesKey("blur_continue_watching_next_up")
    private val continueWatchingSortModeKey = stringPreferencesKey("continue_watching_sort_mode")
    private val detailPageTrailerButtonEnabledKey = booleanPreferencesKey("detail_page_trailer_button_enabled")
    private val preferExternalMetaAddonDetailKey = booleanPreferencesKey("prefer_external_meta_addon_detail")
    private val modernHeroFullScreenBackdropKey = booleanPreferencesKey("modern_hero_full_screen_backdrop")
    private val hideUnreleasedContentKey = booleanPreferencesKey("hide_unreleased_content")
    private val showFullReleaseDateKey = booleanPreferencesKey("show_full_release_date")
    private val memoryOnlyVerticalScrollKey = booleanPreferencesKey("memory_only_vertical_scroll")
    private val smoothBringIntoViewEnabledKey = booleanPreferencesKey("smooth_bring_into_view_enabled")
    private val fastHorizontalNavigationEnabledKey = booleanPreferencesKey("fast_horizontal_navigation_enabled")
    private val followAddonsOrderKey = booleanPreferencesKey("follow_addons_order")
    private val composeHighlighterEnabledKey = booleanPreferencesKey("compose_highlighter_enabled")
    private val navigationFeelKey = stringPreferencesKey("navigation_feel")
    private val topBarEnabledKey = booleanPreferencesKey("top_bar_enabled")
    private val modernTopBarEnabledKey = booleanPreferencesKey("modern_top_bar_enabled")
    private val posterGlowEnabledKey = booleanPreferencesKey("poster_glow_enabled")
    private val focusHighlightEnabledKey = booleanPreferencesKey("focus_highlight_enabled")
    private val cardFocusStyleKey = stringPreferencesKey("card_focus_style")

    private fun <T> profileFlow(extract: (prefs: androidx.datastore.preferences.core.Preferences) -> T): Flow<T> =
        profileManager.activeProfileId.flatMapLatest { pid ->
            factory.get(pid, FEATURE).data.map { prefs -> extract(prefs) }
        }

    val selectedLayout: Flow<HomeLayout> = profileFlow { prefs ->
        val layoutName = prefs[layoutKey] ?: HomeLayout.MODERN.name
        try {
            HomeLayout.valueOf(layoutName)
        } catch (e: IllegalArgumentException) {
            HomeLayout.MODERN
        }
    }

    val hasChosenLayout: Flow<Boolean> = profileFlow { prefs ->
        prefs[hasChosenKey] ?: false
    }

    val heroCatalogSelections: Flow<List<String>> = profileFlow { prefs ->
        val multiSelection = parseCatalogKeys(prefs[heroCatalogKeysKey])
        if (multiSelection.isNotEmpty()) {
            multiSelection
        } else {
            prefs[heroCatalogKey]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::listOf)
                .orEmpty()
        }
    }

    val heroCatalogSelection: Flow<String?> = heroCatalogSelections.map { selections ->
        selections.firstOrNull()
    }

    val homeCatalogOrderKeys: Flow<List<String>> = profileManager.activeProfileId.flatMapLatest { pid ->
        val profile = profileManager.profiles.value.find { it.id == pid }
        val usePrimary = profile != null && !profile.isPrimary && profile.usesPrimaryAddons
        val effectivePid = if (usePrimary) 1 else pid
        factory.get(effectivePid, FEATURE).data.map { prefs ->
            parseCatalogKeys(prefs[homeCatalogOrderKeysKey])
        }
    }

    val disabledHomeCatalogKeys: Flow<List<String>> = profileManager.activeProfileId.flatMapLatest { pid ->
        val profile = profileManager.profiles.value.find { it.id == pid }
        val usePrimary = profile != null && !profile.isPrimary && profile.usesPrimaryAddons
        val effectivePid = if (usePrimary) 1 else pid
        factory.get(effectivePid, FEATURE).data.map { prefs ->
            parseCatalogKeys(prefs[disabledHomeCatalogKeysKey])
        }
    }

    val customCatalogTitles: Flow<Map<String, String>> = profileManager.activeProfileId.flatMapLatest { pid ->
        val profile = profileManager.profiles.value.find { it.id == pid }
        val usePrimary = profile != null && !profile.isPrimary && profile.usesPrimaryAddons
        val effectivePid = if (usePrimary) 1 else pid
        factory.get(effectivePid, FEATURE).data.map { prefs ->
            parseCustomTitles(prefs[customCatalogTitlesKey])
        }
    }

    val sidebarCollapsedByDefault: Flow<Boolean> = profileFlow { prefs ->
        val modernSidebarEnabled =
            prefs[modernSidebarEnabledKey] ?: prefs[legacyModernSidebarEnabledKey] ?: false
        if (modernSidebarEnabled) {
            false
        } else {
            prefs[sidebarCollapsedKey] ?: false
        }
    }

    val modernSidebarEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[modernSidebarEnabledKey] ?: prefs[legacyModernSidebarEnabledKey] ?: false
    }

    val modernSidebarBlurEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[modernSidebarBlurEnabledKey] ?: false
    }

    val modernLandscapePostersEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[modernLandscapePostersEnabledKey] ?: false
    }

    val modernHeroFullScreenBackdropEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[modernHeroFullScreenBackdropKey] ?: false
    }

    val heroSectionEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[heroSectionEnabledKey] ?: true
    }

    // Preserved alongside the new DiscoverLocation enum: existing Feel/SideRail
    // call sites still read this global boolean. Phase 4 migrates them to
    // discoverLocation; until then we keep the flow + setter live.
    val searchDiscoverEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[searchDiscoverEnabledKey] ?: true
    }

    val discoverLocation: Flow<DiscoverLocation> = profileFlow { prefs ->
        val stored = prefs[discoverLocationKey] ?: DiscoverLocation.IN_SEARCH.name
        runCatching { DiscoverLocation.valueOf(stored) }
            .getOrDefault(DiscoverLocation.IN_SEARCH)
    }

    val lastNonOffDiscoverLocation: Flow<DiscoverLocation> = profileFlow { prefs ->
        val stored = prefs[lastNonOffDiscoverLocationKey]
            ?.takeIf { it != DiscoverLocation.OFF.name }
        val fallback = prefs[discoverLocationKey]
            ?.takeIf { it != DiscoverLocation.OFF.name }
        val source = stored ?: fallback ?: DiscoverLocation.IN_SEARCH.name
        runCatching { DiscoverLocation.valueOf(source) }
            .getOrDefault(DiscoverLocation.IN_SEARCH)
    }

    val sideRailSearchVisible: Flow<Boolean> = profileFlow { prefs -> prefs[sideRailSearchVisibleKey] ?: true }
    val sideRailDiscoverVisible: Flow<Boolean> = profileFlow { prefs -> prefs[sideRailDiscoverVisibleKey] ?: true }
    val sideRailMyStuffVisible: Flow<Boolean> = profileFlow { prefs -> prefs[sideRailMyStuffVisibleKey] ?: true }
    val sideRailPillChannelsVisible: Flow<Boolean> = profileFlow { prefs -> prefs[sideRailPillChannelsVisibleKey] ?: true }
    val sideRailSettingsVisible: Flow<Boolean> = profileFlow { prefs -> prefs[sideRailSettingsVisibleKey] ?: true }

    suspend fun setSideRailItemVisible(item: String, visible: Boolean) {
        val key = when (item) {
            "search" -> sideRailSearchVisibleKey
            "discover" -> sideRailDiscoverVisibleKey
            "my_stuff" -> sideRailMyStuffVisibleKey
            "pill_channels" -> sideRailPillChannelsVisibleKey
            "settings" -> sideRailSettingsVisibleKey
            else -> return
        }
        store().edit { prefs -> prefs[key] = visible }
    }

    val sideRailOrder: Flow<List<String>> = profileFlow { prefs ->
        prefs[sideRailOrderKey]?.split(",")?.filter { it.isNotBlank() }
            ?: DEFAULT_SIDE_RAIL_ORDER
    }

    suspend fun setSideRailOrder(order: List<String>) {
        store().edit { prefs -> prefs[sideRailOrderKey] = order.joinToString(",") }
    }

    val sideRailDisplayModes: Flow<Map<String, String>> = profileFlow { prefs ->
        prefs[sideRailDisplayModeKey]?.split(",")
            ?.mapNotNull { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }?.toMap() ?: emptyMap()
    }

    suspend fun setSideRailDisplayMode(item: String, mode: String) {
        store().edit { prefs ->
            val current = prefs[sideRailDisplayModeKey]?.split(",")
                ?.mapNotNull { e -> val p = e.split("="); if (p.size == 2) p[0] to p[1] else null }
                ?.toMap()?.toMutableMap() ?: mutableMapOf()
            current[item] = mode
            prefs[sideRailDisplayModeKey] = current.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }

    val posterLabelsEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[posterLabelsEnabledKey] ?: true
    }

    val catalogAddonNameEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[catalogAddonNameEnabledKey] ?: true
    }

    val catalogTypeSuffixEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[catalogTypeSuffixEnabledKey] ?: true
    }

    val classicFocusGradientEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[classicFocusGradientEnabledKey] ?: false
    }

    val focusedPosterBackdropExpandEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[focusedPosterBackdropExpandEnabledKey] ?: true
    }

    val focusedPosterBackdropExpandDelaySeconds: Flow<Int> = profileFlow { prefs ->
        (prefs[focusedPosterBackdropExpandDelaySecondsKey]
            ?: DEFAULT_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS)
            .coerceAtLeast(MIN_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS)
    }

    val focusedPosterBackdropTrailerEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[focusedPosterBackdropTrailerEnabledKey] ?: false
    }

    val focusedPosterBackdropTrailerMuted: Flow<Boolean> = profileFlow { prefs ->
        prefs[focusedPosterBackdropTrailerMutedKey] ?: true
    }

    val focusedPosterBackdropTrailerPlaybackTarget: Flow<FocusedPosterTrailerPlaybackTarget> =
        profileFlow { prefs ->
            val stored = prefs[focusedPosterBackdropTrailerPlaybackTargetKey]
                ?: FocusedPosterTrailerPlaybackTarget.HERO_MEDIA.name
            runCatching { FocusedPosterTrailerPlaybackTarget.valueOf(stored) }
                .getOrDefault(FocusedPosterTrailerPlaybackTarget.HERO_MEDIA)
        }

    val posterCardWidthDp: Flow<Int> = profileFlow { prefs ->
        prefs[posterCardWidthDpKey] ?: DEFAULT_POSTER_CARD_WIDTH_DP
    }

    val posterCardHeightDp: Flow<Int> = profileFlow { prefs ->
        prefs[posterCardHeightDpKey] ?: DEFAULT_POSTER_CARD_HEIGHT_DP
    }

    val posterCardCornerRadiusDp: Flow<Int> = profileFlow { prefs ->
        prefs[posterCardCornerRadiusDpKey] ?: DEFAULT_POSTER_CARD_CORNER_RADIUS_DP
    }

    val blurUnwatchedEpisodes: Flow<Boolean> = profileFlow { prefs ->
        prefs[blurUnwatchedEpisodesKey] ?: false
    }

    val useEpisodeThumbnailsInCw: Flow<Boolean> = profileFlow { prefs ->
        prefs[useEpisodeThumbnailsInCwKey] ?: true
    }

    val showUnairedNextUp: Flow<Boolean> = profileFlow { prefs ->
        prefs[showUnairedNextUpKey] ?: true
    }

    val nextUpFromFurthestEpisode: Flow<Boolean> = profileFlow { prefs ->
        prefs[nextUpFromFurthestEpisodeKey] ?: true
    }

    val blurContinueWatchingNextUp: Flow<Boolean> = profileFlow { prefs ->
        prefs[blurContinueWatchingNextUpKey] ?: false
    }

    val continueWatchingSortMode: Flow<ContinueWatchingSortMode> = profileFlow { prefs ->
        val stored = prefs[continueWatchingSortModeKey] ?: ContinueWatchingSortMode.DEFAULT.name
        runCatching { ContinueWatchingSortMode.valueOf(stored) }
            .getOrDefault(ContinueWatchingSortMode.DEFAULT)
    }

    val detailPageTrailerButtonEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[detailPageTrailerButtonEnabledKey] ?: true
    }

    val preferExternalMetaAddonDetail: Flow<Boolean> = profileFlow { prefs ->
        prefs[preferExternalMetaAddonDetailKey] ?: true
    }

    val hideUnreleasedContent: Flow<Boolean> = profileFlow { prefs ->
        prefs[hideUnreleasedContentKey] ?: false
    }

    val showFullReleaseDate: Flow<Boolean> = profileFlow { prefs ->
        prefs[showFullReleaseDateKey] ?: true
    }

    val memoryOnlyVerticalScroll: Flow<Boolean> = profileFlow { prefs ->
        prefs[memoryOnlyVerticalScrollKey] ?: true
    }

    val smoothBringIntoViewEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[smoothBringIntoViewEnabledKey] ?: true
    }

    val fastHorizontalNavigationEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[fastHorizontalNavigationEnabledKey] ?: false
    }

    val followAddonsOrder: Flow<Boolean> = profileFlow { prefs ->
        prefs[followAddonsOrderKey] ?: false
    }

    val composeHighlighterEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[composeHighlighterEnabledKey] ?: false
    }

    /**
     * Selected navigation shell — see [Feel]. Returns [Feel.MODERN] when
     * unset (fresh installs) or when the persisted value is unrecognized.
     */
    val navigationFeel: Flow<Feel> = profileFlow { prefs ->
        Feel.fromStorageValue(prefs[navigationFeelKey])
    }

    suspend fun setNavigationFeel(feel: Feel) {
        store().edit { prefs ->
            prefs[navigationFeelKey] = feel.storageValue
        }
    }

    /**
     * Glassmorphism TopBar opt-in. When `false` (the default) the bar
     * keeps the existing opaque rendering and the hero respects the
     * normal top inset. When `true`, MainActivity composes the bar as a
     * frosted-glass overlay (semi-transparent + RenderEffect blur on
     * API 31+) sitting on top of a hero that runs to y=0.
     */
    val topBarEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[topBarEnabledKey] ?: true
    }

    suspend fun setTopBarEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[topBarEnabledKey] = enabled
        }
    }

    val modernTopBarEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[modernTopBarEnabledKey] ?: false
    }

    suspend fun setModernTopBarEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[modernTopBarEnabledKey] = enabled
        }
    }

    /**
     * Poster Glow opt-in. When `true` (the default) focus on content
     * cards and channel-pill logos triggers a soft glow whose colour is
     * sampled from the artwork itself. When `false` callers fall back
     * to the existing static accent-colour focus highlight.
     */
    val posterGlowEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[posterGlowEnabledKey] ?: true
    }

    suspend fun setPosterGlowEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[posterGlowEnabledKey] = enabled
        }
    }

    /**
     * Master "Focus Highlight" toggle (Theme settings). When off, the Poster
     * Glow + Card Focus Style controls are hidden. Defaults to true.
     */
    val focusHighlightEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[focusHighlightEnabledKey] ?: true
    }

    suspend fun setFocusHighlightEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[focusHighlightEnabledKey] = enabled
        }
    }

    /**
     * Card Focus Style — `accent` / `glow` / `bloom`. Replaces the
     * boolean [posterGlowEnabled] toggle. When unset, returns
     * [com.nuvio.tv.domain.model.CardFocusStyle.ACCENT] (the safe
     * default). The boolean key is kept around as legacy ballast —
     * a future cleanup could migrate `poster_glow_enabled = true`
     * to `card_focus_style = "glow"`.
     */
    val cardFocusStyle: kotlinx.coroutines.flow.Flow<com.nuvio.tv.domain.model.CardFocusStyle> =
        profileFlow { prefs ->
            com.nuvio.tv.domain.model.CardFocusStyle.fromStorageValue(prefs[cardFocusStyleKey])
        }

    suspend fun setCardFocusStyle(style: com.nuvio.tv.domain.model.CardFocusStyle) {
        store().edit { prefs ->
            prefs[cardFocusStyleKey] = style.storageValue
        }
    }

    suspend fun setMemoryOnlyVerticalScroll(enabled: Boolean) {
        store().edit { prefs ->
            prefs[memoryOnlyVerticalScrollKey] = enabled
        }
    }

    suspend fun setSmoothBringIntoViewEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[smoothBringIntoViewEnabledKey] = enabled
        }
    }

    suspend fun setFastHorizontalNavigationEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[fastHorizontalNavigationEnabledKey] = enabled
        }
    }

    suspend fun setFollowAddonsOrder(enabled: Boolean) {
        store().edit { prefs ->
            prefs[followAddonsOrderKey] = enabled
        }
    }

    suspend fun setComposeHighlighterEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[composeHighlighterEnabledKey] = enabled
        }
    }

    suspend fun setLayout(layout: HomeLayout) {
        store().edit { prefs ->
            val hadChosenLayout = prefs[hasChosenKey] ?: false
            prefs[layoutKey] = layout.name
            if (
                layout.usesModernPresentation &&
                !hadChosenLayout &&
                prefs[focusedPosterBackdropTrailerPlaybackTargetKey] == null
            ) {
                prefs[focusedPosterBackdropTrailerPlaybackTargetKey] =
                    FocusedPosterTrailerPlaybackTarget.HERO_MEDIA.name
            }
            prefs[hasChosenKey] = true
        }
    }

    suspend fun setHeroCatalogKeys(catalogKeys: List<String>) {
        val normalizedKeys = normalizeCatalogOrderKeys(catalogKeys)
        store().edit { prefs ->
            if (normalizedKeys.isEmpty()) {
                prefs.remove(heroCatalogKeysKey)
                prefs.remove(heroCatalogKey)
            } else {
                prefs[heroCatalogKeysKey] = gson.toJson(normalizedKeys)
                prefs[heroCatalogKey] = normalizedKeys.first()
            }
        }
    }

    suspend fun setHeroCatalogKey(catalogKey: String) {
        setHeroCatalogKeys(listOf(catalogKey))
    }

    suspend fun setHomeCatalogOrderKeys(keys: List<String>) {
        val normalizedKeys = normalizeCatalogOrderKeys(keys)
        store().edit { prefs ->
            if (normalizedKeys.isEmpty()) {
                prefs.remove(homeCatalogOrderKeysKey)
            } else {
                prefs[homeCatalogOrderKeysKey] = gson.toJson(normalizedKeys)
            }
        }
    }

    suspend fun setDisabledHomeCatalogKeys(keys: List<String>) {
        val normalizedKeys = normalizeCatalogOrderKeys(keys)
        store().edit { prefs ->
            if (normalizedKeys.isEmpty()) {
                prefs.remove(disabledHomeCatalogKeysKey)
            } else {
                prefs[disabledHomeCatalogKeysKey] = gson.toJson(normalizedKeys)
            }
        }
    }

    suspend fun setSidebarCollapsedByDefault(collapsed: Boolean) {
        store().edit { prefs ->
            val modernSidebarEnabled =
                prefs[modernSidebarEnabledKey] ?: prefs[legacyModernSidebarEnabledKey] ?: false
            prefs[sidebarCollapsedKey] = if (modernSidebarEnabled) false else collapsed
        }
    }

    suspend fun setModernSidebarEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[modernSidebarEnabledKey] = enabled
            prefs.remove(legacyModernSidebarEnabledKey)
            if (enabled) {
                prefs[sidebarCollapsedKey] = false
            }
        }
    }

    suspend fun setModernSidebarBlurEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[modernSidebarBlurEnabledKey] = enabled
        }
    }

    suspend fun setModernLandscapePostersEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[modernLandscapePostersEnabledKey] = enabled
        }
    }

    suspend fun setModernHeroFullScreenBackdropEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[modernHeroFullScreenBackdropKey] = enabled
        }
    }

    suspend fun setHeroSectionEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[heroSectionEnabledKey] = enabled
        }
    }

    suspend fun setDiscoverLocation(location: DiscoverLocation) {
        store().edit { prefs ->
            prefs[discoverLocationKey] = location.name
            if (location != DiscoverLocation.OFF) {
                prefs[lastNonOffDiscoverLocationKey] = location.name
            }
            prefs.remove(legacySearchDiscoverEnabledKey)
        }
    }

    suspend fun setSearchDiscoverEnabled(enabled: Boolean) {
        store().edit { it[searchDiscoverEnabledKey] = enabled }
    }

    suspend fun setPosterLabelsEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[posterLabelsEnabledKey] = enabled
        }
    }

    suspend fun setCatalogAddonNameEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[catalogAddonNameEnabledKey] = enabled
        }
    }

    suspend fun setCatalogTypeSuffixEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[catalogTypeSuffixEnabledKey] = enabled
        }
    }

    suspend fun setClassicFocusGradientEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[classicFocusGradientEnabledKey] = enabled
        }
    }

    suspend fun setFocusedPosterBackdropExpandEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[focusedPosterBackdropExpandEnabledKey] = enabled
            if (!enabled) {
                prefs[focusedPosterBackdropTrailerEnabledKey] = false
                prefs[focusedPosterBackdropTrailerMutedKey] = true
            }
        }
    }

    suspend fun setFocusedPosterBackdropExpandDelaySeconds(seconds: Int) {
        store().edit { prefs ->
            prefs[focusedPosterBackdropExpandDelaySecondsKey] =
                seconds.coerceAtLeast(MIN_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS)
        }
    }

    suspend fun setFocusedPosterBackdropTrailerEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[focusedPosterBackdropTrailerEnabledKey] = enabled
            if (!enabled) {
                prefs[focusedPosterBackdropTrailerMutedKey] = true
            }
        }
    }

    suspend fun setFocusedPosterBackdropTrailerMuted(muted: Boolean) {
        store().edit { prefs ->
            prefs[focusedPosterBackdropTrailerMutedKey] = muted
        }
    }

    suspend fun setFocusedPosterBackdropTrailerPlaybackTarget(
        target: FocusedPosterTrailerPlaybackTarget
    ) {
        store().edit { prefs ->
            prefs[focusedPosterBackdropTrailerPlaybackTargetKey] = target.name
        }
    }

    suspend fun setPosterCardWidthDp(widthDp: Int) {
        store().edit { prefs ->
            prefs[posterCardWidthDpKey] = widthDp
        }
    }

    suspend fun setPosterCardHeightDp(heightDp: Int) {
        store().edit { prefs ->
            prefs[posterCardHeightDpKey] = heightDp
        }
    }

    suspend fun setPosterCardCornerRadiusDp(cornerRadiusDp: Int) {
        store().edit { prefs ->
            prefs[posterCardCornerRadiusDpKey] = cornerRadiusDp
        }
    }

    suspend fun setBlurUnwatchedEpisodes(enabled: Boolean) {
        store().edit { prefs ->
            prefs[blurUnwatchedEpisodesKey] = enabled
        }
    }

    suspend fun setUseEpisodeThumbnailsInCw(enabled: Boolean) {
        store().edit { prefs ->
            prefs[useEpisodeThumbnailsInCwKey] = enabled
        }
    }

    suspend fun setShowUnairedNextUp(enabled: Boolean) {
        store().edit { prefs ->
            prefs[showUnairedNextUpKey] = enabled
        }
    }

    suspend fun setNextUpFromFurthestEpisode(enabled: Boolean) {
        store().edit { prefs ->
            prefs[nextUpFromFurthestEpisodeKey] = enabled
        }
    }

    suspend fun setBlurContinueWatchingNextUp(enabled: Boolean) {
        store().edit { prefs ->
            prefs[blurContinueWatchingNextUpKey] = enabled
        }
    }

    suspend fun setContinueWatchingSortMode(mode: ContinueWatchingSortMode) {
        store().edit { prefs ->
            prefs[continueWatchingSortModeKey] = mode.name
        }
    }

    suspend fun setDetailPageTrailerButtonEnabled(enabled: Boolean) {
        store().edit { prefs ->
            prefs[detailPageTrailerButtonEnabledKey] = enabled
        }
    }

    suspend fun setPreferExternalMetaAddonDetail(enabled: Boolean) {
        store().edit { prefs ->
            prefs[preferExternalMetaAddonDetailKey] = enabled
        }
    }

    suspend fun setHideUnreleasedContent(enabled: Boolean) {
        store().edit { prefs ->
            prefs[hideUnreleasedContentKey] = enabled
        }
    }

    suspend fun setShowFullReleaseDate(enabled: Boolean) {
        store().edit { prefs ->
            prefs[showFullReleaseDateKey] = enabled
        }
    }

    private fun parseCatalogKeys(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val parsed = gson.fromJson<List<String>>(json, type).orEmpty()
            normalizeCatalogOrderKeys(parsed)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeCatalogOrderKeys(keys: List<String>): List<String> {
        return keys.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun parseCustomTitles(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun setCustomCatalogTitles(titles: Map<String, String>) {
        store().edit { prefs ->
            val filtered = titles.filterValues { it.isNotBlank() }
            if (filtered.isEmpty()) {
                prefs.remove(customCatalogTitlesKey)
            } else {
                prefs[customCatalogTitlesKey] = gson.toJson(filtered)
            }
        }
    }

    internal suspend fun getHomeCatalogSettingsState(): LocalHomeCatalogSettingsState {
        return readHomeCatalogSettingsState(store().data.first())
    }

    internal suspend fun exportCatalogSettingsToSyncPayload(
        addons: List<Addon>,
        collections: List<Collection>
    ): SyncHomeCatalogPayload {
        return buildHomeCatalogSyncPayload(
            addons = addons,
            collections = collections,
            localState = getHomeCatalogSettingsState()
        )
    }

    suspend fun applyCatalogSettingsFromRemote(payload: SyncHomeCatalogPayload) {
        val sortedItems = payload.items.sortedBy { it.order }
        val orderKeys = sortedItems.map { item ->
            if (item.isCollection) homeCollectionKey(item.collectionId)
            else homeCatalogKey(item.addonId, item.type, item.catalogId)
        }
        val disabledKeys = sortedItems.filter { !it.enabled }.map { item ->
            if (item.isCollection) homeCollectionKey(item.collectionId)
            else homeCatalogKey(item.addonId, item.type, item.catalogId)
        }
        val titles = sortedItems.associate { item ->
            val key = if (item.isCollection) homeCollectionKey(item.collectionId)
            else homeCatalogKey(item.addonId, item.type, item.catalogId)
            key to item.customTitle
        }.filterValues { it.isNotBlank() }

        store().edit { prefs ->
            if (orderKeys.isNotEmpty()) {
                prefs[homeCatalogOrderKeysKey] = gson.toJson(orderKeys)
            } else {
                prefs.remove(homeCatalogOrderKeysKey)
            }
            if (disabledKeys.isNotEmpty()) {
                prefs[disabledHomeCatalogKeysKey] = gson.toJson(disabledKeys)
            } else {
                prefs.remove(disabledHomeCatalogKeysKey)
            }
            if (titles.isNotEmpty()) {
                prefs[customCatalogTitlesKey] = gson.toJson(titles)
            } else {
                prefs.remove(customCatalogTitlesKey)
            }
        }
    }

    private fun readHomeCatalogSettingsState(prefs: Preferences): LocalHomeCatalogSettingsState {
        return LocalHomeCatalogSettingsState(
            orderKeys = parseCatalogKeys(prefs[homeCatalogOrderKeysKey]),
            disabledKeys = parseCatalogKeys(prefs[disabledHomeCatalogKeysKey]).toSet(),
            customTitles = parseCustomTitles(prefs[customCatalogTitlesKey])
        )
    }

    // ── Per-screen layout settings (new "Layout & Rows" screen) ──────────────
    //
    // For HOME, these reuse the existing app-global keys so the new screen
    // stays in sync with the old "Layout & Rows" (now "Old Layout"). For other
    // scopes, dedicated suffixed keys are used.

    private fun scopedLayoutKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) layoutKey
        else stringPreferencesKey("selected_layout_${scope.scopeKey}")

    private fun scopedCornerRadiusKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) posterCardCornerRadiusDpKey
        else intPreferencesKey("poster_card_corner_radius_dp_${scope.scopeKey}")

    private fun scopedFullscreenHeroKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) modernHeroFullScreenBackdropKey
        else booleanPreferencesKey("modern_hero_full_screen_backdrop_${scope.scopeKey}")

    private fun scopedRowsKey(scope: LayoutScreenScope) =
        stringPreferencesKey("layout_rows_${scope.scopeKey}")

    fun selectedLayoutForScope(scope: LayoutScreenScope): Flow<HomeLayout> = profileFlow { prefs ->
        val defaultLayout = when (scope) {
            LayoutScreenScope.MOVIES, LayoutScreenScope.TV -> HomeLayout.GRID
            else -> HomeLayout.MODERN
        }
        val raw = prefs[scopedLayoutKey(scope)] ?: defaultLayout.name
        runCatching { HomeLayout.valueOf(raw) }.getOrDefault(defaultLayout)
    }

    fun posterCardCornerRadiusForScope(scope: LayoutScreenScope): Flow<Int> = profileFlow { prefs ->
        // Corner radius is a single global setting (exposed under Appearance →
        // Global → Card Style). Non-HOME scopes have no dedicated radius UI, so
        // they fall back to the base global key before the hard default — making
        // the one global value apply to every scope's cards.
        prefs[scopedCornerRadiusKey(scope)]
            ?: prefs[posterCardCornerRadiusDpKey]
            ?: DEFAULT_POSTER_CARD_CORNER_RADIUS_DP
    }

    fun fullscreenHeroBackdropForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedFullscreenHeroKey(scope)] ?: false
    }

    fun rowsForScope(scope: LayoutScreenScope): Flow<List<LayoutRowConfig>> = profileFlow { prefs ->
        // Strict per-scope filtering: only return rows whose viewContext
        // matches [scope]. Rows missing viewContext (legacy, pre-view-context)
        // are defaulted to HOME by [toDomain], so they only surface under the
        // HOME pill — preserving backwards compatibility for existing setups.
        parseRows(prefs[scopedRowsKey(scope)]).filter { it.viewContext == scope }
    }

    /**
     * Lookup map keyed by [LayoutRowConfig.id] for callers that need to
     * resolve a single row's overrides without holding the whole list. Used by
     * row renderers (Modern/Classic/Collections) to find each row's cardStyle
     * and cardWidthDp by its canonical row key.
     */
    fun rowConfigsForScope(scope: LayoutScreenScope): Flow<Map<String, LayoutRowConfig>> =
        rowsForScope(scope).map { rows -> rows.associateBy { it.id } }

    private val continueWatchingDefaultSeededKey =
        booleanPreferencesKey("cw_default_row_seeded")

    /**
     * One-shot migration flag: whether the default Continue Watching row has
     * been seeded into the HOME scope. Set once so a user who later deletes the
     * CW row isn't re-seeded on every launch (delete stays sticky; re-add via
     * the "+ CW" button).
     */
    val continueWatchingDefaultSeeded: Flow<Boolean> = profileFlow { prefs ->
        prefs[continueWatchingDefaultSeededKey] ?: false
    }

    suspend fun setContinueWatchingDefaultSeeded(seeded: Boolean) {
        store().edit { prefs -> prefs[continueWatchingDefaultSeededKey] = seeded }
    }

    private val continueWatchingSplitSeededKey =
        booleanPreferencesKey("cw_split_seeded")

    /**
     * One-shot flag for the Series/Movies split. Runs once even for users who
     * already had the pre-split [continueWatchingDefaultSeededKey] set, so the
     * default **Series** row is guaranteed to exist in HOME (and therefore show
     * in the Rows Manager) after upgrading. A later manual delete stays sticky.
     */
    val continueWatchingSplitSeeded: Flow<Boolean> = profileFlow { prefs ->
        prefs[continueWatchingSplitSeededKey] ?: false
    }

    suspend fun setContinueWatchingSplitSeeded(seeded: Boolean) {
        store().edit { prefs -> prefs[continueWatchingSplitSeededKey] = seeded }
    }

    suspend fun setSelectedLayoutForScope(scope: LayoutScreenScope, layout: HomeLayout) {
        if (scope == LayoutScreenScope.HOME) {
            // Reuse the global setter — it also flips hasChosenKey and seeds
            // the trailer playback target, which we want to preserve.
            setLayout(layout)
            return
        }
        store().edit { prefs -> prefs[scopedLayoutKey(scope)] = layout.name }
    }

    suspend fun setPosterCardCornerRadiusForScope(scope: LayoutScreenScope, dp: Int) {
        store().edit { prefs -> prefs[scopedCornerRadiusKey(scope)] = dp }
    }

    suspend fun setFullscreenHeroBackdropForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { prefs -> prefs[scopedFullscreenHeroKey(scope)] = enabled }
    }

    suspend fun setRowsForScope(scope: LayoutScreenScope, rows: List<LayoutRowConfig>) {
        store().edit { prefs ->
            if (rows.isEmpty()) {
                prefs.remove(scopedRowsKey(scope))
            } else {
                prefs[scopedRowsKey(scope)] = gson.toJson(rows.map { it.toSerializable() })
            }
        }
    }

    // ── Per-scope View Options + Focused Poster settings ─────────────────────
    //
    // Each pair below follows the same convention as [scopedLayoutKey]: HOME
    // reuses the existing app-global key (so the Old-Layout screen and the
    // new per-screen pills stay in sync for Home) while every other scope
    // gets a dedicated suffixed key. Read flows return the global default if
    // the per-scope key is unset, so existing user choices migrate naturally
    // to the HOME pill without re-prompting.

    private fun scopedHeroSectionEnabledKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) heroSectionEnabledKey
        else booleanPreferencesKey("hero_section_enabled_${scope.scopeKey}")

    private fun scopedSearchDiscoverEnabledKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) searchDiscoverEnabledKey
        else booleanPreferencesKey("search_discover_enabled_${scope.scopeKey}")

    private fun scopedPosterLabelsEnabledKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) posterLabelsEnabledKey
        else booleanPreferencesKey("poster_labels_enabled_${scope.scopeKey}")

    private fun scopedCatalogAddonNameEnabledKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) catalogAddonNameEnabledKey
        else booleanPreferencesKey("catalog_addon_name_enabled_${scope.scopeKey}")

    private fun scopedCatalogTypeSuffixEnabledKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) catalogTypeSuffixEnabledKey
        else booleanPreferencesKey("catalog_type_suffix_enabled_${scope.scopeKey}")

    private fun scopedHideUnreleasedContentKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) hideUnreleasedContentKey
        else booleanPreferencesKey("hide_unreleased_content_${scope.scopeKey}")

    private fun scopedFocusedPosterBackdropExpandEnabledKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) focusedPosterBackdropExpandEnabledKey
        else booleanPreferencesKey("focused_poster_backdrop_expand_enabled_${scope.scopeKey}")

    private fun scopedFocusedPosterBackdropExpandDelaySecondsKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) focusedPosterBackdropExpandDelaySecondsKey
        else intPreferencesKey("focused_poster_backdrop_expand_delay_seconds_${scope.scopeKey}")

    private fun scopedFocusedPosterBackdropTrailerMutedKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) focusedPosterBackdropTrailerMutedKey
        else booleanPreferencesKey("focused_poster_backdrop_trailer_muted_${scope.scopeKey}")

    fun heroSectionEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedHeroSectionEnabledKey(scope)] ?: true
    }

    fun searchDiscoverEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedSearchDiscoverEnabledKey(scope)] ?: true
    }

    fun posterLabelsEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedPosterLabelsEnabledKey(scope)] ?: true
    }

    fun catalogAddonNameEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedCatalogAddonNameEnabledKey(scope)] ?: true
    }

    fun catalogTypeSuffixEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedCatalogTypeSuffixEnabledKey(scope)] ?: true
    }

    fun hideUnreleasedContentForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedHideUnreleasedContentKey(scope)] ?: false
    }

    fun focusedPosterBackdropExpandEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedFocusedPosterBackdropExpandEnabledKey(scope)] ?: true
    }

    fun focusedPosterBackdropExpandDelaySecondsForScope(scope: LayoutScreenScope): Flow<Int> = profileFlow { prefs ->
        (prefs[scopedFocusedPosterBackdropExpandDelaySecondsKey(scope)]
            ?: DEFAULT_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS)
            .coerceAtLeast(MIN_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS)
    }

    fun focusedPosterBackdropTrailerMutedForScope(scope: LayoutScreenScope): Flow<Boolean> = profileFlow { prefs ->
        prefs[scopedFocusedPosterBackdropTrailerMutedKey(scope)] ?: true
    }

    suspend fun setHeroSectionEnabledForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedHeroSectionEnabledKey(scope)] = enabled }
    }

    suspend fun setSearchDiscoverEnabledForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedSearchDiscoverEnabledKey(scope)] = enabled }
    }

    suspend fun setPosterLabelsEnabledForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedPosterLabelsEnabledKey(scope)] = enabled }
    }

    suspend fun setCatalogAddonNameEnabledForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedCatalogAddonNameEnabledKey(scope)] = enabled }
    }

    suspend fun setCatalogTypeSuffixEnabledForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedCatalogTypeSuffixEnabledKey(scope)] = enabled }
    }

    suspend fun setHideUnreleasedContentForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedHideUnreleasedContentKey(scope)] = enabled }
    }

    suspend fun setFocusedPosterBackdropExpandEnabledForScope(
        scope: LayoutScreenScope,
        enabled: Boolean,
    ) {
        store().edit { prefs ->
            prefs[scopedFocusedPosterBackdropExpandEnabledKey(scope)] = enabled
            if (!enabled) {
                prefs[scopedFocusedPosterBackdropTrailerMutedKey(scope)] = true
            }
        }
    }

    suspend fun setFocusedPosterBackdropExpandDelaySecondsForScope(
        scope: LayoutScreenScope,
        seconds: Int,
    ) {
        store().edit { prefs ->
            prefs[scopedFocusedPosterBackdropExpandDelaySecondsKey(scope)] =
                seconds.coerceAtLeast(MIN_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS)
        }
    }

    suspend fun setFocusedPosterBackdropTrailerMutedForScope(
        scope: LayoutScreenScope,
        muted: Boolean,
    ) {
        store().edit { it[scopedFocusedPosterBackdropTrailerMutedKey(scope)] = muted }
    }

    // ── Per-scope hero catalogs + classic focus gradient ─────────────────────
    //
    // Two more settings that the simplified Layout screen needs per-pill:
    //  - hero_catalog_keys_<scope>  → which catalogs feed the Hero strip
    //  - classic_focus_gradient_enabled_<scope>  → classic-only artwork blend
    // HOME aliases the existing global keys so legacy users see no jump.

    private fun scopedHeroCatalogKeysKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) heroCatalogKeysKey
        else stringPreferencesKey("hero_catalog_keys_${scope.scopeKey}")

    private fun scopedClassicFocusGradientKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) classicFocusGradientEnabledKey
        else booleanPreferencesKey("classic_focus_gradient_enabled_${scope.scopeKey}")

    fun heroCatalogSelectionsForScope(scope: LayoutScreenScope): Flow<List<String>> =
        profileFlow { prefs -> parseCatalogKeys(prefs[scopedHeroCatalogKeysKey(scope)]) }

    fun classicFocusGradientEnabledForScope(scope: LayoutScreenScope): Flow<Boolean> =
        profileFlow { prefs -> prefs[scopedClassicFocusGradientKey(scope)] ?: false }

    // ── Per-scope "Follow addons order" toggle (Task 4) ──────────────────────
    //
    // Lives next to the Rows list in Appearance → Rows. When ON, the row
    // arrangement for that scope is derived from the installed addons'
    // manifest order; the user's manual reordering on that scope is
    // overridden. HOME reuses the global `followAddonsOrderKey` so existing
    // installs keep their saved choice; other scopes get suffixed keys.
    private fun scopedFollowAddonsOrderKey(scope: LayoutScreenScope) =
        if (scope == LayoutScreenScope.HOME) followAddonsOrderKey
        else booleanPreferencesKey("follow_addons_order_${scope.scopeKey}")

    fun followAddonsOrderForScope(scope: LayoutScreenScope): Flow<Boolean> =
        profileFlow { prefs -> prefs[scopedFollowAddonsOrderKey(scope)] ?: false }

    suspend fun setFollowAddonsOrderForScope(scope: LayoutScreenScope, enabled: Boolean) {
        store().edit { it[scopedFollowAddonsOrderKey(scope)] = enabled }
    }


    suspend fun setHeroCatalogKeysForScope(scope: LayoutScreenScope, catalogKeys: List<String>) {
        val normalizedKeys = normalizeCatalogOrderKeys(catalogKeys)
        if (scope == LayoutScreenScope.HOME) {
            // HOME also writes the legacy single-key alias for backwards-compat
            // (callers that still read `heroCatalogKey` should not regress).
            setHeroCatalogKeys(normalizedKeys)
            return
        }
        store().edit { prefs ->
            if (normalizedKeys.isEmpty()) {
                prefs.remove(scopedHeroCatalogKeysKey(scope))
            } else {
                prefs[scopedHeroCatalogKeysKey(scope)] = gson.toJson(normalizedKeys)
            }
        }
    }

    suspend fun setClassicFocusGradientEnabledForScope(
        scope: LayoutScreenScope,
        enabled: Boolean,
    ) {
        store().edit { it[scopedClassicFocusGradientKey(scope)] = enabled }
    }

    // ── Global fallback layout settings (Settings → Appearance → Global) ─────
    //
    // These supply the floor for the per-row / per-screen / global resolution
    // hierarchy. A per-screen value (e.g. `selectedLayoutForScope(MOVIES)`)
    // takes precedence; if missing, the global value applies.

    private val globalLayoutKey = stringPreferencesKey("global_selected_layout")
    private val globalCardStyleKey = stringPreferencesKey("global_card_style")
    private val globalFocusedPosterTrailerEnabledKey =
        booleanPreferencesKey("global_focused_poster_trailer_enabled")
    private val globalFocusedPosterTrailerTargetKey =
        stringPreferencesKey("global_focused_poster_trailer_target")

    val globalLayout: Flow<HomeLayout> = profileFlow { prefs ->
        val raw = prefs[globalLayoutKey] ?: HomeLayout.MODERN.name
        runCatching { HomeLayout.valueOf(raw) }.getOrDefault(HomeLayout.MODERN)
    }

    val globalCardStyle: Flow<LayoutCardStyle> = profileFlow { prefs ->
        val raw = prefs[globalCardStyleKey] ?: LayoutCardStyle.POSTER.name
        runCatching { LayoutCardStyle.valueOf(raw) }.getOrDefault(LayoutCardStyle.POSTER)
    }

    val globalFocusedPosterTrailerEnabled: Flow<Boolean> = profileFlow { prefs ->
        prefs[globalFocusedPosterTrailerEnabledKey] ?: false
    }

    val globalFocusedPosterTrailerTarget: Flow<FocusedPosterTrailerPlaybackTarget> = profileFlow { prefs ->
        val raw = prefs[globalFocusedPosterTrailerTargetKey]
            ?: FocusedPosterTrailerPlaybackTarget.HERO_MEDIA.name
        runCatching { FocusedPosterTrailerPlaybackTarget.valueOf(raw) }
            .getOrDefault(FocusedPosterTrailerPlaybackTarget.HERO_MEDIA)
    }

    suspend fun setGlobalLayout(layout: HomeLayout) {
        store().edit { it[globalLayoutKey] = layout.name }
    }

    suspend fun setGlobalCardStyle(style: LayoutCardStyle) {
        store().edit { it[globalCardStyleKey] = style.name }
    }

    suspend fun setGlobalFocusedPosterTrailerEnabled(enabled: Boolean) {
        store().edit { it[globalFocusedPosterTrailerEnabledKey] = enabled }
    }

    suspend fun setGlobalFocusedPosterTrailerTarget(target: FocusedPosterTrailerPlaybackTarget) {
        store().edit { it[globalFocusedPosterTrailerTargetKey] = target.name }
    }

    // ── Per-scope focused-poster settings ────────────────────────────────────
    //
    // Two knobs per scope: trailer-enabled (all scopes) and trailer-target
    // (HOME / DETAIL only — other scopes have no Hero Media surface). Both
    // are nullable in `*Flow` form so the resolver hierarchy can fall back to
    // the global tier when the user hasn't overridden per-screen.

    private fun scopedFocusedPosterTrailerEnabledKey(scope: LayoutScreenScope) =
        booleanPreferencesKey("focused_poster_trailer_enabled_${scope.scopeKey}")

    private fun scopedFocusedPosterTrailerTargetKey(scope: LayoutScreenScope) =
        stringPreferencesKey("focused_poster_trailer_target_${scope.scopeKey}")

    fun focusedPosterTrailerEnabledForScope(scope: LayoutScreenScope): Flow<Boolean?> =
        profileFlow { prefs -> prefs[scopedFocusedPosterTrailerEnabledKey(scope)] }

    fun focusedPosterTrailerTargetForScope(
        scope: LayoutScreenScope,
    ): Flow<FocusedPosterTrailerPlaybackTarget?> = profileFlow { prefs ->
        prefs[scopedFocusedPosterTrailerTargetKey(scope)]?.let { raw ->
            runCatching { FocusedPosterTrailerPlaybackTarget.valueOf(raw) }.getOrNull()
        }
    }

    suspend fun setFocusedPosterTrailerEnabledForScope(
        scope: LayoutScreenScope,
        enabled: Boolean,
    ) {
        store().edit { it[scopedFocusedPosterTrailerEnabledKey(scope)] = enabled }
    }

    suspend fun setFocusedPosterTrailerTargetForScope(
        scope: LayoutScreenScope,
        target: FocusedPosterTrailerPlaybackTarget,
    ) {
        store().edit { it[scopedFocusedPosterTrailerTargetKey(scope)] = target.name }
    }

    @androidx.annotation.Keep
    private data class SerializableLayoutRow(
        val id: String,
        val kind: String,
        val name: String,
        val cardStyle: String = LayoutCardStyle.POSTER.name,
        val cardWidthDp: Int = DEFAULT_POSTER_CARD_WIDTH_DP,
        val enabled: Boolean = true,
        // Nullable so we can detect legacy rows (pre-view-context) on read
        // and default them to HOME for backward compatibility.
        val viewContext: String? = null,
        val metadata: Map<String, String>? = null,
        // Per-row expand override. Null (absent in legacy JSON) → follow the
        // per-scope global expand setting. See [LayoutRowConfig.expandEnabled].
        val expandEnabled: Boolean? = null,
    )

    private fun LayoutRowConfig.toSerializable() = SerializableLayoutRow(
        id = id,
        kind = kind.name,
        name = name,
        cardStyle = cardStyle.name,
        cardWidthDp = cardWidthDp,
        enabled = enabled,
        viewContext = viewContext.name,
        metadata = metadata.takeIf { it.isNotEmpty() },
        expandEnabled = expandEnabled,
    )

    private fun SerializableLayoutRow.toDomain() = LayoutRowConfig(
        id = id,
        kind = runCatching { LayoutRowKind.valueOf(kind) }.getOrDefault(LayoutRowKind.ADDON),
        name = name,
        cardStyle = runCatching { LayoutCardStyle.valueOf(cardStyle) }
            .getOrDefault(LayoutCardStyle.POSTER),
        cardWidthDp = cardWidthDp,
        enabled = enabled,
        viewContext = viewContext
            ?.let { raw -> runCatching { LayoutScreenScope.valueOf(raw) }.getOrNull() }
            ?: LayoutScreenScope.HOME,
        metadata = metadata.orEmpty(),
        expandEnabled = expandEnabled,
    )

    private fun parseRows(json: String?): List<LayoutRowConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SerializableLayoutRow>>() {}.type
            gson.fromJson<List<SerializableLayoutRow>>(json, type).orEmpty().map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

internal val legacySearchDiscoverEnabledKey = booleanPreferencesKey("search_discover_enabled")
internal val discoverLocationKey = stringPreferencesKey("discover_location")
internal val lastNonOffDiscoverLocationKey = stringPreferencesKey("last_non_off_discover_location")
