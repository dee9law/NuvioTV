# Upstream Releases Audit — 0.6.16-beta → 0.6.18-beta

**Generated:** 2026-05-19
**Fork merge-base:** `68b4a34e` (Merge PR #1779 — fix-sidebar-icon-shift)
**Fork point relative to tags:** between `0.6.15-beta` and `0.6.16-beta`
(merge-base is contained in `0.6.16-beta`, `0.6.17-beta`, `0.6.18-beta` but
not in `0.6.15-beta`).

| Tag | Date | Commits in window |
|-----|------|-------------------|
| `0.6.15-beta` | 2026-05-08 | (fork-base falls just after this tag) |
| `0.6.16-beta` | 2026-05-12 | 48 non-merge commits |
| `0.6.17-beta` | 2026-05-16 | 48 non-merge commits |
| `0.6.18-beta` | 2026-05-18 | 84 non-merge commits |

**Total reviewed:** 180 non-merge commits across 3 release windows.

**Classification key:**
- ✅ Safe = no overlap with our 111 fork-modified files
- ⚠️ Caution = touches a file we modified but a different section
- ❌ High = touches a file we modified substantially in the same area
- ✅ ALREADY PICKED = already in our `dev` branch

---

## Release 0.6.16-beta

| # | Hash | Feature | Type | What Changed | Core or UI | Importance | Conflict Risk | Recommend |
|---|------|---------|------|--------------|------------|------------|---------------|-----------|
| 1 | 5f689b27 | Version bump | New | Bumps `app/build.gradle.kts` versionName/code for the 0.6.16 release. | Core | Low | ⚠️ Caution | Skip — apply manually |
| 2 | d2d3a115 | Trakt i18n round 5 | Fix | Adds `@ApplicationContext` to `TraktRelatedService`, `TraktCommentsService`, `TraktLibraryService`; localizes 4 fallback error strings; adds 3 keys to `strings.xml` + FR. | Core | Low | ✅ Safe | Pick |
| 3 | 3438711a | Repos/player/Torr i18n round 3 | Fix | Threads `Context` into `MetaRepositoryImpl`, `StreamRepositoryImpl`, `TorrentService`, `TorrServerBinary`; localizes addon-failure + torrent-startup fallbacks. | Core | Low | ⚠️ Caution | Skip — localization-only, big surface |
| 4 | d011ab2b | Trakt i18n round 4 | Fix | `TraktCommentsService.toReviewModel` + `StreamRepositoryImpl` Unknown-quality/Unknown-addon fallbacks; Compose fixes. | Core | Low | ✅ Safe | Skip — localization-only |
| 5 | e7122efa | CollectionFolder hide-unreleased | Fix | `TmdbCollectionSourceResolver.toPreview` populates `MetaPreview.released`; `FolderDetailViewModel` applies `filteredForRelease` on every load. | Core | Medium | ✅ Safe | Pick |
| 6 | a3de54ee | Filename in media session | New | `PlayerMediaSessionMetadata` + `PlayerMediaSourceFactory` expose filename to Android media-session metadata. | Core | Low | ✅ Safe | Skip — minor |
| 7 | d75b4cbb | 5 profile support | New | `ProfileManager`, `ProfileSyncService`, `ProfileSelectionScreen/ViewModel`, `ProfileSettingsViewModel` raise profile cap from 4 to 5. | Core | High | ✅ Safe | Pick |
| 8 | 8db89cf4 | Secondary preferred subs + lang matching | New | `PlayerRuntimeControllerObservers` + `…Tracks` add a secondary preferred subtitle stream and bcp47 matching. | Core | Medium | ❌ High | Skip |
| 9 | fcb59bd8 | Restrict forced subs to preferred audio | Fix | `…Observers` + `…Tracks` enable forced subs only when language matches chosen audio. | Core | Medium | ❌ High | Skip |
| 10 | 933c3d64 | Forced sub language match fix | Fix | `PlayerRuntimeControllerTracks.kt` normalizes language tags for forced-sub auto-pick. | Core | Medium | ❌ High | Skip |
| 11 | ed6e1cdd | Still-watching threshold max 6 | New | `PlayerSettingsDataStore` + `PlaybackAutoPlaySettings` raise still-watching episode threshold max 5 → 6. | Core | Low | ❌ High | Skip |
| 12 | 3c599980 | Player unit tests | New | Adds `AutoplaySessionCountTest`, `PostPlayModeTest`, `PostPlayResetRulesTest`, `StillWatchingGatingTest`. | Core | Low | ✅ Safe | Skip — depends on still-watching |
| 13 | 8190d582 | Still-watching → detail w/ hero restore | New | `NuvioNavHost` + `MetaDetailsScreen` route still-watching exit back to detail; 27 string XMLs. | Core | Medium | ❌ High | Skip |
| 14 | 052dd190 | Still-watching prompt state machine | New | Adds `PlayerRuntimeControllerStillWatching.kt`, `PlayerAutoplaySessionRules.kt`; rewires `PlayerRuntimeController`, `Lifecycle`, `Mpv`, `Observers`, `PlayerViewModel`. | Core | High | ✅ Safe | Pick |
| 15 | 4bfc1dc3 | PostPlayMode unification refactor | New | Unifies `NextEpisodeCardOverlay` + `PostPlayOverlay` into a single `PostPlayMode` enum; rewires `PlayerScreen`, `PlayerUiState`, multiple controller files. | Core | Medium | ⚠️ Caution | Pick — prerequisite for #14 |
| 16 | 919576f9 | Still-watching settings persistence | New | `PlayerSettingsDataStore` adds still-watching keys; `PlaybackSettings*` exposes them in UI. | Core | Medium | ❌ High | Pick — required for still-watching |
| 17 | 0d77e24c | Forced subtitle refactor | New | Adds `ForcedSubtitleMode`; rewires forced-sub auto-pick across `PlayerRuntimeController`, `Observers`, `Tracks`, `PlaybackSettings*`. | Core | Medium | ❌ High | Skip |
| 18 | 114fb05f | ExoPlayer resume race fix | Fix | Adds `loadSavedProgressSuspend()` to `PlayerRuntimeController`; called before `initializePlayer` in `Startup`. Fixes buffering hang / 0:00 resume. | Core | Critical | ⚠️ Caution | Pick |
| 19 | 7e3d4953 | DiscoverLocation cross-profile sync | New | `ProfileSettingsSyncService` syncs `DiscoverLocation` enum with legacy-boolean bridge. | Core | Medium | ✅ Safe | Pick |
| 20 | 5ce6f798 | DiscoverLocation datastore | New | New `domain/model/DiscoverLocation.kt`; `LayoutPreferenceDataStore` adds enum persistence + legacy-boolean migration. | Core | High | ❌ High | Pick — careful merge |
| 21 | 4b9350e5 | ExoPlayer teardown order | Fix | `PlayerRuntimeControllerInitialization` + `Lifecycle` drain renderers before detaching the surface. | Core | High | ⚠️ Caution | Pick |
| 22 | 6b4f0a2b | App-wide i18n cleanup (~150 keys) | Fix | Adds Context across many services; `PlaybackException.toDisplayMessage(context)` signature change ripples. | Core | Medium | ⚠️ Caution | Skip — localization-only, very large diff |
| 23 | d4f3ad73 | Harden ExoPlayer audio fallback | Fix | `PlayerRuntimeController`, `ErrorRecovery`, `Initialization`, `Lifecycle` add safer audio-fallback paths. | Core | High | ⚠️ Caution | Pick |
| 24 | dc9cb2f8 | Localized TMDB trailers on folder hero | Fix | `FolderDetailViewModel` fetches `iso_639_1` localized videos for hero trailer. | Core | Low | ✅ Safe | Pick |
| 25 | 925d3028 | Collection crash fix | Fix | `HomeViewModelContinueWatching` + `ModernHomeModels` null-safety around CW collection items; PL string. | UI | High | ❌ High | Skip — reapply manually |
| 26 | 974fd558 | Polish translations | New | `values-pl/strings.xml` only. | UI | Low | ⚠️ Caution | Skip |
| 27 | 3e3bd26a | CW match details logic | Fix | `HomeViewModelContinueWatching` aligns CW display logic with Details page. | UI | Medium | ❌ High | Skip |
| 28 | d6bcae9c | More Polish translations | New | `values-pl/strings.xml`. | UI | Low | ⚠️ Caution | Skip |
| 29 | 2483115b | CW loading regression | Fix | `HomeViewModel` + `HomeViewModelContinueWatching` restore CW loading bypassed by Sort-Mode introduction. | UI | High | ❌ High | Skip — manual port |
| 30 | acd5a8c2 | CW progress polish | Fix | `HomeViewModelContinueWatching` tweaks progress calc to match Details. | UI | Medium | ❌ High | Skip |
| 31 | 3cd0c104 | Turkish translation pass | New | Reorganizes + completes `values-tr/strings.xml`. | UI | Low | ⚠️ Caution | Skip |
| 32 | e62e03d2 | Legacy sidebar icon offset fix | Fix | `MainActivity` corrects legacy sidebar icon offset during expand/collapse. | UI | Medium | ❌ High | Skip — sidebar overlaps with Feel rework |
| 33 | 5ec0006a | Remove duplicate modifier | Fix | `MainActivity` removes a duplicated `Modifier` declaration. | UI | Low | ❌ High | Skip |
| 34 | 61d98675 | Forced subtitle translations | New | 26 locale `strings.xml` files add `subtitle_track_forced` + related keys. | UI | Low | ⚠️ Caution | Skip — localization-only |
| 35 | f6485105 | Czech translation update | New | `values-cs/strings.xml`. | UI | Low | ⚠️ Caution | Skip |
| 36 | d68061b5 | Stray brace fix | Fix | One-character fix in `HomeViewModelContinueWatching`. | UI | Low | ❌ High | Skip |
| 37 | bb77cde5 | Discover-location dialog row | New | `LayoutSettingsScreen` swaps "Show Discover in sidebar" boolean for a 3-option location dialog (off/sidebar/search). | UI | Medium | ❌ High | Pick — required for DiscoverLocation |
| 38 | 97ee6aa9 | Search recent-searches padding | Fix | `SearchScreen` pads "Recent Searches" header to match layouts. | UI | Low | ❌ High | Skip |
| 39 | 39a23fb2 | Discover Location row + memory | New | `LayoutSettingsScreen` + `LayoutSettingsViewModel` add Discover Location row with last-non-off memory. | UI | Medium | ❌ High | Pick — needed with #20/#37/#40 |
| 40 | 99a1b307 | DiscoverLocation gating in shell | New | `MainActivity` + `ModernSidebarBlurPanel` gate sidebar membership/routing/focus on the new enum; new `DrawerFocusRequesters.kt` + test. | UI | High | ❌ High | Skip — reapply manually inside Feel system |
| 41 | d179b69d | Search/Discover use DiscoverLocation | New | `DiscoverScreen`, `SearchScreen`, `SearchUiState`, `SearchViewModel` branch on the new enum. | UI | High | ❌ High | Pick — manual port required |
| 42 | 67ec9b6e | Attributions page | New | New `LicensesAttributionsScreen.kt`; `NuvioNavHost` + `Screen` routes; `AboutScreen` + `SettingsScreen` links; 3 assets; `build.gradle.kts` plugin entry. | UI | Medium | ❌ High | Pick — discrete enough to merge carefully |
| 43 | 0e629295 | Continue-Watching sort mode setting | New | New `ContinueWatchingSortMode.kt`; `LayoutPreferenceDataStore` + `HomeViewModel` + CW + `LayoutSettingsScreen/ViewModel` add Default vs Streaming-Style sort; UTC→local timezone fix. | UI | High | ❌ High | Skip — heavily customized in fork |
| 44 | 4d30a76a | Legacy sidebar collapse animation | Fix | `MainActivity` smooths legacy sidebar icon collapse. | UI | Low | ❌ High | Skip |
| 45 | ed45ef6c | Legacy sidebar offset animation | Fix | `MainActivity` smooths legacy sidebar icon offset on collapse. | UI | Low | ❌ High | Skip |
| 46 | 6210aefd | Sidebar auto-collapse idle timer | Fix | `MainActivity` adds 4s idle-collapse for both Modern home-pill and Legacy drawer. | UI | Medium | ❌ High | Skip — Modern path differs in Feel system |
| 47 | 5c0fc7e0 | Onboarding/account i18n | Fix | `ExperienceModeSelectionScreen`, `AccountViewModel.userFriendlyError`, `EssentialAddonSetupScreen`, `EssentialPlaybackSettingsContent` switched to `stringResource`. | UI | Low | ✅ Safe | Skip — localization-only |
| 48 | 9af6eda7 | Mark previous seasons watched | New | `EpisodesSection` + `MetaDetailsScreen/UiState/ViewModel` add a "Mark previous seasons as watched" affordance. | UI | High | ✅ Safe | Pick |

### 0.6.16-beta summary

48 commits — **24 Core / 24 UI**. **Recommended Pick: 14** (~29%). **Recommended Skip: 34** (~71%). Highest-leverage picks: `114fb05f` (Critical — ExoPlayer resume race), `4b9350e5` + `d4f3ad73` (player teardown/audio-fallback), `052dd190` + `4bfc1dc3` + `919576f9` (still-watching chain), `d75b4cbb` (5-profile support), `9af6eda7` (mark prev seasons watched). Skip drivers: 28-locale XMLs in localization commits, four forced-subtitle commits colliding with our `PlayerRuntimeControllerTracks.kt` customization, seven `HomeViewModelContinueWatching.kt` / `HomeViewModel.kt` CW patches requiring manual port, and every `MainActivity.kt` sidebar polish since our Modern feel hides the SideRail. Suggested apply order for player chain: `4bfc1dc3` → `919576f9` → `052dd190` → `114fb05f` → `4b9350e5` → `d4f3ad73`.

---

## Release 0.6.17-beta

| # | Hash | Feature | Type | What Changed | Core or UI | Importance | Conflict Risk | Recommend |
|---|------|---------|------|--------------|------------|------------|---------------|-----------|
| 1 | 450e1687 | Version bump | New | Bumps `versionName`/`versionCode` in `app/build.gradle.kts`. | Core | Low | ⚠️ Caution | Skip — handle manually at release |
| 2 | 95e299f9 | Native Watch Next via CW pipeline | Fix | Routes `AndroidTvChannelManager`/`AndroidTvChannelSyncService` to `HomeViewModel`'s CW pipeline; renames `RecommendationConstants` keys. | Core | High | ⚠️ Caution | Pick |
| 3 | f9187156 | Auto-forward to next stream | Fix | Sets `playbackEnded = false` in `PlayerRuntimeControllerStreams` before forwarding so the next stream advances. | Core | High | ✅ Safe | Pick |
| 4 | 31c109fd | WebDAV playback fixes | Fix | Patches network/auth handling in `PlayerPlaybackNetworking.kt` for WebDAV streams. | Core | Medium | ✅ Safe | Pick |
| 5 | f8498ccc | Filter out season 0 specials | Fix | Excludes `season == 0` entries in `MetaDetailsViewModel`. | Core | Medium | ✅ Safe | Pick |
| 6 | 49b1d4ed | CW Next-Up thumbnail mismatch | Fix | Adds season+episode equality guard before applying disk-cached enrichment in `HomeViewModelContinueWatching.applyContinueWatchingEnrichmentOverlay`. | Core | High | ❌ High | Skip — port guard manually |
| 7 | f39ebfee | CW cache feeds TV Channel sync | New | `TvChannelRefreshJobService` + `AndroidTvChannelSyncService` consume `ContinueWatchingEnrichmentCache` as channel source. | Core | Medium | ✅ Safe | Pick |
| 8 | 521418e1 | Settings import crash guard | Fix | Wraps cast in `ProfileSettingsSyncService` to prevent `ClassCastException` on profile-settings import. | Core | High | ✅ Safe | Pick |
| 9 | 16d2e68e | Skip placeholder streams for progress | Fix | `PlayerRuntimeControllerPlaybackEvents` ignores tiny/placeholder streams when reporting progress. | Core | Medium | ✅ Safe | Pick |
| 10 | bf7ec953 | Parental guide → imdbapi | New | New `ParentalGuideApi` + `ParentalGuideRepository`; wired through `NetworkModule`; consumed by `PlayerRuntimeControllerMetadata`. | Core | Medium | ✅ Safe | Pick |
| 11 | 1dfa38ad | Forced subtitle matching perf | New | Hoists `normalizedTarget`/`targetName`, replaces `setOf` with booleans, lazy regex in `PlayerSubtitleUtils.kt` (+ `…Tracks`). | Core | Medium | ⚠️ Caution | Pick |
| 12 | 426d9c9a | Respect prefer-binge-group | Fix | `StreamScreenViewModel` honors `preferBingeGroup` when reading persisted binge group. | Core | Medium | ✅ Safe | Pick |
| 13 | 8b530821 | Persist binge group per content ID | New | New `BingeGroupCacheDataStore` plumbed through controller, view-model, screen. | Core | Medium | ✅ Safe | Pick |
| 14 | c3f3aa7e | Hero artwork in Collection view | Fix | `FolderDetailViewModel` no longer returns empty hero when TMDB enabled globally but disabled for modern home. | Core | Medium | ✅ Safe | Pick |
| 15 | 08663af4 | Scored forced subtitle matcher | New | Language normalization/scoring in `PlayerSubtitleUtils` (pt-BR/pt-PT, es-419, zh, fr-CA, nb/nn) plus test; ranks tracks in `…Tracks`. | Core | High | ⚠️ Caution | Pick |
| 16 | d9df6242 | Don't wipe un-pushed local progress | Fix | Failsafe in `StartupSyncService`/`WatchProgressSyncService`/`WatchProgressRepositoryImpl`/`WatchProgressPreferences`. | Core | Critical | ✅ Safe | Pick |
| 17 | d525243c | PosterOptions race + catalog fallback | Fix | Cancels stale `PosterOptionsController.show()` coroutines; `substringBefore(",")` catalog fallback in `CollectionEditorFolderContent`. | Core | High | ✅ Safe | Pick |
| 18 | f34b4744 | Bypass first-frame wait for tunneled | Fix | `PlayerRuntimeControllerInitialization` skips `onRenderedFirstFrame` deferral when tunneled decoding is in use. | Core | High | ⚠️ Caution | Pick |
| 19 | aba43145 | Canonicalize before reading membership | Fix | Moves `canonicalize()` ahead of `isInLibrary`/`isWatched` reads in `PosterOptionsController.show()`. | Core | High | ✅ Safe | Pick |
| 20 | d78872b6 | Long-press library label + catalog | Fix | Resolves library membership via `.first()` synchronously in `PosterOptionsController.show()`; unfiltered `addonCatalogInfoByKey` in `CollectionEditorViewModel`. Fixes #1827/#1615. | Core | High | ✅ Safe | Pick |
| 21 | 41cb6440 | Defer playback to first video frame | Fix | Starts ExoPlayer with `playWhenReady=false`, kicks off in `onRenderedFirstFrame()` to cure A/V desync. | Core | High | ⚠️ Caution | Pick |
| 22 | 35342a9d | Plugin provider repository grouping | New | New repository-group field on `PluginDataStore`; rewires `PluginManager`, `PluginViewModel/UiState/Screen`, `StreamRepositoryImpl`, `StreamScreenViewModel`, `PlayerRuntimeControllerStreams`. | Core | Medium | ⚠️ Caution | Pick |
| 23 | 0899569b | TMDB settings read once per trailer | Fix | `TrailerService` reads settings once and threads `languageOverride` into `getTrailerPlaybackSourceFromTmdbId`. | Core | Low | ✅ Safe | Pick |
| 24 | d3981c22 | Audio pipeline refresh for amplification | Fix | Forces sink refresh in `PlaybackSpeedAwareAudioSink` + `PlayerRuntimeControllerPlaybackEvents`. | Core | Medium | ✅ Safe | Pick |
| 25 | 7580e61b | Honor "Disable Trailers" toggle | Fix | Adds `useTrailers` short-circuit in `TrailerService` and `MetaDetailsViewModel.tryApplyTmdbFallbackMeta`; gate test. Fixes #1647. | Core | High | ✅ Safe | Pick |
| 26 | dd39314e | Audio amplification with passthrough | Fix | `GainAudioProcessor` + `PlaybackSpeedAwareAudioSink` + `…Initialization` + `…PlaybackEvents` re-enable amplification when passthrough is active. | Core | High | ⚠️ Caution | Pick |
| 27 | aec4e6e1 | Faster autoplay stream ordering | New | Reorders providers in `StreamAutoPlaySelector`; adds `StreamAutoPlaySelectorTest`. | Core | Medium | ✅ Safe | Pick |
| 28 | 126a74c0 | TMDB cross-region artwork fix | Fix | `TmdbMetadataService` skips cross-region fallback once a region is resolved. | Core | High | ❌ High | Skip — port logic manually |
| 29 | 3a80c8ce | Back-to-details after episode end | Fix | `NuvioNavHost` + `PlayerScreen` route Back to detail when episode completed. | UI | High | ❌ High | Skip — re-implement on our nav graph |
| 30 | 7a266de7 | Last extended posters focus #1867 | Fix | Adjusts last-row spacing/focus padding in `ModernHomeRows.kt`. | UI | Medium | ❌ High | Skip — port manually |
| 31 | 011ef952 | Buffering indicator perf | New | Extracts `PlayerBufferingIndicator` from `PlayerScreen`; adds `graphicsLayer` to isolate invalidation. | UI | Medium | ✅ Safe | Pick |
| 32 | 1c3514fc | Indonesian in Theme settings | New | Adds Indonesian option to `ThemeSettingsScreen.kt`. | UI | Low | ✅ Safe | Skip — localization-only |
| 33 | e2da7e99 | Indonesian translated strings | New | New `values-in/strings.xml`. | UI | Low | ✅ Safe | Skip — localization-only |
| 34 | 090161f9 | Indonesian locale_config | New | Adds `in` to `xml/locale_config.xml`. | UI | Low | ✅ Safe | Skip — localization-only |
| 35 | 54dcc98d | Generic flag for text-based fallbacks | Fix | `PlayerSubtitleUtils.normalizeLanguage()` appends `generic = true` for fa/he/id/ms/jv/fil/el/ro. | UI | Medium | ✅ Safe | Pick |
| 36 | 4e8847f2 | User-selectable More Like This source | New | New `TraktSettingsDataStore` toggle + `TraktScreen`/`TraktViewModel` UI; consumed in `MetaDetailsViewModel`. | UI | Medium | ⚠️ Caution | Pick |
| 37 | d6a4deeb | Hide Discover header under floating pill | Fix | Threads `showBuiltInHeader` from `NuvioNavHost` into `DiscoverScreen` and `SearchDiscoverSection`. | UI | High | ❌ High | Skip — N/A under Modern feel |
| 38 | 356200ac | Classic-View extended-poster trailers | Fix | Fixes trailer playback wiring in `ClassicHomeContent.kt`. | UI | Medium | ❌ High | Skip — port if we still ship Classic |
| 39 | 8359cc4b | PR template header fix | Fix | `.github/PULL_REQUEST_TEMPLATE.md` text-only. | UI | Low | ✅ Safe | Skip — fork-specific |
| 40 | dd5fe31b | CONTRIBUTING.md update | New | Updates `CONTRIBUTING.md`, PR template, workflow. | UI | Low | ✅ Safe | Skip — fork-specific |
| 41 | 81229188 | Drop duplicate es-419 CW string | Fix | Removes duplicate in `values-b+es+419/strings.xml`. | UI | Low | ⚠️ Caution | Skip — localization cleanup |
| 42 | f4160ff1 | Drop duplicate es-419 layout desc | Fix | Removes duplicate `layout_section_continue_watching_desc`. | UI | Low | ⚠️ Caution | Skip |
| 43 | 8ece1c37 | es-419 localization updates | New | Bulk update to `values-b+es+419/strings.xml`. | UI | Low | ⚠️ Caution | Skip — localization-only |
| 44 | 31d66c49 | Stream sources focus on load | Fix | `StreamSourcesSidePanel` requests focus once source loading completes. | UI | Medium | ✅ Safe | Pick |
| 45 | 96597a0e | Collection delete confirmation + edit focus | New | Confirm dialog and focus restoration in `CollectionEditorFolderContent`, `CollectionEditorScreen`, `CollectionManagementScreen`. | UI | Medium | ⚠️ Caution | Pick |
| 46 | 03225964 | Italian translations | New | Updates `values-it/strings.xml`. | UI | Low | ⚠️ Caution | Skip — localization-only |
| 47 | c44249f9 | pt-BR translation update | New | Updates `values-pt-rBR/strings.xml`. | UI | Low | ⚠️ Caution | Skip — localization-only |
| 48 | ae462c64 | Apply TMDB logo in landscape | Fix | In `ModernHomeRows.ModernCarouselCard`, swaps freeze gate so blank `frozenLogoUrl` adopts TMDB-enriched URL. | UI | High | ❌ High | Skip — port manually |

### 0.6.17-beta summary

48 commits — **28 Core / 20 UI**. **Recommended Pick: 26** (~54%). **Recommended Skip: 22** (~46%). Headline picks: `d9df6242` (Critical — data-loss prevention for un-pushed watch progress), `41cb6440` + `f34b4744` (A/V sync regressions), `7580e61b` (#1647 trailer toggle), `aba43145` + `d78872b6` (PosterOptions correctness), `08663af4` (forced-subtitle scoring). Skip drivers: 8 localization-only commits, 2 fork-specific docs/PR-template, 6 high-conflict commits requiring manual port (`HomeViewModelContinueWatching`, `TmdbMetadataService`, `NuvioNavHost`, `ModernHomeRows`, `ClassicHomeContent`). The Modern-feel-only Discover-header fix (#37) can be dropped entirely since our `ProfileOverlay` replaces the SideRail trigger that produced the duplicate header.

---

## Release 0.6.18-beta

This window's headline feature is the **In-house Debrid Integration**
(a new `core/debrid/` module + companion DataStore + Settings screen +
webserver UI). A secondary thread is the **FFmpeg downmix** audio pipeline
(own Gradle module + decoder AAR + audio sink wiring). A third is the
**playback autoplay timeout** refactor (DataStore migration, predicates,
settings UI). The rest is bug fixes, translations, and small polish.

| # | Hash | Feature | Type | What Changed | Core or UI | Importance | Conflict Risk | Recommend |
|---|------|---------|------|--------------|------------|------------|---------------|-----------|
| 1 | 27398b71 | Version bump | New | `app/build.gradle.kts` versionName bump. | Core | Low | ❌ High | Skip — handle manually |
| 2 | 29fdf992 | Debrid: init streaming | New | 42-file foundation — `core/debrid/DebridProvider`, `DirectDebridResolver`, `DebridStreamFormatter`, `DebridStreamTemplateEngine`, `DirectDebridConfigEncoder`, `DirectDebridStreamFilter`, `DebridSettingsDataStore`, `DebridSettingsScreen`, build deps. | Core | High | ⚠️ Caution | Pick — as bundle (#2–#8) |
| 3 | 82ba5b86 | Debrid: pre-resolve + heuristics | New | Adds `DebridFileSelection`, `DirectDebridStreamPreparer`, `DirectDebridStreamSource`, `RealDebridFileSelector`, `TorboxFileSelector`. | Core | High | ⚠️ Caution | Pick — as bundle |
| 4 | b6b757c8 | Debrid: stream sorting/filtering | New | `DirectDebridStreamFilter`, `DebridFormatterConfigServer`, `DebridFormatterWebPage`, `DebridSettingsViewModel` + sort/filter UI. | Core | High | ⚠️ Caution | Pick — as bundle |
| 5 | 67fdb49e | Debrid: precache | New | `DirectDebridStreamSource` precache + hook in `MetaDetailsViewModel` + unit test. | Core | Medium | ✅ Safe | Pick — as bundle |
| 6 | bd8a170e | Debrid: webserver UI parity | New | Updates `DebridFormatterConfigServer`, `DebridFormatterWebPage`, `DebridSettingsViewModel`. | Core | Medium | ✅ Safe | Pick — as bundle |
| 7 | f540f269 | Debrid: shared preload picker | New | Refactors `DebridSettingsScreen` to share preload picker. | Core | Low | ✅ Safe | Pick — as bundle |
| 8 | ef2df918 | Debrid: minor UI adjustments | New | Tweaks `DebridSettingsScreen` + helper in `SettingsDesignSystem.kt`. | Core | Low | ⚠️ Caution | Pick — as bundle |
| 9 | a78c4bcc | SettingsMultiChoiceDialog refactor | New | Introduces `NuvioDialog` + multi-choice dialog; touches `DirectDebridStreamFilter`, `ModernHomeRows.kt` (ours), `PlayerRuntimeControllerInitialization.kt` (ours), `SettingsDesignSystem.kt` (ours). | Core | Medium | ❌ High | Skip — refactor in our files |
| 10 | f7dfa189 | wip: FFmpeg downmix pipeline | New | 28-file wip on downmix; touches `PlayerSettingsDataStore.kt`, init/tracks, `PlaybackSettingsScreen.kt`. | Core | Low | ❌ High | Skip — wip checkpoint |
| 11 | b23be8d1 | Downmix checkpoint | New | Empty checkpoint commit. | Core | Low | ✅ Safe | Skip — empty |
| 12 | ff573821 | Align FFmpeg downmix with Kodi float | New | Touches `PlayerSettingsDataStore.kt`, `PlayerRuntimeControllerInitialization.kt`, new `PlaybackAudioSettings.kt`. | Core | Medium | ❌ High | Skip — heavy overlap |
| 13 | 755ad920 | Downmix toggle + rename module | New | Renames `ffmpeg-decoder-downmix/`; adds Settings toggle across many files. | Core | Medium | ❌ High | Skip — massive overlap |
| 14 | 12a4c967 | Resample downmix to sink rate | New | `PlayerRuntimeControllerInitialization.kt` + JNI/decoder/renderer. | Core | Medium | ❌ High | Skip — reverted by #15 |
| 15 | 6cda7489 | Revert resample downmix | Removed | Reverts #14. | Core | Low | ❌ High | Skip — pair with #14 |
| 16 | 2da07543 | PCM 16-bit when downmix off | Fix | Updates `ffmpeg-decoder-downmix/` decoder + renderer + JNI + AAR. | Core | Medium | ✅ Safe | Pick — only if downmix module is in |
| 17 | 80d514d2 | Downmix license clarify | New | `ffmpeg-decoder-downmix/NOTICE.md` + comment headers. | Core | Low | ✅ Safe | Pick — with downmix bundle |
| 18 | 1468e24f | Default to patched FFmpeg AAR | New | `build.gradle.kts` + AAR binary + README + `local.example.properties`. | Core | Medium | ❌ High | Skip — gradle conflict |
| 19 | 06fd4dbc | Autoplay timeout value list + sentinel | New | `STREAM_AUTOPLAY_TIMEOUT_VALUES` + sentinel in `PlayerSettingsDataStore.kt` + test. | Core | Medium | ⚠️ Caution | Pick — bundle #19–#25 |
| 20 | c23b9e08 | Snap unknown timeouts to nearest | New | Migration helper in `PlayerSettingsDataStore.kt` + test. | Core | Medium | ⚠️ Caution | Pick — with #19 |
| 21 | 0ed6eb71 | isBoundedTimeout predicate | New | Adds `isBoundedTimeout(...)` + test. | Core | Medium | ⚠️ Caution | Pick — with #19 |
| 22 | 0ccb0937 | Route timeout R/W through helper | New | Refactors callers to use the migration helper. | Core | Medium | ⚠️ Caution | Pick — with #19 |
| 23 | 33ca6112 | Replace magic-11 with isBoundedTimeout | New | Refactors `PlayerRuntimeControllerStreams.kt` + `StreamScreenViewModel.kt`. | Core | Low | ✅ Safe | Pick — with #19 |
| 24 | b65f4e66 | Clamp unknown timeouts to 30 | Fix | `PlayerSettingsDataStore.kt` + test — defensive fix. | Core | High | ⚠️ Caution | Pick — with #19 |
| 25 | f4c6d65a | Test: every timeout value passes through | New | Test-only `PlayerSettingsTimeoutMigrationTest.kt`. | Core | Low | ✅ Safe | Pick — with #19 |
| 26 | 0688ec76 | Push unsynced after pull | Fix | `StartupSyncService.kt`, `WatchedItemsPreferences.kt`, `WatchProgressRepositoryImpl.kt`, `AccountViewModel.kt`. | Core | High | ✅ Safe | Pick |
| 27 | 2e9fec9b | Protect unsynced WatchProgress | Fix | `StartupSyncService.kt`, `WatchedItemsSyncService.kt`, `WatchedItemsPreferences.kt`. | Core | High | ✅ Safe | Pick |
| 28 | ac652084 | Mirror progress to Nuvio Sync w/ Trakt | New | `WatchProgressRepositoryImpl.kt` — dual-write when both enabled. | Core | High | ✅ Safe | Pick |
| 29 | 8b87b092 | Trakt history as CW next-up seeds | Fix | `WatchProgressRepositoryImpl.kt` — accept Trakt entries for next-up. | Core | High | ✅ Safe | Pick |
| 30 | 2ebceaca | Persist binge group + manual mode | Fix | `StreamAutoPlaySelector.kt` + `StreamScreenViewModel.kt`. | Core | Medium | ✅ Safe | Pick |
| 31 | c35b3c44 | Respect addons idPrefixes | Fix | `StreamRepositoryImpl.kt`, `PlayerRuntimeControllerStreams.kt`, `StreamScreenViewModel.kt`. | Core | High | ✅ Safe | Pick |
| 32 | ff78e3bc | Use aggregate_credits for TV | Fix | `TmdbMetadataService.kt` (ours) + `TmdbApi.kt`. | Core | High | ⚠️ Caution | Pick |
| 33 | 8675f4f9 | Fallback for contentLanguage | Fix | `PlayerRuntimeController.kt` + `PlayerRuntimeControllerMetadata.kt`. | Core | Medium | ✅ Safe | Pick |
| 34 | ff3bb232 | Don't rebuild player w/o ASS subs | Fix | `PlayerRuntimeControllerTracks.kt` (ours) — perf fix. | Core | Medium | ⚠️ Caution | Pick |
| 35 | 9a44bf09 | Regional variants in forced subs | Fix | `PlayerRuntimeControllerTracks.kt` (ours) — language-tag normalization. | Core | Medium | ⚠️ Caution | Pick |
| 36 | 18793455 | Revert improved forced-subs matcher | Removed | Reverts a PR — `PlayerRuntimeControllerTracks.kt` (ours) + `PlayerSubtitleUtils.kt` + test. | Core | Medium | ⚠️ Caution | Pick — paired with #35 |
| 37 | f5ae7d2e | Keep amplification from forcing PCM | Fix | `PlaybackSpeedAwareAudioSink.kt` + `PlayerRuntimeControllerInitialization.kt`. | Core | High | ⚠️ Caution | ✅ ALREADY PICKED |
| 38 | c7da4e1e | Restore audio OSD focus after amp | Fix | `AudioSelectionOverlay.kt` + strings. | Core | Medium | ⚠️ Caution | Pick |
| 39 | 42d84aef | Revert audio sink #1763 | Removed | Reverts an audio-sink PR — `GainAudioProcessor.kt`, `PlayerRuntimeController.kt`, `…Initialization.kt` (ours), `Lifecycle.kt`, `PlaybackEvents.kt`. | Core | Medium | ❌ High | Skip — risky on our customized init |
| 40 | fa998b02 | Revert audio sink #1826 | Removed | Reverts another audio-sink PR. | Core | Medium | ❌ High | Skip — pair with #39 |
| 41 | 65b44c74 | Highest quality HLS for trailers | Fix | `TrailerPlayerPool.kt` — enforce max bitrate variant. | Core | Medium | ✅ Safe | Pick |
| 42 | d9531a09 | Fix NextUp URI | Fix | `ProgramBuilder.kt` + `RecommendationConstants.kt` — Android TV Leanback rec URI. | Core | Medium | ✅ Safe | Pick |
| 43 | 748f7fa6 | Correct time-left for Trakt | Fix | `ContinueWatchingProgressLabel.kt` — math fix. | Core | Medium | ✅ Safe | Pick |
| 44 | c4250024 | Update monetizationTypes | New | `TmdbCollectionSourceResolver.kt` — adds watch-provider monetization types. | Core | Low | ✅ Safe | Pick — with #46 |
| 45 | b1d87590 | Fix CEC remote long-press | Fix | `ContentCard.kt`, `ContinueWatchingSection.kt` (ours), `GridContentCard.kt`, `EpisodesSection.kt`, `HeroSection.kt`, `ModernHomeRows.kt` (ours). | Core | High | ❌ High | Skip — too many of our files |
| 46 | ceb4d9c8 | Vote count sort + watch provider filter | New | `AddonConfigServerModels.kt`, `AddonWebPage.kt`, `TmdbCollectionSourceResolver.kt`, `CollectionsDataStore.kt`, `TmdbApi.kt`, `Collection.kt`, `AddonManagerViewModel.kt`, `CollectionEditorControls.kt`. | Core | High | ⚠️ Caution | Pick |
| 47 | ae6380e4 | Initial plan | New | Empty Copilot commit. | Core | Low | ✅ Safe | Skip — empty |
| 48 | d93a9e16 | cleanup | Removed | Repo cleanup of submodule cruft outside `app/`. | Core | Low | ✅ Safe | Skip — irrelevant |
| 49 | 3a736476 | Remove unused plugin imports | Removed | Import cleanup in plugin manager. | Core | Low | ✅ Safe | Pick — cheap |
| 50 | daf4546c | Player exit after CW finish | Fix | `NuvioNavHost.kt` (ours), `PlayerRuntimeControllerInitialization.kt` (ours), `PlayerScreen.kt`. | UI | High | ❌ High | Pick — expect conflicts |
| 51 | 5b2f0819 | Next-episode end overlay | New | `NuvioNavHost.kt` (ours), new `NextEpisodeEndPromptOverlay.kt`, `…Observers.kt`, `PlayerScreen.kt`, `PlayerUiState.kt`. | UI | High | ❌ High | Pick — expect NavHost merge |
| 52 | f3f3683f | Skip Outro + Next Ep coexistence | Fix | `PlayerScreen.kt` + `SkipIntroButton.kt`. | UI | Medium | ✅ Safe | Pick |
| 53 | f8840d57 | Parental Guide setting | New | `PlayerSettingsDataStore.kt` (ours), `WatchProgressPreferences.kt`, `PlayerRuntimeController.kt`, `Metadata.kt`, `Observers.kt`, `PlaybackSettingsScreen.kt` (ours). | UI | High | ❌ High | Pick — expect merges |
| 54 | aefbf510 | Fix sidebar focus skip | Fix | `MainActivity.kt` (ours), `HomeViewModel.kt` (ours), `HomeViewModelPresentationPipeline.kt` (ours). | UI | High | ❌ High | Skip — Feel-system core |
| 55 | d84b8ee3 | Fix Search Screen focus | Fix | `SearchScreen.kt` (ours), `SearchViewModel.kt`. | UI | Medium | ⚠️ Caution | Pick |
| 56 | e9a6c232 | Don't click on shimmer items | Fix | `CatalogRowSection.kt` (ours), `ModernHomeRows.kt` (ours) — `clickable=false` while loading. | UI | Medium | ❌ High | Skip — manual port |
| 57 | dd7a56ce | Remove duplicate imports | Removed | `ContinueWatchingSection.kt` (ours), `HeroCarousel.kt`, `CastSection.kt`, `HomeViewModel.kt` (ours), `ModernHomeContent.kt` (ours), `PlaybackSettingsScreen.kt` (ours) — cosmetic. | UI | Low | ❌ High | Skip — zero behavior change in 4 of our files |
| 58 | db0ed0eb | Experience mode labels | Fix | `NetworkSettingsScreen.kt`, `SettingsScreen.kt` (ours). | UI | Low | ⚠️ Caution | Pick |
| 59 | 07d7c08e | p2p modal consistent width | Fix | `P2pConsentDialog.kt` — width fix. | UI | Medium | ✅ Safe | Pick |
| 60 | 95cb2cf1 | Loading overlay crossfade | New | `LoadingOverlay.kt` — subtle crossfade. | UI | Low | ✅ Safe | Pick |
| 61 | 79a2c762 | Gate Recomposition Highlighter | Fix | `MainActivity.kt` (ours) — disable in non-debug. | UI | Medium | ❌ High | Skip — cosmetic |
| 62 | 026fa3a9 | Shared SettingsSingleChoiceDialog | New | `LayoutSettingsScreen.kt` (ours), `PlaybackAudioSettings.kt`, `PlaybackAutoPlaySettings.kt`, `PlaybackSettingsScreen.kt` (ours), `PlaybackSettingsSections.kt`, `SettingsDesignSystem.kt` (ours). | UI | Medium | ❌ High | Skip — refactor, 3 of our files |
| 63 | e91cecdd | 15/20/25/30s autoplay options | New | `PlaybackAutoPlaySettings.kt`. | UI | Medium | ✅ Safe | Pick — with timeout bundle |
| 64 | 0df664a6 | Discrete-value SliderSettingsItem | New | `PlaybackSettingsScreen.kt` (ours) — adds discrete-value slider overload. | UI | Medium | ⚠️ Caution | Pick — with timeout bundle |
| 65 | 84e9b6f0 | Extract SliderSettingsItemLayout | New | `PlaybackSettingsScreen.kt` (ours) — refactor helper. | UI | Low | ⚠️ Caution | Pick — with timeout bundle |
| 66 | 7e5d4beb | Drop unused extraModifier hook | Removed | `PlaybackSettingsScreen.kt` (ours) — dead-code removal. | UI | Low | ⚠️ Caution | Pick — with timeout bundle |
| 67 | 4199ddac | Tidy timeout KDoc/test docs | Removed | `PlaybackSettingsScreen.kt` (ours) + test. | UI | Low | ⚠️ Caution | Pick — with timeout bundle |
| 68 | 80daab45 | Subtitle colour-picker clip | Fix | `PlaybackSettingsScreen.kt` (ours) — UI clipping fix. | UI | Medium | ⚠️ Caution | ✅ ALREADY PICKED |
| 69 | fa6e3bed | English locales for TMDB | New | `PlayerSettingsDataStore.kt` (ours), `PlaybackSettingsScreen.kt` (ours), `TmdbSettingsScreen.kt` (ours). | UI | Medium | ⚠️ Caution | ✅ ALREADY PICKED |
| 70 | 6876fadf | Update watch provider description | New | `values/strings.xml`. | UI | Low | ⚠️ Caution | Pick — with #46 |
| 71 | 456c05c4 | Fix watch provider helper format | Fix | `values/strings.xml`. | UI | Low | ⚠️ Caution | Pick — with #46 |
| 72 | 4fce41fa | Phrasing change | New | `values/strings.xml`. | UI | Low | ⚠️ Caution | Pick |
| 73 | 995da9bd | SL upload | New | `values-sl/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 74 | 53969b94 | Fix SL duplicates | Fix | `values-sl/strings.xml` (ours). | UI | Low | ⚠️ Caution | Pick — small |
| 75 | ee75aab0 | Greek grammar | Fix | `values-el/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 76 | 90519be8 | Greek Trakt placeholder | Fix | `values-el/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 77 | eff45a09 | Greek remaining strings | New | `values-el/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 78 | 0b1d509d | French 74 missing strings | Fix | `values-fr/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 79 | 0b26720f | Polish missing strings | New | `values-pl/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 80 | 83c3bed6 | Polish wording change | New | `values-pl/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 81 | 91626954 | Turkish missing strings | New | `values-tr/strings.xml` (ours). | UI | Low | ⚠️ Caution | Skip — localization |
| 82 | 083d0a81 | Indonesian update | New | `values-in/strings.xml`. | UI | Low | ✅ Safe | Skip — localization |
| 83 | a90d9626 | Indonesian update | New | `values-in/strings.xml`. | UI | Low | ✅ Safe | Skip — localization |
| 84 | 233f4c02 | Indonesian update | New | `values-in/strings.xml`. | UI | Low | ✅ Safe | Skip — localization |

### 0.6.18-beta summary

84 commits — **49 Core / 35 UI**. **Already picked: 3** (`f5ae7d2e`, `80daab45`, `fa6e3bed`). **Recommended Pick: ~38** (~45%). **Recommended Skip: ~43** (~51%).

**Key bundles to pick together (do not pick partial):**

1. **Debrid bundle (#2–#8):** `29fdf992` → `82ba5b86` → `b6b757c8` → `67fdb49e` → `bd8a170e` → `f540f269` → `ef2df918`. Marquee feature of 0.6.18-beta. The modules cross-import — picking any one alone won't compile (confirmed earlier in this session when isolated picks of `9638b2fa`/`5847e6c9` failed against the missing module). Topological cherry-pick in order.

2. **Autoplay-timeout bundle (#19–#25 + UI #63–#67):** `06fd4dbc` → `c23b9e08` → `0ed6eb71` → `0ccb0937` → `33ca6112` → `b65f4e66` → `f4c6d65a` → `84e9b6f0` → `0df664a6` → `e91cecdd` → `7e5d4beb` → `4199ddac`. All revolve around `PlayerSettingsDataStore.kt` migration + `PlaybackSettingsScreen.kt` slider.

3. **FFmpeg downmix bundle (#10–#18):** Recommend **skipping the entire bundle** unless audio downmix is a priority. Heavy restructuring of `PlayerSettingsDataStore.kt`, `…Initialization.kt`, `PlaybackSettingsScreen.kt`, a new Gradle module, and a shipped AAR binary — porting is days of work.

4. **Sync hardening bundle (#26–#29):** `0688ec76` → `2e9fec9b` → `ac652084` → `8b87b092`. Touches sync/repo files we have **not** modified. Clean picks. High value (data-loss prevention).

**Highest-impact individual picks:** `c35b3c44` (correctness), `daf4546c` (player exit after CW), `5b2f0819` (next-episode end overlay), `f8840d57` (Parental Guide), `ff78e3bc` (TV credits), `ceb4d9c8` (watch-provider filter), `9a44bf09` + `18793455` (forced-subtitle pair).

**Highest-conflict skips:** `aefbf510` (touches all 3 Feel-core files), `b1d87590` (CEC long-press hits CW + ModernHomeRows), `dd7a56ce` + `026fa3a9` (pure refactors touching 3-4 of our files for zero behavior change), `42d84aef` + `fa998b02` (audio-sink reverts on our customized init).

**Localization (13 commits #72–#84):** Recommend skipping all to keep merge load tractable. Back-port in a single dedicated localization pass later.

**Special cases:** `ae6380e4` and `b23be8d1` are empty commits. `27398b71` is version bump — never cherry-pick. `d93a9e16` touches only non-`app/` paths.

---

## Overall Summary — 0.6.16-beta → 0.6.18-beta

| Release | Commits | Core | UI | Pick | Skip | Already Picked |
|---------|---------|------|------|------|------|----------------|
| 0.6.16-beta | 48 | 24 | 24 | 14 | 34 | 0 |
| 0.6.17-beta | 48 | 28 | 20 | 26 | 22 | 0 |
| 0.6.18-beta | 84 | 49 | 35 | 38 | 43 | 3 |
| **Total** | **180** | **101** | **79** | **78** | **99** | **3** |

**Coverage:** ~43% of upstream commits in this 3-release window are recommended Picks; ~55% are recommended Skips; the remaining 3 are already in our HEAD.

**Skip drivers (in rough order of frequency):**
1. Localization-only commits touching one or more of our 28 modified locale XMLs — picking each individually is expensive vs batched localization sync at end.
2. `MainActivity.kt` / `SideRail.kt` / `ProfileOverlay.kt` patches that are now obsolete or in conflict because our Modern feel reworks navigation entirely.
3. `HomeViewModelContinueWatching.kt` / `HomeViewModel.kt` / `ModernHomeRows.kt` continue-watching and rows fixes — heavily customized in our fork, require manual port.
4. `PlayerRuntimeControllerTracks.kt` forced-subtitle commits — we've modified this file.
5. Pure refactors (import dedup, dialog refactor, slider helper) that touch 3-5 of our files for no behavior change.
6. WIP / reverted / empty / docs-only / version-bump commits — never cherry-pick.

**Pick priority tiers (highest-value picks across all 3 releases):**

| Tier | Commits | Rationale |
|------|---------|-----------|
| **Critical** | `114fb05f` (ExoPlayer resume race), `d9df6242` (don't wipe un-pushed progress) | Data-loss prevention and major regression fixes |
| **Bundles** | Debrid (#2–#8), Still-watching chain (`4bfc1dc3` → `919576f9` → `052dd190`), Autoplay-timeout (#19–#25 + #63–#67), Sync hardening (#26–#29) | Multi-commit features that only work picked together |
| **High individual** | `41cb6440` (A/V sync), `f34b4744` (tunneled), `7580e61b` (trailer toggle), `aba43145` + `d78872b6` (PosterOptions), `08663af4` (forced-sub scoring), `c35b3c44` (idPrefixes), `ff78e3bc` (TV credits), `ceb4d9c8` (watch-provider filter), `daf4546c` (player exit), `5b2f0819` (next-ep overlay), `f8840d57` (parental guide), `d75b4cbb` (5 profiles), `9af6eda7` (mark seasons watched), `4b9350e5` + `d4f3ad73` (audio teardown) | High user-visible impact, safe or ⚠️-caution risk |

**Recommended apply order (high-level):**

```
Phase 1 — Critical safety fixes:
  d9df6242  → 114fb05f  → 521418e1  → d525243c

Phase 2 — Sync hardening bundle:
  0688ec76  → 2e9fec9b  → ac652084  → 8b87b092

Phase 3 — Still-watching feature chain:
  4bfc1dc3  → 919576f9  → 052dd190

Phase 4 — Autoplay-timeout bundle (12 commits in order):
  06fd4dbc → c23b9e08 → 0ed6eb71 → 0ccb0937 → 33ca6112
  → b65f4e66 → f4c6d65a → 84e9b6f0 → 0df664a6 → e91cecdd
  → 7e5d4beb → 4199ddac

Phase 5 — Standalone high-value picks (any order):
  41cb6440, f34b4744, 7580e61b, aba43145, d78872b6,
  08663af4, c35b3c44, ff78e3bc, ceb4d9c8, d75b4cbb,
  9af6eda7, 4b9350e5, d4f3ad73, 31c109fd, f8498ccc,
  f9187156, 16d2e68e, bf7ec953, 1dfa38ad, 426d9c9a,
  8b530821, c3f3aa7e, 35342a9d, 0899569b, d3981c22,
  dd39314e, aec4e6e1, ff3bb232, 9a44bf09, 18793455,
  c7da4e1e, 65b44c74, d9531a09, 748f7fa6, 2ebceaca,
  8675f4f9, 3a736476, e7122efa, dc9cb2f8, d2d3a115,
  4e8847f2, 31d66c49, 96597a0e, 011ef952, 54dcc98d,
  07d7c08e, 95cb2cf1, db0ed0eb, 53969b94, f3f3683f,
  4fce41fa

Phase 6 — Discrete features needing manual port:
  daf4546c, 5b2f0819, f8840d57, d55... (Discover Location bundle:
  5ce6f798 → 7e3d4953 → 39a23fb2 → port d179b69d/99a1b307 by hand)
  bb77cde5, 67ec9b6e (attributions page), 6876fadf, 456c05c4

Phase 7 — Debrid bundle (last, isolated):
  29fdf992 → 82ba5b86 → b6b757c8 → 67fdb49e
  → bd8a170e → f540f269 → ef2df918

Phase 8 — Localization sweep (batched at end):
  All locale-only commits picked together with a single
  --strategy-option theirs pass and manual review of values/strings.xml.
```

**Estimated effort:** Phases 1–4 are mostly safe/⚠️-caution and should land in a single focused day. Phase 5 spans many small picks but most are clean; budget half a day. Phase 6 needs careful per-commit attention — half to full day. Phase 7 (debrid) is the largest single payoff but also the riskiest — budget a full day with verification builds between picks 2 and 5. Phase 8 is a cleanup sweep — half day.
