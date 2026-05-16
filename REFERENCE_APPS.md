# Reference Apps

## ARVIO

**Package note:** Despite the "ARVIO" brand name, the app package is `com.arflix.tv`. This applies to all code paths described here.

### 1. Project Structure & Architecture

**Summary:** ARVIO is a single-activity Jetpack Compose app built with Hilt DI and structured in vertical slices (feature modules within the same package). The app supports TV, phone, and tablet form factors via runtime device detection.

**Key files:**
- `ArflixApplication.kt:56-100` — `@HiltAndroidApp` with Coil image loader factory, WorkManager, cloud sync initialization
- `MainActivity.kt:1-120` — Single activity hosting the Compose navigation stack; handles device-mode detection, window insets, and jank monitoring via `JankStats`
- `di/AppModule.kt:1-153` — Provides Retrofit clients for TMDB, Trakt, Supabase, and skip-intro APIs; uses `OkHttpProvider` for shared HTTP client
- `util/DeviceType.kt:1-102` — Runtime device detection (`TV`, `TABLET`, `PHONE`) with override support via SharedPreferences mirror; no-touch detection treats Chinese TVs as TV even on Android TV API misdetection

**Build variants:**
- `play` — Google Play build with self-update disabled
- `sideload` — Direct APK build with GitHub Releases in-app updater enabled
- `debug` / `staging` — Development variants for testing

**Dependency injection:** Hilt with `@Singleton` repositories scoped to app lifetime. `ProfileManager` bridges profile-scoped DataStore keys into per-repository Preferences lookups.

---

### 2. Navigation

**Summary:** Single-activity Navigation Compose with 8 top-level screens (Login, Home, Search, Watchlist, Details, Collections, TV, Settings, Player). Transitions are premium crossfades (Netflix TV style, ~250ms).

**Key files:**
- `navigation/AppNavigation.kt:35-150` — `Screen` sealed class with route generation; `NavHost` with fade transitions; back-stack management uses `saveState=true` for smoother re-entry
- `navigation/AppNavigation.kt:119-137` — `navigateTopLevel` clears back-stack above Home with `restoreState`; prevents stale Details pages from reappearing

**Navigation patterns:**
- **Top-level:** Bottom navigation (phone/tablet) or sidebar + topbar (TV)
- **Deep linking:** Routes include optional query params (`?initialSeason={seasonNumber}` for Details)
- **Back stack:** Custom logic clears Details/Search when navigating to Home/TV to prevent stale data

**Notable:** The app preserves the back-stack entry's composed state across transitions (e.g., Home row scroll position survives a Details page open-and-close).

---

### 3. Home Screen

**Summary:** Premium hero carousel with configurable rows (poster or landscape cards), Continue Watching, and smart refresh on profile/catalog changes.

**Key files:**
- `ui/screens/home/HomeScreen.kt:1-150` — Complex Compose layout with hero metadata animation, focus-driven backdrop blur, and row-level prefetch
- `ui/screens/home/HomeViewModel.kt` — Loads categories, preinstalled catalogs, and custom catalogs; caches categories to disk for instant re-navigation

**Layout patterns:**
- **Hero section:** Large backdrop + title + metadata (rating, duration, year) with animated transitions on focus change
- **Rows:** LazyColumn of LazyRows with configurable card layout (portrait poster or landscape 16:9)
- **Prefetching:** Background logo fetches for all rows (not just first 2) to reduce perceived latency
- **Continue Watching:** Special row with resume progress badge and formatted time-remaining label (`"23min left"`)

**Configuration:** Catalog rows order is user-configurable via Settings; home data is cached to disk so re-launch shows cached categories immediately.

---

### 4. Catalog Rows & Poster Cards

**Summary:** Adaptive card rendering with focus handling, lazy loading, and smart prefetch. Supports both poster (vertical) and landscape (16:9) modes.

**Key files:**
- `ui/components/CardLayoutMode.kt` — Enum for `POSTER` / `LANDSCAPE` with user-toggleable persistence
- Focus handling uses `onFocusChanged { isFocused }` to drive hero metadata updates and backdrop animations
- `util/MediaBadges.kt` — Renders badges (Watched, Continuing, IMAX, Dolby Vision) atop cards

**Image loading:**
- `ArflixApplication.kt:18-59` — Dedicated Coil `ImageLoader` with large disk (256MB) and memory caches; DNS warm-up; SVG + GIF decoders
- Clearlogo overlays positioned bottom-left (not internal gradient) for cleaner look
- Episode progress bars and rank badges (Top 10) stay visible when cards are focused

**Focus behavior on TV:**
- Border and scale animation on focus; focus loss resets smoothly
- Horizontal scroll within row on D-pad left/right; vertical scroll across rows on D-pad up/down
- Focus cropping issues fixed by careful `Modifier.focusable()` + `FocusRequester` placement

---

### 5. Player

**Summary:** Media3 (ExoPlayer 1.9.0+) with TV remote controls, subtitle selection, audio track switching, and AI subtitle tools. Supports mobile swipe-to-seek controls.

**Key files:**
- `ui/screens/player/PlayerScreen.kt` — Complex Compose wrapper around ExoPlayer; handles playback state, source switching, and skip-intro
- `ui/screens/player/PlayerViewModel.kt` — Manages playback session, resume position, Trakt watched-state sync
- `ui/screens/player/SubtitleTranslationManager.kt` / `SubtitleTranslationService.kt` — AI subtitle translation via Groq/Gemini
- `ui/screens/player/AiSubtitleRenderersFactory.kt` — Custom `SubtitleProcessor` for AI-generated subtitles
- `ui/screens/player/SkipIntroButton.kt` — IntroDb + AniSkip + ARM skip detection (anime-aware)

**Subtitle handling:**
- Only the selected subtitle is loaded (not all 30+) to speed startup
- Non-English subtitles matched via normalized language tokens
- Manual selection is not overwritten by defaults
- Offset and style settings persist per-profile
- Spoiler blur support with Android TV 10 fallback

**Stream source selection:**
- Richer source cards with metadata chips (quality, release date, audio, provider)
- Sources pre-sorted by quality and size; higher-quality sources preferred while keeping startup fast (3.5s prefetch window)
- Autoplay fallback if selected source stalls

**Mobile vs TV controls:**
- TV: D-pad navigation, Enter to play/pause, colored menu overlays
- Mobile: Tap to toggle controls, drag-to-seek gesture, landscape fullscreen

---

### 6. Detail Page

**Summary:** Vertical scroll layout (mobile) or hybrid (TV) with backdrop, metadata, cast, episodes (for series), similar items, collections, and action buttons.

**Key files:**
- `ui/screens/details/DetailsScreen.kt:1-100` — Large composable with focus management for episodes and cast rows
- Sections: Overview → Actions (Play, Trailer, Sources, Watchlist) → Episodes/Seasons → Cast → Similar Items → Collections

**Episode list behavior:**
- First unwatched episode focused by default
- Season picker (TV: LazyRow, Mobile: DropdownMenu)
- Watched toggle via context menu; batch season-watch supported

**Focus restoration:** Remembers last-focused episode across navigation cycles to reduce friction.

---

### 7. Settings

**Summary:** Multi-section hierarchy (one screen on phone, multi-tab on TV) covering profiles, accounts, catalogs, IPTV, and playback preferences.

**Key files:**
- `ui/screens/settings/SettingsScreen.kt` — Mobile: single column with sections; TV: tab chips at top
- Profile management: create, edit, PIN protection, avatar selection
- Catalog management: add/remove custom URLs, reorder, discover public Trakt/MDBList lists
- IPTV: add M3U/Xtream playlists, toggle enabled/disabled state, delete
- Playback: subtitle defaults, audio language, auto-play-next, frame-rate matching

**Cloud sync settings:** Toggle ARVIO Cloud, trigger manual sync, account deletion.

---

### 8. Stremio Addons & Catalog Handling

**Summary:** Addons installed via manifest URL; catalogs built from TMDB, Trakt lists, MDBList, and addon sources. Per-profile isolation with shared IPTV/addons.

**Key files:**
- `data/repository/AddonRuntimeAggregator.kt` — Aggregates sources from all installed Stremio addons; parallel async resolution with semaphore throttling
- `data/repository/AddonRuntimeImplementations.kt:1-35` — `StremioAddonRuntime` maps addon manifests to stream resolvers
- `data/repository/CatalogRepository.kt:1-80` — Manages preinstalled catalogs, custom Trakt/MDBList URLs, per-profile visibility, cloud sync

**Addon installation:**
- URL input modal with D-pad navigation (mobile) or keyboard (TV)
- Manifest fetch + validation; instant "Added" feedback with race-condition fixes

**Catalog discovery:**
- "Discover Catalogs" search pulls public Trakt and MDBList lists (v1.9.91+)
- One-click add with state feedback
- Catalog layout controls (portrait/landscape toggle per catalog)

**Per-profile scoping:**
- Catalogs, Trakt connection, history, and watchlist are profile-isolated
- Addons and IPTV are shared across profiles (configurable in future versions)

---

### 9. Live TV / IPTV (13 Files)

**Summary:** M3U and Xtream Codes playlist support with provider categories, favorites, hidden categories, EPG integration, and channel grid UI optimized for 50,000+ channel lists.

**Key files:**
- `ui/screens/tv/live/LiveTvScreen.kt` — Main screen with category sidebar and channel grid
- `ui/screens/tv/live/CategorySidebar.kt` — Collapsible category rail; left-nav focus in/out
- `ui/screens/tv/live/ChannelRow.kt` — Horizontal scrollable row of channels with logo loading
- `ui/screens/tv/live/EpgGrid.kt` — Timeline view of upcoming programs (now/next/future)
- `ui/screens/tv/live/ChannelLogo.kt` — Logo fetching with error fallback
- `ui/screens/tv/live/MiniPlayer.kt` — Small preview player; first click selects channel, second opens fullscreen
- `data/repository/IptvRepository.kt:1-80` — M3U parsing (XmlPull + SAX fallback), Xtream API, GZIP decompression, EPG backfill

**IPTV configuration:**
- DataStore storage with encryption for credentials
- Up to 3 named playlists (enabled/disabled state per-playlist)
- M3U metadata groups (`#EXTINF` attributes) become categories
- "All Channels" auto-grouping for matched channels

**EPG handling:**
- Full backfill at startup (no trickle-in)
- SAX parser with timeout fallback for very large EPG files
- Updates on manual refresh or every ~30min
- Program cell rendering shows current + upcoming programs

**Live stream VOD:** IPTV playlist groups can include VOD movies/series with quality selection.

---

### 10. Home-Server Integrations (Jellyfin / Emby / Plex)

**Summary:** User-owned server support with distinct labels, library browsing, stream URL construction, and per-profile catalog import.

**Key files:**
- `data/repository/HomeServerRepository.kt:1-80` — Connection management, auth, collection discovery, VOD matching
- Enum `HomeServerKind` with `JELLYFIN`, `EMBY`, `PLEX` variants

**Discovery & authentication:**
- Jellyfin: manual URL + username/password
- Emby: manual URL + API key
- Plex: PIN auth via Plex OAuth (v1.9.92+)

**Library browsing:**
- Collections fetched and mapped to ARVIO catalogs
- Collections can appear as custom home-screen rows
- Stream URLs constructed per-server (direct HTTP or via relay)

**Matching:** IMDB ID + title/year matching to resolve between Jellyfin/Emby/Plex and TMDB for metadata enrichment.

---

### 11. Trakt Integration

**Summary:** Two-way watchlist sync, watched-history, progress tracking, and continue-watching derivation with per-profile isolation.

**Key files:**
- `data/repository/TraktRepository.kt` — Watchlist add/remove, history fetch, progress tracking
- `data/repository/TraktSyncService.kt:1-80` — Full sync (Trakt → Supabase), incremental sync (last_activities), outbox for local-first writes
- Auth via device-code flow (TV-friendly); token stored in Supabase

**Watched state sync:**
- Supabase is source of truth; Trakt sync happens in background
- Profile-scoped isolation: per-profile Trakt connections
- Outbox pattern for offline edits (add to watchlist, mark watched → local write → background push)

**Continue watching:** Derived from items with progress > 0s and < total duration; updated in real-time on playback stop.

---

### 12. Cloud Sync (ARVIO Cloud via Supabase)

**Summary:** Optional real-time sync of profiles, settings, catalogs, IPTV state, watch progress, watchlist, and profile avatars across devices.

**Key files:**
- `data/repository/CloudSyncRepository.kt` — Snapshot export/import, per-profile payload maps
- `data/repository/CloudSyncCoordinator.kt` — Manages full sync on login, incremental syncs on profile change
- `data/repository/RealtimeSyncManager.kt` — WebSocket-based real-time updates (auth via JWT)
- Auth via Supabase GoTrue (email + passwordless, Google OAuth, ID tokens)

**Sync payload:** Settings, addons, catalogs, IPTV config + favorites, watchlist, watched state, profile avatars.

**Conflict resolution:** Last-write-wins with timestamp comparison; no 3-way merge.

**Account deletion:** Via `auth.arvio.tv/delete-account` web flow.

---

### 13. Profiles

**Summary:** Multiple independent profiles with isolated settings, catalogs, Trakt connections, and watch state. PIN protection and custom avatars.

**Key files:**
- `data/repository/ProfileManager.kt:1-80` — Profile ID/name caching, synchronous access for cold-start
- `data/repository/ProfileRepository.kt` — CRUD operations on profiles; active profile tracking
- `util/ProfileAvatarFiles.kt` — Avatar image storage and cloud sync

**PIN protection:** Enforced on profile selection; settable in Edit Profile dialog.

**Avatar handling:**
- Local files + cloud sync
- Custom avatars uploaded to Supabase; synced across devices
- Fallback to default avatars if cloud fetch fails

---

### 14. Watchlist & Continue Watching

**Summary:** Local (DataStore) + Trakt + Cloud sync; profile-scoped; continue-watching derived from in-progress items.

**Key files:**
- `data/repository/WatchlistRepository.kt:1-80` — Local watchlist with TMDB enrichment, in-memory cache, semaphore-throttled requests
- Continue Watching preloads on app startup for instant display
- Refresh on profile switch or Trakt sync completion

**Storage:** DataStore for local, Supabase for cloud, Trakt API for two-way sync.

**Stale data cleanup:** Supabase history cleanup, Continue Watching cache purge on login.

---

### 15. Unique Features & Novel Patterns

1. **Device-mode cache via SharedPreferences** — Cold-start TV/phone detection without DataStore I/O stall (50–200ms saved)
   - `util/DeviceType.kt:48-73` — Mirroring DataStore in SharedPreferences for speed

2. **Frame-rate matching** — Real display mode switching via `Display.Mode` API with stabilization polling
   - `updater/AppUpdateRepository.kt` — Also serves as display mode utility

3. **Coil image loader singleton** — Custom ImageLoaderFactory with DNS warm-up, large caches, and no-cache guards for empty URLs
   - `ArflixApplication.kt:18-59`

4. **Skip-intro multi-source detection** — IntroDb + AniSkip + ARM with anime-aware fallback
   - `ui/screens/player/SkipIntroButton.kt`

5. **AI subtitle rendering** — Custom `SubtitleProcessor` for Groq/Gemini-translated subtitles
   - `ui/screens/player/AiSubtitleRenderersFactory.kt`

6. **AI key config server** — Embedded HTTP server for on-device Groq/Gemini API key input via local web page
   - `server/AiKeyConfigServer.kt:1-77` — NanoHTTPD with `/groq`, `/gemini` routes

7. **Live TV EPG backfill at startup** — No trickling; full guide loaded upfront when stale/missing
   - `data/repository/IptvRepository.kt` — SAX + pull-parser with timeout fallback

8. **Source switching during playback** — Hardened flow to avoid black/stuck states; light seek first, then re-prepare
   - `ui/screens/player/PlayerViewModel.kt`

9. **Watchlist two-way sync** — Items added in ARVIO sync to Trakt and vice versa (v1.9.7+)
   - `data/repository/TraktRepository.kt`

10. **Android TV launcher integration** — Publishes Continue Watching to launcher via Watch Next API
    - `data/repository/LauncherContinueWatchingRepository.kt`

---

### 16. Patterns ARVIO Implements That NuvioTV Could Adopt

1. **Profile-scoped DataStore keys via ProfileManager** — Cleaner than passing profile ID everywhere
   - Enables true multi-profile without global state leakage
   - `data/repository/ProfileManager.kt:28-71`

2. **SharedPreferences mirror for cold-start config** — Avoids DataStore I/O stall on main thread
   - Apply to device-mode, language, and other startup-critical settings
   - `util/DeviceType.kt:47-57`

3. **Semaphore-throttled parallel API requests** — Prevents thundering herd of TMDB/addon calls
   - `data/repository/WatchlistRepository.kt:71`

4. **Incremental sync with Trakt last_activities** — Only sync changed items, not entire library
   - `data/repository/TraktSyncService.kt:1-80`

5. **Outbox pattern for offline edits** — Local writes first, background push second
   - Ensures UI responsiveness and tolerates network flakiness
   - `data/repository/TraktOutboxRepository.kt`

6. **Focus-driven hero metadata animation** — Row focus change animates backdrop blur + metadata
   - Much better TV UX than static hero
   - `ui/screens/home/HomeScreen.kt` — onFocusChanged listeners drive Animatable transitions

7. **Custom Compose modifiers for common patterns** — Focus borders, badge overlays, clearlogo positioning
   - Reduces boilerplate across 100+ cards
   - `ui/components/` — Shared `Modifier.ext()` functions

8. **Coil image loader customization** — Large caches, DNS warm-up, SVG + GIF decoders, no-cache guards
   - Measurably reduces perceived latency on low-bandwidth devices
   - `ArflixApplication.kt:56-100`

9. **SAX + pull-parser EPG parsing** — Fallback parser for robustness with very large XML files (50,000+ channels)
   - `data/repository/IptvRepository.kt` — Both parsers side-by-side

10. **Real device-mode override** — Don't force users to one form factor; TV mode on phones, phone mode on TVs
    - `util/DeviceType.kt:59-102` — Override dropdown in Settings

---

### Quick Reference: ARVIO Feature → File

| Feature | Primary File |
|---------|--------------|
| Project init, DI | `di/AppModule.kt` |
| Device detection | `util/DeviceType.kt` |
| Navigation graph | `navigation/AppNavigation.kt` |
| Home screen | `ui/screens/home/HomeScreen.kt` |
| Details page | `ui/screens/details/DetailsScreen.kt` |
| Player + ExoPlayer | `ui/screens/player/PlayerScreen.kt` |
| Live TV + IPTV | `ui/screens/tv/live/LiveTvScreen.kt` |
| Settings | `ui/screens/settings/SettingsScreen.kt` |
| Profiles | `data/repository/ProfileManager.kt` |
| Watchlist | `data/repository/WatchlistRepository.kt` |
| Trakt sync | `data/repository/TraktSyncService.kt` |
| Cloud sync | `data/repository/CloudSyncRepository.kt` |
| Home servers | `data/repository/HomeServerRepository.kt` |
| Catalogs | `data/repository/CatalogRepository.kt` |
| IPTV playlists | `data/repository/IptvRepository.kt` |
| Addons (Stremio) | `data/repository/AddonRuntimeAggregator.kt` |
| Skip intro | `ui/screens/player/SkipIntroButton.kt` |
| AI subtitles | `ui/screens/player/AiSubtitleRenderersFactory.kt` |
| Image loading | `ArflixApplication.kt` (Coil setup) |
| In-app updates | `updater/AppUpdateRepository.kt` |
| AI key config server | `server/AiKeyConfigServer.kt` |

---

### Development Context

- **IDE:** Android Studio (Kotlin 2.0+ with Compose plugin)
- **Min SDK:** 23 (Android 6.0) for Fire TV compatibility
- **Target SDK:** 35 (Android 15)
- **Compose:** Latest with material3 + androidx.tv.material3 for TV
- **Database:** DataStore Preferences (no Room)
- **HTTP:** OkHttp 4.x + Retrofit 2.x with Gson
- **Async:** Coroutines with Hilt for injection
- **Image loading:** Coil with custom cache + decoders
- **Video playback:** Media3 ExoPlayer 1.9.0+
- **Auth:** Supabase GoTrue client with Google OAuth
- **AI subtitles:** Groq API or Gemini (user-configured)
- **Testing:** JUnit + Espresso (not extensively covered in source)

---

**Document generated from ARVIO v1.9.92 source code, built 2026-05-11.**
