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

### TopBar — both feels (verified 2026-06-10)
- The loop lives in the **channel zone**: first channel Left →
  `wrapToLastChannel()`; last channel Right → `wrapToFirstChannel()`.
- The **avatar is a hard left edge** (`onWrapLeft = null` — the old
  "avatar ↔ last channel" wrap was removed in the pill rebuild;
  `wrapToLeftmostBarItem` is currently unused).
- Edit Mode L/R: wraps within category list (`onEditSwap` modulo `categories.size`).

### Content rows — CLOSED horizontal containers (2026-06-10)
Every content row on every screen/layout is a closed container:
- **Left at first item**: `tvLeftFromFirstItemToSideRail` ALWAYS consumes —
  opens the SideRail in Legacy, hard-stops in Modern (null controller).
- **Right at last item**: `Modifier.tvStopRightAtLastItem()` on the row's
  last focusable (last card, or the trailing "See All" card when shown).
- Applied in `ModernHomeRows` (Modern+Immersive, catalog+CW),
  `CatalogRowSection` (Classic/Spotlight/Collections), `ContinueWatchingSection`,
  `CollectionRowSection`, `GridContinueWatchingSection`. Both modifiers live in
  `ui/navigation/TvDpadNavigation.kt`. No jump to TopBar, no wrap, no row spill;
  the TopBar's own channel loop is unaffected (its handlers sit on bar pills).

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

### TopBar pill indicator (rebuilt 2026-06-09)
Both category and channel pills render as `Box { Column { topDash; Card{content} } }`
— **no capsule** background or focus border on any state (edit-mode reorder cues
kept). Selection/focus shown by a **TOP dash + downward glow** (`Modifier.dashDownGlow`,
glow drawn in +Y below the dash) + a dash-width **dark contrast gradient**
(`Modifier.pillContrastGradient(active, dashWidth)` — vertical gradient sized to the
dash, NOT the pill/bar). Dash width tracks the measured content width.
- **Category pills** (Home/For You/Movies/TV Shows/Collections): 3-state —
  **selected → NEUTRAL white dash** (0.85 alpha; the dash is the secondary cue)
  with **accent icon + text** (primary selection signal),
  **focused-not-selected → gray** dash+icon+text, idle → none. Selected wins
  over focused. (Dash de-accented 2026-06-10.) The category↔channel divider
  is vertically centred via its own `fillMaxHeight` row (bar is Top-aligned).
- **Channel pills**: dash + glow use the channel's **brand colour**
  (`rememberArtworkBackedGlowColor`, logo-sampled, `channel.brandColor` fallback) when
  selected/focused — **never** accent/gray. Caption white@0.65 idle → white active.
  Text-only channels (no logo) centre the name in a 24dp box to align with logo pills.
- Layout insets (Modern feel): leading `6dp` (avatar near left edge), trailing `0dp`
  (channels edge-to-edge right), bar content **top-aligned** with `2dp` top pad (bar
  bg is transparent so the visible bar = the pills), category↔channel divider `8dp`
  each side.
- **Immersion / hide-on-scroll:** `TopBarImmersionState.visible` drives a 300/400ms
  alpha fade. `HomeScreen` sets visible from `focusedRowIndex <= 1` when a hero is on
  screen (else always visible). Modern feeds `focusedRowIndex` live via
  `ModernHomeContent` `snapshotFlow{activeRowKey}` → `viewModel.updateFocusedRowIndex`.
  The TopBar Box has `onFocusChanged{ hasFocus → setVisible(true) }` so Back/Up from
  deep rows always re-shows the (alpha-hidden but still focusable) bar.

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

## 🏷️ Stream Badges subsystem (Fusion Style/Size — ported 2026-06-09)

Ported from upstream `0.7.4-beta`. Decorates stream rows with image "Style"
badges (from imported JSON rule URLs) + an optional file-"Size" chip.

- **Data:** `Stream.badges: List<StreamBadge>` (`domain/model/Stream.kt`).
  `core/streams/StreamBadgeRules.kt` (serializable rules + `StreamBadgeMatcher`
  regex matcher over stream filename/title/parsed fields — note the upstream
  `debridCacheStatus.cachedName` candidate was dropped, fork lacks it),
  `StreamBadgeSettings.kt` (`showFileSizeBadges`, `badgePlacement`),
  `StreamBadgePresentation.kt` (`@Singleton`; `apply(groups)` attaches matched
  badges), `data/local/StreamBadgeSettingsDataStore.kt` (per-profile).
- **Pipeline:** `StreamRepositoryImpl` injects `StreamBadgePresentation` and
  applies it at the emit point of `getStreamsFromAllAddons` — the single source
  feeding both the stream picker and the player, so badges flow everywhere.
- **Render:** `ui/components/StreamBadgeChips.kt` (size chip uses literal
  `"SIZE $label"`; image chips from `imageURL`). Wired into **both**
  `StreamComponents.StreamItem` (player side panels) and `StreamScreen.StreamCard`
  (main picker). `showFileSizeBadges` is read live in `StreamsList` /
  `EpisodesSidePanel.EpisodeStreamsView` / `StreamSourcesSidePanel` via
  `hiltViewModel<BadgeSettingsViewModel>()`. Placement renders bottom-only
  in-app (TOP would restructure the card; web config page still exposes it).
- **Config "gateway" server:** `core/server/StreamBadgeConfigServer.kt` (NanoHTTPD)
  + `StreamBadgeWebPage.kt` (web UI; 27 badge strings inlined as literals to avoid
  locale churn). Owned by **`core/server/StreamBadgeServerManager.kt` (`@Singleton`)**
  — binds once at app launch (`NuvioApplication.onCreate`, off main thread) and
  **stays alive for the whole app process** (not screen-scoped). Default port
  8091 (`startOnAvailablePort` tries 8091–8100). `BadgeSettingsViewModel` only
  reads `serverUrl()` + shows the QR; closing the QR / leaving the screen does
  NOT stop it.
- **Settings:** **Settings → Extensions → "Stream Badges"** (hub id
  `extensions.badges`; `BadgeSettingsContent` + `BadgeSettingsViewModel`).
- **NOT ported:** upstream's player-side badge rendering
  (`PlayerViewModel`/`PlayerRuntimeController`), `ProfileSettingsSyncService`
  badge sync, the 2 badge test files.

## 🗂️ Collections in the Rows Manager — 3-level accordion (2026-06-10)

Collection-kind rows render as accordion blocks in `RowsManagerContent`
(`NewLayoutSettingsScreen.kt`): rows group by collection id into one display
unit (`buildManagerDisplayUnits`); the **COLLECTIONS scope tab** lists every
collection (it was dead UI before). Expand state is UI-only.

- **LEVEL 1 — collection**: chevron+title (expand) | ↑↓ block move (in
  COLLECTIONS scope: reorders collections via CollectionsDataStore + sync) |
  Edit → `CollectionEditorScreen` (`onNavigateToCollectionEditor` plumbed
  NuvioNavHost → SettingsHubScreen → NewLayoutSettingsContent) | On/Off + ✕ on
  the block's rows (hidden in COLLECTIONS scope).
- **LEVEL 2 — folders**: visibility = per-folder rows
  (`collection|<cid>|<fid>`; materialize-all-on-first-interaction; the
  pipeline's `collectionFolderVisibility` map in `applyConfiguredHomeRows` /
  `updateCatalogRowsPipeline` filters `HomeRow.CollectionRow` — no folder rows
  = all folders, all-disabled = row hidden). ↑↓/✕ mutate the collection itself
  (confirm dialog on ✕). Unified **Layout picker** (COLLECTIONS scope only).
- **LEVEL 3 — catalogs (sources)**: ↑↓/✕ mutate `folder.sources` (confirm);
  Orient/Size/On-Off persist per-source in the folder row's metadata
  (`src_style|/src_width|/src_off|<key>`, `collectionSourceKey()` in the VM)
  — **render-inert** until FolderDetail consumes them.
- ⚠️ `mutateRows` stamps `viewContext` on every row — rows created inside
  transforms otherwise default to HOME and get dropped by `rowsForScope`'s
  read filter.
- ⚠️ **Spotlight home layout renders NO collection rows** (no CollectionRow
  branch in `SpotlightHomeContent`) — pre-existing gap, open follow-up.

### Per-folder presentation (3-tier, 2026-06-10)
"How a folder opens" resolves in `FolderDetailViewModel.loadFolder()`:
**per-folder override → `collection.viewMode` → TABBED_GRID**. The override
lives in the folder row's `metadata[FOLDER_LAYOUT_METADATA_KEY]` in the
**COLLECTIONS scope (canonical — entry-point independent)**; values:
`TABS` / `ROWS` / a `HomeLayout` name (→ FOLLOW_LAYOUT with that layout;
IMMERSIVE implies fullscreen backdrop). Picker options: Default · Tabs · Rows ·
Classic · Modern · Immersive · Spotlight · Grid. The collection editor's View
Mode is the tier-2 default (subtitle says so). All three folder entry points
(TopBar pill, Collections tab, home-row card) hit the same
`Screen.FolderDetail` route + resolver.

### FolderDetail layouts + the Back trap (2026-06-10)
`FollowLayoutContent` renders ALL five layouts — **Spotlight is real now**
(was a Classic fallback; hero falls back to first row's first item since
folder homeState has no heroItems). All four content composables
(Spotlight/Modern/Classic/Grid) end their Back hierarchy with
`navBarFr.requestFocus()`, which silently no-ops on the TopBar-less
FolderDetail route → Back trap. Fix: **`LocalContentBackFallback`**
(MainActivity.kt) — optional back action invoked at each terminal
TopBar-escape branch (`fallback?.invoke() ?: requestFocus`);
FolderDetailScreen provides its `onBack` around FollowLayoutContent. Main
screens provide nothing → unchanged. Any future TopBar-less embed of these
layouts MUST provide this Local.

## 🖥️ Device notes

- **Skyworth/Jawwy TV**: 960×540dp (1080p @2x). GPU silently no-ops
  `RenderEffect` (blur) and TV M3 `Card.glow` — use Coil `BlurTransformation`
  and `Modifier.shadow` respectively.
- Current test TV IP: `192.168.8.187` (drifts — check `adb devices`).
  Package: `com.nuviodebug.com`. APK: `app-full-armeabi-v7a-debug.apk`.
- **Custom buffer engine** (added 2026-06-08, upstream `a24c38b4` port, DV7-separated):
  `core/player/BitrateAwareLoadControl.kt` (DefaultLoadControl subclass with a
  memory-budget byte target + runtime back-buffer/budget override setters) is built
  in `PlayerRuntimeControllerInitialization` from `PlayerSettings.bufferSettings` +
  `MemoryBudget.budgetMb` (replacing the old flat 100MB/70s DefaultLoadControl).
  `ui/screens/player/ParallelRangeDataSource.kt` (parallel HTTP range downloader,
  needs a concrete `OkHttpDataSource.Factory`) is wired into the progressive branch
  of `PlayerMediaSourceFactory` — **opt-in** (`PlayerSettings.parallelNetworkEnabled`,
  default off), never for HLS/DASH/forced-default. `ui/screens/settings/MemoryBudget.kt`
  = heap-tiered budget helpers. Settings: **Settings → Playback → "Buffer & Network"**
  (`BufferNetworkSettingsContent` + isolated `BufferNetworkSettingsViewModel`;
  hub id `playback.buffer`): Target buffer size (Auto), **Max buffer duration**
  (30s–180s, default 50s — `setBufferDurationMs` writes min==max for continuous
  top-up; added 2026-06-09), parallel toggle/connections/chunk. **Parallel
  connections default = 3** (was 2; 2026-06-09). NOT ported: upstream's VOD
  disk-cache (`VodCacheSizeMode`) + DV7-coupled `NetworkSettingsScreen`. Other
  LoadControl knobs (initial/after-rebuffer/back-buffer/memory budget) deliberately
  not exposed — no real-world gain for progressive/debrid (2026-06-09 research).
  Fusion badges now ported (see Stream Badges subsystem above).
- **Legacy-Android TLS** (added 2026-06-08, upstream port): bundled ISRG root
  certs (`res/raw/isrg_root_x2.pem`, `isrgrootx1.pem`) + `res/xml/network_security_config.xml`
  (referenced from `AndroidManifest`) so old Android TV cert stores can complete
  Let's Encrypt TLS handshakes.

---

## 🔀 Fork divergence from upstream (relevant for syncs)

- We **cherry-pick, never merge** — merge-base with upstream stays at the May
  fork point (`68b4a34e`). See `PHASE1_PICKLIST.md` for the live sync audit.
- **Subsystems upstream has that our fork does NOT carry** (0 files each — any
  upstream commit depending on them is unpickable without porting the whole
  subsystem): `DolbyVision`/HDR-strip (`core/player/DolbyVision*`, `dvmkv/`,
  DV7 native libs), `CloudLibrary` (`core/cloud/CloudLibrary*`).
  **`StreamBadge` was ported 2026-06-09** (see Stream Badges subsystem below) —
  no longer absent.
- **Locale files diverged across the board** (lint-baseline English defaults +
  already-applied upstream picks) → per-commit i18n cherry-pick is not viable;
  use a wholesale locale-file refresh instead. Fork carries 27 locales; upstream
  has 31 (missing: `in`, `ta`, `zh-rCN`, `zh-rTW`).
- ⚠️ **Tooling note:** `grep -q`/`grep -qv` return wrong exit status in this
  shell — never use them in classification/verification loops; use explicit
  capture + `[ -n ]`/`[ -z ]`.
