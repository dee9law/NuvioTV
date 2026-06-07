# NuvioTV — Architecture Reference

> Detailed architecture notes, key-file references, layout/pipeline/enum
> descriptions. Consult this when working on a subsystem. High-level rules
> and current state live in [`CLAUDE.md`](./CLAUDE.md); full session history
> in [`SESSION_HISTORY.md`](./SESSION_HISTORY.md).

---

## 🚦 Feel — Dual Navigation System (2026-05-18)

Two completely separate navigation shells controlled by a single per-profile
DataStore key (`navigation_feel` in `LayoutPreferenceDataStore`). Switching is
live — no restart.

### Feel.MODERN — default for fresh installs
- SideRail **hidden entirely** (gated, not deleted). Content runs edge-to-edge.
- TopBar: **profile avatar (far left)** → **category pills** (icon-on-top,
  text-below; reorderable; per-pill icons-only toggle) → **channel pills**
  (Pill Channels, scroll to right screen edge).
- **Profile Overlay** (glassmorphism panel from top-left) hosts Search /
  Discover / My Stuff / Settings / Pill Channels + any demoted category pills
  (Hidden Items).
- D-pad Left at content carousel index 0 → opens Profile Overlay (reuses
  `LocalSideRailController`). **Note:** as of 2026-05-31 D-pad Left from the
  leftmost poster hard-stops in Modern (`MainActivity.kt` provides `null`
  controller); overlay reachable via avatar Select.
- Long-press on Home/Movies/TV Shows → **Edit Mode** (Collections long-press
  still opens `CollectionsDropdown`).
- Loop: avatar ↔ first category ↔ last channel ↔ avatar.

### Feel.LEGACY — preserved verbatim
- Prime-Video–style SideRail: Profile → Search → Home → My Stuff →
  **Pill Channels** → Settings.
- TopBar with side-by-side icon+text category pills + channel pills.
- Focus chains, immersion mode, Back Level-Up hierarchy unchanged.
- Loop: first category ↔ last channel ↔ first category.
- No Edit Mode, no Profile Overlay.

### Gate point
`MainActivity.TopNavBarScaffold` reads `navigationFeel`, computes
`isModernFeel`, branches:
- SideRail: `if (showTopNav && !isModernFeel) { SideRail(...) }`
- `LocalSideRailController` provides `openProfileOverlay` (Modern) or
  `openSideRail` (Legacy)
- ProfileOverlay mounted only when Modern
- Category pill source: dynamic `CategoryPillsViewModel` (Modern) vs fixed
  `["Home", "Movies", "TV Shows", "Collections"]` (Legacy)

---

## 🎯 Profile Overlay triggers (Modern only)

1. **Profile avatar Select** — `MainActivity` sets `showProfileOverlay = true`.
2. **D-pad Left at content carousel index 0** — via `LocalSideRailController`
   (Modern provides `openProfileOverlay`). (Hard-stopped from leftmost poster
   since 2026-05-31 — see Feel.MODERN above.)

Dismiss: Back key (overlay `BackHandler`), D-pad Right inside the panel
(`onPreviewKeyEvent` traps it), or tap scrim. Focus restores to TopBar
(`navBarFr.requestFocus()`).

`ProfileOverlay.kt`: glassmorphism (~280dp, 0.85-alpha black, slide-down +
fade-in 250ms FastOutSlowInEasing; real-blur via Coil `BlurTransformation` —
Skyworth GPU no-ops `RenderEffect`). Rows: Profile header → Search → Discover →
My Stuff → Settings → Pill Channels → (optional) Hidden Items. Vertical wrap
via `focusProperties { up/down }` on first/last rows.

---

## ✏️ Edit Mode mechanics

Entry: long-press any non-Collections category pill in Modern. Detection:
`KeyEvent.nativeKeyEvent.repeatCount == 1` (fires once after ~500ms).

State in `MainActivity.TopNavBarScaffold`:
- `var editMode: Boolean`; focused pill = grabbed pill (no separate grab state).
- Each pill wrapped in `key(label) { CategoryTabItem(...) }` → same Card
  instance moves on reorder (focus + state follow the pill, not position).

Key bindings (in `CategoryTabItem.onEditKey`):
- **D-pad L/R** — swap with adjacent pill; wraps **within category group only**
  (never into channel pills). Pos 0 Left → last category; lastIndex Right → 0.
- **D-pad Down** — `pillsVm.demote(pill)` (Home + ≥2 guards).
- **Select/OK** — exit edit mode (drop in place).
- **Back/Escape** — exit edit mode (`BackHandler(enabled = editMode)`).

Visual: every pill gets a faint accent border (moveable hint); grabbed pill
scale 1.12 + 2dp accent border; "Done" pill after the category zone.

Persistence: every swap/demote/promote saves to `CategoryPillOrderDataStore`
immediately. Invariants in `CategoryPillsViewModel`: `HOME` cannot be demoted;
TopBar count cannot drop below 2.

Promotion: `ProfileOverlay` Hidden Items renders demoted pills with `+`;
tapping promotes to rightmost TopBar position **and** navigates to its route.

---

## 🔄 Loop scrolling scope

### TopBar — both feels
- **Modern:** avatar ↔ first category ↔ last channel → wraps to avatar.
- **Legacy:** first category ↔ last channel → wraps to first category.
- First bar item Left: `onPreviewKeyEvent` → `wrapToLastChannel()`
  (`listState.scrollToItem(lastIndex) + withFrameNanos + channelFr.requestFocus()`).
- Last channel Right: `wrapToLeftmostBarItem()` (avatar in Modern / first
  category in Legacy).
- Edit Mode L/R: wraps within category list (`onEditSwap` modulo `categories.size`).

### Popups & dropdowns (wired)
- `ProfileOverlay` — `focusProperties { up/down }` on boundary rows.
- `CollectionsDropdown` — first/last `DropdownItem` `wrapUpTo`/`wrapDownTo`.
- `FolderPillsDropdown` — first/last `FolderRow` (skips non-focusable headers).
- `Modifier.dpadLoopWrap` (`ui/screens/settings/LoopScroll.kt`) — reusable.

### Not yet wired
- `SettingsHubScreen` left rail (long `LazyColumn`, needs scroll-into-view helper).
- Misc confirmation dialogs / vertical menus.

---

## ⚙️ Top Bar settings (Settings → Appearance → "Top Bar")

Visible for **both feels** (Legacy: four category pills only). Per pill: name;
↑/↓ reorder (`pillsVm.moveUp/Down`); **Display mode** single-press toggle
("Icon + text" ↔ "Icon only" → writes `iconsOnly` on `CategoryPillOrderEntry`,
rendered by `CategoryTabItem` short-circuit); visibility **Switch** (Home
disabled; others disabled if removing drops below 2). Syncs with Edit Mode via
shared `CategoryPillsViewModel` / `CategoryPillOrderDataStore`.

---

## 📺 Pill Channels (renamed from "Networks")

Old `+ Networks` button gone in both feels; channel pills scroll to right edge.
Management (`FolderPillsDropdown`) moved to:
- **Modern:** `Pill Channels` row in Profile Overlay (icon `Icons.Default.Tune`)
  → `ProfileOverlayDestination.PILL_CHANNELS` → closes overlay, opens dropdown.
- **Legacy:** `Pill Channels` SideRail item between My Stuff and Settings
  (`SideRailItem.PillChannels` + `onPillChannelsClick`).

`FolderPillsDropdown.FolderRow` shows inline `AsyncImage` for `titleLogoUrl`.
TopBar channel-pill redesign (2026-05-30/31): capsule → artwork-backed dynamic
underline; `NavBarHeight` 54dp, avatar 26dp, uniform 48×24 logos, edge-to-edge.

---

## 🏠 Home layouts

`HomeLayout` enum: **CLASSIC, MODERN, IMMERSIVE, SPOTLIGHT, GRID**.
`usesModernPresentation` extension — Modern + Immersive share the content
pipeline, differ only in hero State 1 (Immersive = fullscreen backdrop) vs
State 2 (Modern = non-fullscreen). Every old `HomeLayout.MODERN` gate switched
to `usesModernPresentation`.

### Hero states
- **Spotlight** three-state hero: **Carousel** (State A) / **Constrained**
  (State B) / **Hidden** (State C). Column→LazyColumn (Box rewrite 2026-05-26).
  State B renders identically to Modern's non-fullscreen hero. Hero collapse is
  focus-driven (leaving a Cinema row restores it).
- **Modern State 1** (Immersive): fullscreen backdrop + `ModernHomeRowsPager`
  (ONE row visible, fixed bottom, `Crossfade` on D-pad up/down, dimmed
  prev/next row-name hints). Up at row 0 → escapes to TopBar; down at last row
  consumed. Autorepeat throttled (`MODERN_PAGER_REPEAT_MS = 180`).
- **Modern State 2**: non-fullscreen hero + `ModernHomeRowsList`. Cinema-row
  reflow rides single animated `rowsViewportHeight` (`animateDpAsState` 220ms;
  `MODERN_CINEMA_STATE2_HERO_MIN = 200dp` floor). ⚠️ Proportions still need to
  match Spotlight State B (open follow-up).

### Card styles
`LayoutCardStyle`: **POSTER, LANDSCAPE, CINEMA**. CINEMA is fixed-size
(`260×370` tall premium portrait — `CINEMA_CARD_WIDTH_DP`/`CINEMA_CARD_HEIGHT_DP`
in `RowDisplayConfig.kt`). Landscape/Cinema show `backdropUrl` + permanent logo
overlay (`ContentCard.cardStyle`, `isWideCardStyle` — wins over `item.posterShape`
for resize). 3-state style selector: Poster→Landscape→Cinema. Corner radius
under Appearance → Global → Card Style. `CardFocusStyle`: **ACCENT, BLOOM**
(`Modifier.shadow` for all cards — TV M3 `Card.glow` no-ops on Skyworth).

### Row display config (3-tier resolution)
`ui/screens/home/RowDisplayConfig.kt` — pure resolver `resolveRowDisplayConfig`.
**Authoritative 3-tier: per-row → per-screen → global** (never bypass).
`LayoutRowConfig.expandEnabled` is a 3-state per-row override.

---

## ▶️ Continue Watching row subsystem

- `ContinueWatchingCardStyle { POSTER, CARD, WIDE }` stored on the CW row's
  `metadata["cw_style"]` (deliberately separate from `LayoutCardStyle` — CINEMA
  semantics clash with "Wide"). `ui.components.ContinueWatchingCard` (shared
  Classic + Modern) renders all three with per-style progress bar;
  `continueWatchingCardFootprint(style, base)` = single sizing source.
- Rows Manager CW controls: CW-specific Orient selector (Poster/Card/Wide) +
  Size (Compact→Large); **Expand hidden**.
- `ContinueWatchingFilter { SERIES, MOVIES, UP_NEXT, BOTH }`.
  `List<ContinueWatchingItem>.forContinueWatchingFilter()` partitions by
  `WatchProgress.contentType` ("movie" vs series). `HomeRow.ContinueWatching`
  carries the filter — one render path, multiple slices. Pipeline emits up to
  3 CW rows; Modern builds one `HeroCarouselRow` per filter; Spotlight chains
  focus across multiple CW rows.
- Row kinds: `CONTINUE_WATCHING` → BOTH; `CONTINUE_WATCHING_SERIES`;
  `CONTINUE_WATCHING_MOVIES`; `TRAKT_UP_NEXT` (Up Next = CW-derived NextUp
  filter, **not** a Trakt catalog row — which is why it works while true Trakt
  catalog rows needed a separate pipeline).
- "+ Continue Watching" pill opens a Series/Movies/Both submenu (max one each).
- Seeding flags: `cw_split_seeded` (one-shot legacy→Series migration + Series
  re-ensure), guarded never to create a lone CW row.
- Spotlight CW hero: `ContinueWatchingItem.toSpotlightFocusMeta()` + `cwCardFocused`
  flag forces hero CONSTRAINED (CW rows aren't in `catalogRows`).

---

## 🎬 Trakt catalog pipeline (2026-06-07)

Six auth-gated, TTL-cached row kinds → real Trakt data → `CatalogRow` of
`MetaPreview`:

| Kind | Endpoint | TTL |
|---|---|---|
| `TRAKT_RECOMMENDED_SHOWS`/`_MOVIES` | `/recommendations/{type}?extended=full,images` | 60 min |
| `TRAKT_WATCHLIST_SHOWS`/`_MOVIES` | `/sync/watchlist/{type}?extended=full,images` | 60 min |
| `TRAKT_NEW_EPISODES`/`_MOVIES` | `/calendars/my/{type}/{start}/{days}` (±7d shows, ±14d movies) | 30 min |

- **`core/trakt/TraktHomeCatalogResolver.kt`** (modeled on
  `TraktPublicListSourceResolver`): `resolve(kind): CatalogRow?`, **profile-keyed**
  TTL cache (`"<profileId>:<kind>"` — no cross-profile leak), auth-gated, returns
  stale-but-present row on transient blip, maps DTOs → `MetaPreview` via
  `TraktImageUtils` + `normalizeContentId`. No extra image fetch (uses
  `extended=full,images`).
- DTOs: recommendations reuse `List<TraktShowDto>`/`List<TraktMovieDto>`;
  watchlist reuses `TraktListItemDto` (added `extended` param to `getWatchlist`);
  calendars added `TraktCalendarShowItemDto`/`TraktCalendarMovieItemDto`.
- **Pipeline injection (separate from CW/addon paths):**
  `observeTraktCatalogRowsPipeline()` → `traktCatalogRowsByKey`
  (`ConcurrentHashMap` on `BaseHomeViewModel`) → `scheduleUpdateCatalogRows()`.
  `applyConfiguredHomeRows` TRAKT branch reserves order slots;
  `updateCatalogRowsPipeline` injects `HomeRow.Catalog(traktRow)` by key (after
  CW, before collections — **CW branch untouched**). Resolver injected into
  `BaseHomeViewModel` + all 4 subclasses.
- Rows Manager: 6 options + Up Next functional, each dims once added; show
  Orient/Size/On-Off/Delete, **Expand hidden** (gated via `isTraktCatalogRow` /
  `TRAKT_CATALOG_KINDS`).
- ⚠️ CW / Up Next / scrobble pipeline left **completely untouched** by this work.

---

## ✨ For You standalone screen

- `LayoutScreenScope.FOR_YOU` ("for_you"/"For You") + `CategoryPill.FOR_YOU`
  (first enum entry → first pill on fresh installs) + `Screen.ForYou`.
- **Reuses HomeScreen wholesale** via thin wrapper (like MoviesScreen). FOR_YOU
  forced to **Classic + hero-off** by defaults in
  `LayoutPreferenceDataStore.selectedLayoutForScope` / `heroSectionEnabledForScope`
  (FOR_YOU layout key never written — Rows Manager opens it ROWS_ONLY). No new
  rendering/pipeline code.
- Default seed (`forYouSeeded`, **only when Trakt authed**): CW Both → Up Next →
  Recommended Shows → Recommended Movies (`forYouRecommendedSeeded` adds the two
  recommendation rows once to earlier 2-row-seed users).
- Default tab: Trakt authed → For You is start destination (+ one-shot
  `promoteForYouToFrontOnce()`); else Home. MainActivity gates first frame on
  Trakt auth resolving.
- Rows Manager: FOR_YOU scope tab appears automatically; excluded from the
  layout-picker scope tabs (`ScopePills.showForYou=false`).

---

## 📁 Key files reference

### Navigation shell
- `MainActivity.TopNavBarScaffold` — Modern/Legacy gate; owns `editMode` +
  `showProfileOverlay`.
- `ui/components/TopNavigationBar.kt` — single composable for both feels
  (gated by `isModernFeel`).
- `ui/components/SideRail.kt` — Legacy-only render (includes Pill Channels).
- `ui/components/ProfileOverlay.kt` — Modern-only.

### Data layer
- `data/local/LayoutPreferenceDataStore.kt` — `navigationFeel` + per-scope
  layout/hero defaults; seeding flags (`cw_split_seeded`, `forYouSeeded`,
  `forYouRecommendedSeeded`); DataStore keys (`modern_top_bar_enabled`,
  `poster_glow_enabled`, `card_focus_style`, `top_bar_enabled`).
- `data/local/CategoryPillOrderDataStore.kt` — per-profile pill order;
  `promoteForYouToFrontOnce()`.
- `data/local/ChannelRailDataStore.kt` — `titleLogoUrl` field.

### ViewModels
- `ui/components/CategoryPillsViewModel.kt` — pill ordering (`order`/
  `topbarPills`/`drawerPills`; `swap`/`moveUp`/`moveDown`/`demote`/`promote`/
  `setVisible`/`setIconsOnly`).
- `ui/screens/home/ChannelRailViewModel.kt` — channel pills.
- `ui/screens/collection/CollectionRailViewModel.kt` — collection folders.
- `BaseHomeViewModel` (+ Home/Movies/TvShows/ForYou subclasses) —
  `traktCatalogRowsByKey`, `observeTraktCatalogRowsPipeline()`.
- `NewLayoutSettingsViewModel.kt` — `traktSignedIn`, `addContinueWatchingRow`,
  `addTraktCatalogRow`.

### Settings screens
- `ui/screens/settings/SettingsHubScreen.kt` — `appearance.feel`/`.topbar`/
  `.rows` sub-items; left-rail single-expand accordion.
- `ui/screens/settings/NavigationFeelContent.kt` — Feel picker.
- `ui/screens/settings/TopBarSettingsContent.kt` — pill order/display/visibility.
- `ui/screens/settings/NewLayoutSettingsScreen.kt` — Rows Manager (scope tabs /
  source pills / table header global controls; `PickerActionBar`).
- `ui/screens/settings/AddRowPickerDialog.kt` — add-row picker with section labels.
- `ui/screens/settings/LoopScroll.kt` — `Modifier.dpadLoopWrap`.

### Home rendering / pipeline
- `ui/screens/home/RowDisplayConfig.kt` — `resolveRowDisplayConfig`, cinema dims,
  `isTraktCatalogRow`/`TRAKT_CATALOG_KINDS`.
- `ui/screens/home/ModernHomeContent.kt` / `ModernHomeRowsList.kt` /
  `ModernHomeRowsPager.kt` / `ModernHomeModels.kt` / `ModernRowSection`.
- `ui/screens/home/SpotlightHomeContent.kt`, `ClassicHomeContent.kt`.
- `ui/screens/home/HomeLayoutSizing.kt`, `UniversalHomeNavigation.kt`.
- `HomeViewModelCatalogPipeline.kt` — `applyConfiguredHomeRows`,
  `updateCatalogRowsPipeline`, `scheduleUpdateCatalogRows`.
- `HomeViewModelContinueWatching.kt` — CW (do not disturb).
- `ui/components/ContinueWatchingCard.kt`, `ContinueWatchingSection.kt`.

### Domain / config
- `domain/model/Feel.kt`, `CategoryPill.kt`.
- `LayoutRowConfig.kt` — row kinds, `LayoutRowKey`, `LayoutCardStyle`,
  `LayoutScreenScope`, `ContinueWatchingFilter`.

### Core / Trakt
- `core/trakt/TraktHomeCatalogResolver.kt`, `TraktPublicListSourceResolver.kt`.
- `core/trakt/TraktApi.kt`, `TraktSyncDtos.kt`, `TraktImageUtils`.
- `TraktProgressService.kt`, `TraktScrobbleService.kt` (scrobble — do not disturb).
- `core/debrid/` — Real-Debrid + Torbox (added 2026-05-19).

### Misc
- `BlurTransformation.kt` — Coil stack-blur (Skyworth `RenderEffect` fallback).
- `LongPressKeyTracker.kt` — CEC long-press.
- `NuvioApplication.kt` — Coil `ImageLoader` (memory cache 0.45,
  `bitmapFactoryMaxParallelism 4`).

---

## 🖥️ Device notes

- **Skyworth/Jawwy TV**: 960×540dp (1080p @2x). GPU silently no-ops
  `RenderEffect` (blur) and TV M3 `Card.glow` — use Coil `BlurTransformation`
  and `Modifier.shadow` respectively.
- Current test TV IP: `192.168.8.187` (drifts — check `adb devices`).
  Package: `com.nuviodebug.com`. APK: `app-full-armeabi-v7a-debug.apk`.
