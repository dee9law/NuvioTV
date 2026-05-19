# NuvioTV — App Status Report

**Generated:** 2026-05-19
**Branch:** `dev`
**Scope:** Read-only audit. No code changes were made.

This report captures the as-of-today state of the navigation shell, routes,
settings hub, layout/rows system, known issues, and a full file inventory
under `ui/screens/`, `ui/components/`, `ui/navigation/`, and `domain/model/`.

---

## 1. Current Navigation System

### Visible UI

The app runs one of two navigation shells, both rendered inside
`TopNavBarScaffold` in `MainActivity.kt`. The source of truth is the
per-profile `LayoutPreferenceDataStore.navigationFeel` flow, threaded into
the scaffold at `MainActivity.kt:485` as `navigationFeel = mainUiPrefs.navigationFeel`.

**Gate point:** `val isModernFeel = navigationFeel == Feel.MODERN` at
`MainActivity.kt:555`. This single boolean drives every branch below.

#### Modern feel — visible UI
- TopBar (rendered at `MainActivity.kt:735` via `TopNavigationBar(...)`):
  - Profile avatar circle at far left (`TopNavigationBar.kt:218-231`, only when `isModernFeel`).
  - Dynamic, reorderable category pills sourced from `pillsVm.topbarPills`
    (`MainActivity.kt:715-719`), rendered in vertical icon-over-label layout
    (`verticalLayout = isModernFeel`, `TopNavigationBar.kt:276`).
  - Channel pills (Pill Channels) scrolling to the right screen edge
    (`TopNavigationBar.kt:360-388`).
- **No SideRail** — gated off at `MainActivity.kt:820`
  (`if (showTopNav && !isModernFeel)`).
- `ProfileOverlay` mounted at `MainActivity.kt:898-945` (only when
  `isModernFeel`), with rows for Search / Discover / My Stuff / Settings /
  Manage Profiles / Pill Channels + Hidden Items for demoted pills.

#### Legacy feel — visible UI
- Same TopBar composable, but with `categoryIcons = null` and
  `categoryIconsOnly = null` (`MainActivity.kt:729, 734`) and the fixed pill
  list `["Home", "Movies", "TV Shows", "Collections"]` (`MainActivity.kt:718`).
  Avatar zone is skipped.
- SideRail rendered at `MainActivity.kt:821-857` along the left edge.
- ProfileOverlay never mounted.

TopBar fades via `topBarAlpha` (`MainActivity.kt:700-707`, 600ms
`FastOutSlowInEasing`) driven by `TopBarImmersionState.visible`.

### What's wired (TopNavBarScaffold callbacks)

**TopNavigationBar callbacks (`MainActivity.kt:735-812`):**
- `onProfileClick = { showProfileOverlay = true }` (line 746)
- `onCategorySelected` (lines 779-796) — Modern: looks up
  `topbarPills[index]`, routes via `routeForPill`. Legacy: hardcoded
  1→Movies, 2→TvShows, 3→CollectionsHome, else→Home. Always
  `collectionRailVm.clear()` then `navigateToTopNavRoute(...)`.
- `onCategoryLongPress` (lines 765-778) — Modern: Collections opens
  `CollectionsDropdown`; any other pill enters `editMode = true`. Legacy:
  only index 3 opens the dropdown.
- `onEditSwap` (lines 750-759) — translates visible indices to full-order
  indices, calls `pillsVm.swap(a, b)`.
- `onEditDemote` (lines 760-763) — `pillsVm.demote(pill)`.
- `onExitEditMode = { editMode = false }` (line 764)
- `onChannelSelected` (lines 797-811) — decodes folder pill ID via
  `CollectionRailViewModel.decodeFolderTabId`; resolves → navigates to
  `Screen.FolderDetail`. Inline comment notes "No fallback: every rail pill
  is a folder pill now."

**SideRail callbacks (`MainActivity.kt:821-857`, Legacy only):**
- `onSearchClick` → `Screen.Search.route`
- `onHomeClick` → `Screen.Home.route`
- `onDiscoverClick` → `Screen.Discover.route`
- `onMyStuffClick` → `Screen.Library.route`
- `onSettingsClick` → `Screen.Settings.route`
- `onPillChannelsClick = { showFolderPillsDropdown = true }` (line 846)
- `onProfileClick` → `navController.navigate(Screen.ManageProfiles.route)` (line 848)

**ProfileOverlay callbacks (`MainActivity.kt:899-944`):**
- `onDismiss` → hides overlay + `navBarFr.requestFocus()`
- `onNavigate` → SEARCH→Search, DISCOVER→Discover, MY_STUFF→Library,
  SETTINGS→Settings, MANAGE_PROFILES→ManageProfiles; `PILL_CHANNELS` opens
  `showFolderPillsDropdown = true` instead of routing. (Line 924 also maps
  `PILL_CHANNELS → Screen.Home.route` in an inner `when` but it's unreachable
  — outer `when` catches it first. Minor dead branch.)
- `onHiddenItemSelected` → `pillsVm.promote(pill)` + navigates to that
  pill's route in one motion.

**Empty/TODO lambdas:** `AuthQrSignInScreen(onBackPress = {}, ...)` at
`MainActivity.kt:335` is an intentional no-op. No other handlers are
stubbed; every callback resolves to a real destination or state mutation.

### Focus and navigation mechanics

**CompositionLocals (`MainActivity.kt:676-682`):**
- `LocalContentFocusRequester` → `contentFocusRequester`
- `LocalNavBarFocusRequester` → `navBarFr`
- `LocalSideRailController` → `openProfileOverlay` (Modern) or
  `openSideRail` (Legacy) — same trigger, different destination.

**D-pad behavior (`TopNavigationBar.kt`):**
- Down anywhere on the bar → `contentFr.requestFocus()` (lines 208-214).
- Last channel pill Right → `wrapToLeftmostBarItem` (avatar in Modern,
  first category in Legacy) (lines 197-201, 385).
- Modern avatar Left → `wrapToLastChannel` (scrolls LazyRow + waits
  `withFrameNanos` + requests focus) (lines 185-196, 228).
- Legacy first category Left → `wrapToLastChannel` (lines 316-318); gated
  `!isModernFeel`.
- LazyRow uses `focusRestorer { channelFr(lastFocusedChannel) }` (line 364).

**Edit Mode (`TopNavigationBar.kt:278-311`):** When `editMode && isFocused`
on a category pill, `onEditKey` intercepts L/R (swap with neighbor, wraps
within category list), Down (demote), Center/Enter/Back/Escape (exit).
Entry: long-press detected via `event.nativeKeyEvent.repeatCount == 1`
(`TopNavigationBar.kt:507`).

**BackHandlers (`MainActivity.kt`):**
- Line 649-651: `BackHandler(enabled = editMode)` — exits edit mode.
- Line 653: `BackHandler(enabled = currentRoute in rootRoutes, onBack = onExitApp)` — quits from root.
- ProfileOverlay has its own internal BackHandler.

### SideRail items (top-down)

Enum at `SideRail.kt:90`:
`enum class SideRailItem { Profile, Search, Home, Discover, MyStuff, PillChannels, Settings }`.

Render order in the `Column` (`SideRail.kt:212-287`):
1. ProfileHeader (line 220)
2. Search (line 231)
3. Home (line 240)
4. Discover (line 249-258) — conditional on `showDiscover`
5. My Stuff (line 260)
6. Pill Channels (line 269, `Icons.Default.Tune`)
7. Settings (line 278)

### TopNavigationBar structure

- `data class ChannelTab` (`TopNavigationBar.kt:99-105`): `id`, `name`,
  `logoResId`, `titleLogoUrl`, `brandColor`.
- `fun TopNavigationBar(...)` (line 126) — single composable for both feels.
- `CategoryTabItem` (line 401) — three render paths: icons-only (546-556),
  vertical icon-over-label Modern path (557-577), and Row icon-beside-label
  Legacy path (578-599). Grabbed-state scale 1.12 (452-461); edit-mode accent
  border (464, 531-540).
- `ChannelTabItem` (line 605) — Card with optional `AsyncImage` logo above
  caption (676-696), text-only fallback (697-705).
- `DoneEditPill` (line 715) — accent-tinted pill rendered after the
  category zone via `AnimatedVisibility` while `editMode` is true.
- `ProfileAvatarButton` (line 755) — 36dp circular Card wrapping
  `ProfileAvatarCircle`, accent focus ring, Left-key wrap handler.
- `NavDivider` (line 806) — 1dp × 24dp vertical divider between zones.

Modern: `categories = topbarPills.map { it.displayLabel }`
(`MainActivity.kt:715-716`), `categoryIcons` from `topbarPills` (720-729),
`categoryIconsOnly` from `pillOrderFull` (730-734). Each pill is wrapped in
`key(label)` (`TopNavigationBar.kt:251`) so reorders move the existing Card
and focus/state follow the pill.

Legacy: fixed `["Home", "Movies", "TV Shows", "Collections"]` with
`categoryIcons = null` (Row path with text-only pills).

The "+ Networks" manage button has been removed in both feels
(`TopNavigationBar.kt:390-393` comment "Networks/Pill-Channels manage
button removed (Task G-A)").

---

## 2. Current Screens

### Routes defined in `Screen.kt`

| Route Name | Path String | Parameters | Represents |
|---|---|---|---|
| `Home` | `home` | — | Home shell (catalog rows + hero) |
| `Detail` | `detail/{itemId}/{itemType}?...` | itemId, itemType, addonBaseUrl?, returnFocusSeason?, returnFocusEpisode?, returnToHomeOnBack?, heroBackdropUrl? | Meta detail page |
| `Stream` | `stream/{videoId}/{contentType}/{title}?...` | 19 args incl. videoId, contentType, title, season, episode, contentId, manualSelection, startFromBeginning | Stream/source picker |
| `Player` | `player/{streamUrl}/{title}?...` | 28 args incl. streamUrl, headers, infoHash, fileIdx, autoPlayNav | Video player |
| `Search` | `search` | — | Search screen |
| `Discover` | `discover` | — | Discover/browse screen |
| `Movies` | `movies` | — | Movies hub |
| `TvShows` | `tv_shows` | — | TV Shows hub |
| `Library` | `library` | — | "My Stuff" library |
| `Settings` | `settings` | — | Settings hub |
| `Trakt` | `trakt` | — | Trakt integration settings |
| `TmdbSettings` | `tmdb_settings` | — | TMDB API settings |
| `ThemeSettings` | `theme_settings` | — | Theme/appearance settings |
| `PlaybackSettings` | `playback_settings` | — | Playback settings |
| `About` | `about` | — | About screen |
| `SupportersContributors` | `supporters_contributors` | — | Credits screen |
| `AddonManager` | `addon_manager` | — | Addon manager |
| `CatalogOrder` | `catalog_order` | — | Reorder catalogs |
| `Plugins` | `plugins` | — | Plugin manager (feature-gated) |
| `ExperienceModeSelection` | `experience_mode_selection` | — | Essential/Advanced picker (deprecated in onboarding) |
| `LayoutSelection` | `layout_selection` | — | Onboarding layout picker |
| `LayoutSettings` | `layout_settings` | — | Dormant "Old Layout" screen |
| `AppearanceRows` | `appearance_rows/{scope}` | scope (LayoutScreenScope) | Per-screen row appearance editor |
| `Account` | `account` | — | Account screen (renders AuthQrSignInScreen) |
| `ManageProfiles` | `manage_profiles` | — | Profile management |
| `AuthSignIn` | `auth_sign_in` | — | Manual sign-in screen |
| `AuthQrSignIn` | `auth_qr_sign_in` | — | QR sign-in screen |
| `SyncCodeGenerate` | `sync_code_generate` | — | Generate device sync code |
| `SyncCodeClaim` | `sync_code_claim` | — | Claim device sync code |
| `CatalogSeeAll` | `catalog_see_all/{catalogId}/{addonId}/{type}?fromSearch=` | catalogId, addonId, type, fromSearch | Paginated "see all" grid |
| `Collections` | `collections` | — | Collection management |
| `CollectionsHome` | `collections_home` | — | Collections home grid (top-nav target) |
| `CollectionEditor` | `collection_editor?collectionId=` | collectionId? | Create/edit a collection |
| `FolderDetail` | `folder_detail/{collectionId}/{folderId}` | collectionId, folderId | Single folder grid |
| `ProfileSelection` | `profile_selection` | — | Profile picker (shown pre-NavHost) |
| `CastDetail` | `cast_detail/{personId}/{personName}?preferCrew=` | personId, personName, preferCrew | Cast/crew person page |
| `TmdbEntityBrowse` | `tmdb_entity_browse/{entityKind}/{entityId}/{entityName}?sourceType=` | entityKind, entityId, entityName, sourceType | TMDB entity browse |

### Reachability map

| Route | Registered? | Navigated From | Notes |
|---|:-:|---|---|
| `Home` | Y (`NuvioNavHost.kt:129`) | start dest, TopBar/SideRail Home, Detail/Stream/Player back-flows | |
| `Detail` | Y (`NuvioNavHost.kt:221`) | Home/Movies/TvShows/Search/Discover/CastDetail/TmdbEntityBrowse/FolderDetail/CatalogSeeAll, launcher intent | |
| `Stream` | Y (`NuvioNavHost.kt:345`) | Detail `onPlayClick`, Home continue-watching, Player back/end flows | |
| `Player` | Y (`NuvioNavHost.kt:566`) | Stream `onStreamSelected`/`onAutoPlayResolved`, Player error fallback | |
| `Search` | Y (`NuvioNavHost.kt:886`) | SideRail, ProfileOverlay | |
| `Discover` | Y (`NuvioNavHost.kt:902`) | SideRail, ProfileOverlay | |
| `Movies` | Y (`NuvioNavHost.kt:910`) | TopBar category pill | |
| `TvShows` | Y (`NuvioNavHost.kt:942`) | TopBar category pill | |
| `Library` | Y (`NuvioNavHost.kt:974`) | SideRail My Stuff, ProfileOverlay | |
| `Settings` | Y (`NuvioNavHost.kt:983`) | SideRail, ProfileOverlay | |
| `Trakt` | Y (`NuvioNavHost.kt:1004`) | SettingsHub callback | |
| `TmdbSettings` | Y (`NuvioNavHost.kt:1010`) | No `navigate()` call found | ⚠️ orphan (likely inline-only inside hub) |
| `ThemeSettings` | Y (`NuvioNavHost.kt:1016`) | No `navigate()` call found | ⚠️ orphan |
| `PlaybackSettings` | Y (`NuvioNavHost.kt:1022`) | No `navigate()` call found | ⚠️ orphan |
| `About` | Y (`NuvioNavHost.kt:1028`) | No `navigate()` call found | ⚠️ orphan |
| `SupportersContributors` | Y (`NuvioNavHost.kt:1037`) | SettingsHub, About | |
| `AddonManager` | Y (`NuvioNavHost.kt:1043`) | Home/Movies/TvShows empty-state, SettingsHub | |
| `CatalogOrder` | Y (`NuvioNavHost.kt:1051`) | AddonManager | |
| `Plugins` | Y (gated, `NuvioNavHost.kt:1105`) | No `navigate()` call found | ⚠️ orphan |
| `ExperienceModeSelection` | **N** | — | Intentionally removed from graph (lines 113-118) |
| `LayoutSelection` | Y (`NuvioNavHost.kt:119`) | start dest when `!hasChosenLayout` | |
| `LayoutSettings` | Y (`NuvioNavHost.kt:1132`) | No `navigate()` call found | ⚠️ dormant per CLAUDE.md |
| `AppearanceRows` | Y (`NuvioNavHost.kt:1138`) | Home/Movies/TvShows empty-state | |
| `Account` | Y (`NuvioNavHost.kt:1112`) | No `navigate()` call found | ⚠️ duplicate of `AuthQrSignIn` |
| `ManageProfiles` | Y (`NuvioNavHost.kt:996`) | SettingsHub, SideRail profile, ProfileOverlay | |
| `AuthSignIn` | Y (`NuvioNavHost.kt:1118`) | No `navigate()` call found | ⚠️ orphan |
| `AuthQrSignIn` | Y (`NuvioNavHost.kt:1126`) | SettingsHub, AuthSignIn | |
| `SyncCodeGenerate` | **N** | — | ⚠️ defined, never registered or navigated |
| `SyncCodeClaim` | **N** | — | ⚠️ defined, never registered or navigated |
| `CatalogSeeAll` | Y (`NuvioNavHost.kt:1152`) | Home, Movies, TvShows, Search | |
| `Collections` | Y (`NuvioNavHost.kt:1057`) | AddonManager | |
| `CollectionsHome` | Y (`NuvioNavHost.kt:1066`) | TopBar Collections pill | |
| `CollectionEditor` | Y (`NuvioNavHost.kt:1074`) | CollectionManagementScreen | |
| `FolderDetail` | Y (`NuvioNavHost.kt:1089`) | Home/Movies/TvShows, CollectionsHome, TopBar channel pill | |
| `ProfileSelection` | **N** | — | Rendered imperatively in `MainActivity.kt:379` outside NavHost |
| `CastDetail` | Y (`NuvioNavHost.kt:1193`) | Detail, self (cast-to-cast) | |
| `TmdbEntityBrowse` | Y (`NuvioNavHost.kt:1212`) | Detail, self | |

### Hardcoded route strings

No hardcoded `navigate("literal-string")` calls. Every `navigate()` uses
`Screen.Foo.route` or `Screen.Foo.createRoute(...)`. Two raw string prefix
checks exist in `NuvioNavHost.kt:54-58` and `:718`/`:778`
(`startsWith("stream/")`, `startsWith("player/")`, `startsWith("detail/")`)
used only for transition gating — acceptable but fragile if path roots
change.

### Key findings (routes)

- **2 ghost routes** (`SyncCodeGenerate`, `SyncCodeClaim`) defined in
  `Screen.kt:162-163` are completely unwired.
- **1 explicitly dormant** (`ExperienceModeSelection`) plus `LayoutSettings`
  flagged dormant per CLAUDE.md.
- **6+ orphans** registered without inbound navigation found in scanned
  files: `TmdbSettings`, `ThemeSettings`, `PlaybackSettings`, `About`,
  `Plugins`, `Account`, `AuthSignIn`, `LayoutSettings`. Most are likely
  reached via inline content in `SettingsHubScreen` (which now embeds
  content rather than routing) — confirm before deleting.
- **`Account` is a duplicate destination** — its composable at
  `NuvioNavHost.kt:1112-1116` just renders `AuthQrSignInScreen`, same as
  `AuthQrSignIn`. Candidate for consolidation.
- **`ProfileSelection`** has a `Screen` entry but is rendered imperatively
  outside the NavHost (`MainActivity.kt:379`) — its route string is unused.

---

## 3. Current Settings Structure

The Settings hub (`SettingsHubScreen.kt:83`) is a two-panel cascade: a left
rail of expandable category cards and a right pane that renders inline
content per sub-item. Sub-items are one of two kinds — `HubSubItem.Content`
(renders inline at `:520`) or `HubSubItem.NavAction` (jumps to a full
screen, `:607`).

### Appearance — `Icons.Default.Palette` (`SettingsHubScreen.kt:617`)
- **Feel** — `appearance.feel` Content (`:528`, `:622`) — inline picker
  (`NavigationFeelContent`) for Modern vs Legacy navigation shell.
- **Top Bar** — `appearance.topbar` Content (`:529`, `:623`) — inline
  `TopBarSettingsContent`; reorder/visibility/icon-only for category
  pills. Reads live `feel` via
  `NavigationFeelViewModel.feel.collectAsStateWithLifecycle()`.
- **Global** — `appearance.global` Content (`:536`, `:624`) — inline
  `GlobalSettingsContent` (global layout defaults).
- **Layout** — `appearance.layout` Content (`:537`, `:625`) — inline
  `NewLayoutSettingsContent` in `LAYOUT_ONLY` mode.
- **Rows** — `appearance.rows` Content (`:538`, `:626`) — inline
  `NewLayoutSettingsContent` in `ROWS_ONLY` mode.
- **Continue Watching** — `appearance.continue_watching` Content (`:539`,
  `:627`) — inline `ContinueWatchingSettingsContent`.
- **Theme** — `appearance.theme` Content (`:540`, `:633`) — inline
  `ThemeSettingsContent`.
- _Note:_ "Old Layout" intentionally removed; `LayoutSettingsScreen.kt`
  kept dormant per inline comment at `:628-:632`.

### Extensions — `Icons.Default.Extension` (`SettingsHubScreen.kt:636`)
- **Plugins** — `extensions.plugins` Content (`:542`, `:641`) — inline
  `PluginsInlineWrapper` hosting `PluginScreenContent` inside a
  `SettingsGroupCard` with a `SettingsDetailHeader` (`:564`).
- **Addons** — `extensions.addons` NavAction → `NavTarget.ADDONS` (`:642`)
  — leaves hub via `onNavigateToAddons()` (external-link icon at `:451`).
- **TMDB** — `extensions.tmdb` Content (`:543`, `:643`) — inline
  `TmdbSettingsContent`.
- **MDBList** — `extensions.mdblist` Content (`:544`, `:644`) — inline
  `MDBListSettingsContent`.
- **AnimeSkip** — `extensions.animeskip` Content (`:545`, `:645`) — inline
  `AnimeSkipSettingsContent`.

### Accounts & Sync — `Icons.Default.Person` (`SettingsHubScreen.kt:648`)
- **Account** — `accounts.account` Content (`:547`, `:653`) — inline
  `AccountSettingsInline`; receives `onNavigateToAuthQrSignIn` lambda.
- **Profiles** — `accounts.profiles` Content (`:548`, `:654`) — inline
  `ProfileSettingsContent`; "Manage" button → `onNavigateToManageProfiles`.
- **Trakt** — `accounts.trakt` NavAction → `NavTarget.TRAKT` (`:655`) —
  full-screen Trakt route via `onNavigateToTrakt()`.

### Playback — `Icons.Default.PlayArrow` (`SettingsHubScreen.kt:658`)
- **Playback** — `playback.main` Content (`:550`, `:663`) — inline
  `PlaybackSettingsContent`. Only one sub-item; redundant with category
  label.

### Advanced — `Icons.Default.Settings` (`SettingsHubScreen.kt:666`)
- **Network** — `advanced.network` Content (`:552`, `:671`) — inline
  `AdvancedSettingsContent` (network/diagnostics).
- **About** — `advanced.about` Content (`:553`, `:672`) — inline
  `AboutSettingsContent` with `onNavigateToSupportersContributors` lambda.

### Unwired / Placeholder Items
- `ComingSoonPlaceholder` fallback (`SettingsHubScreen.kt:560`, `:581`) —
  renders "$label — coming soon" for unknown `contentSubId`. All 14 current
  IDs are mapped; remains as a safety net.
- No explicit TODO markers; the dormant comment at `:628-:632` flags
  `LayoutSettingsScreen.kt` as deliberately retained.

### State / side effects
- `NavigationFeelViewModel.feel` collected via
  `collectAsStateWithLifecycle()` at `:533` — only DataStore flow read
  directly in this file.
- `expandedCategoryIds: Set<String>` at `:98` — multi-expand cascade.
- `selectedContentSubId: String` at `:99` — empty by default.
- `BackHandler { onBack() }` at `:91`.
- `FocusRequester` for back pill at `:101`; first category gets
  `up = backFocusRequester` for D-pad-up wrap.

---

## 4. Current Layout System

The New Layout Settings screen is rendered by `NewLayoutSettingsContent`
and embedded in three modes via `NewLayoutContentMode { ALL, LAYOUT_ONLY,
ROWS_ONLY }` at `NewLayoutSettingsScreen.kt:79`.

### Scope pills (top)
`ScopePills` at `NewLayoutSettingsScreen.kt:274` renders one pill per
`LayoutScreenScope` entry (HOME / MOVIES / TV / COLLECTIONS / DETAIL). The
Rows screen filters out DETAIL (`:280-286`). Selection writes
`_selectedScope` in the VM (`NewLayoutSettingsViewModel.kt:217`); every
layout/row flow re-`flatMapLatest`s off that scope — the whole panel is
per-screen-scoped.

### Layout picker section
`LayoutSection` at `:349` — three `LayoutCard` previews: `HomeLayout.MODERN`,
`GRID`, `CLASSIC`. Bound to `uiState.layout` via `viewModel.setLayout`
(`NewLayoutSettingsViewModel.kt:221`), which writes
`layoutPreferenceDataStore.setSelectedLayoutForScope(scope, layout)`.

### Per-layout settings (`NewLayoutSettingsScreen.kt:145-167`)

- **Modern** (`ModernLayoutSettings`, line 392)
  - `Fullscreen Hero Backdrop` toggle → `uiState.fullscreenHero` ↔
    `setFullscreenHero` (`:225`).

- **Grid** (`GridLayoutSettings`, line 405)
  - `Show Hero Section` → `uiState.showHeroSection` ↔ `setShowHeroSection`
    (`:229`).
  - When ON, `HeroCatalogsPicker` (line 483) — multi-select LazyRow over
    `uiState.availableHeroCatalogs` (`HeroCatalogChoice`, VM `:45`).
    `toggleHeroCatalog` flips entries via `setHeroCatalogKeysForScope`.

- **Classic** (`ClassicLayoutSettings`, line 430)
  - `Focus Item Gradient` → `setFocusItemGradient` (`:240`).
  - `Show Hero Section` + `HeroCatalogsPicker` (same as Grid).

### Detail Page section
`DetailPageSection` (`:948`) replaces the layout picker when scope==DETAIL.
Bypasses `NewLayoutSettingsViewModel` and injects `LayoutSettingsViewModel`
(four toggles already on global keys):
- `Blur unwatched episodes` → `SetBlurUnwatchedEpisodes`
- `Trailer button` → `SetDetailPageTrailerButtonEnabled`
- `Prefer external metadata` → `SetPreferExternalMetaAddonDetail`
- `Full release date` → `SetShowFullReleaseDate`

### 3-tier resolution
- **Per-row (top)**: `LayoutRowConfig` carries `cardStyle` (POSTER/LANDSCAPE)
  + `cardWidthDp`. Set via `setRowCardStyle` / `setRowCardWidth`
  (`NewLayoutSettingsViewModel.kt:346-352`).
- **Per-screen (middle)**: every `*ForScope` write keyed by `_selectedScope`
  (layout, fullscreenHero, showHeroSection, heroCatalogKeys,
  focusItemGradient, rows).
- **Global (floor)**: `Card Orientation` toggle (`CardOrientationToggle`,
  `:843`) reads/writes `modernLandscapePostersEnabled` via
  `setLandscapePostersDefault` (`:248`). Subtitle: "Per-row card style still
  wins when set." Detail Page toggles are also global.

---

## 5. Current Rows System

Rows section is rendered inside the same `LazyColumn` when `showRows` is
true (`NewLayoutSettingsScreen.kt:171`) and hidden entirely on DETAIL
scope (`:108`).

### Add Row buttons
Legacy single "Add Row" replaced with `AddRowButtonBar` (`:886`) — a
horizontal Row of four `AddRowChip` pills (`:904`), each 38dp rounded
Button with a `+` icon:

1. **Catalog** → `showCatalogPicker = true` → `CatalogPickerDialog` over
   `uiState.availableSources` (filtered to non-search addon catalogs). On
   select → `viewModel.addRow(source)` (VM `:264`), produces a
   `LayoutRowConfig` with `kind = LayoutRowKind.ADDON` (default 126dp).
2. **TMDB Source** → `TmdbSourcePickerDialog`:
   - `onAddDiscover` → `addTmdbDiscoverRow(mediaType, sortBy, genre, year, name)`
     (VM `:275`) → `LayoutRowKind.TMDB_DISCOVER` with metadata
     `{media_type, sort_by, with_genres?, year?}`.
   - `onAddNetwork` → `addTmdbNetworkRow(networkId, mediaType, name)`
     (VM `:299`) → `LayoutRowKind.TMDB_NETWORK`.
3. **Trakt List** → `TraktPickerDialog` over the static four-entry list
   (VM `:146-151`: Watchlist, Recommended, Trending Movies, Trending Shows).
   Kind = `LayoutRowKind.TRAKT`.
4. **Collection** → `CollectionPickerDialog` over
   `uiState.availableCollections` → `addCollectionFolderRow(collectionId, folderId, name)`
   (VM `:314`) → `LayoutRowKind.COLLECTION` with metadata
   `{collection_id, folder_id}`.

Hidden for COLLECTIONS scope (`showScopedAddButtons`, `:110`). All adds
route through `addRow(LayoutRowConfig)` (VM `:253`), which dedupes on `id`,
stamps `viewContext = scope`, and appends.

### Per-row controls
Rendered by `RowItem` (`:525`) inside a non-focusable `Box` so D-pad lands
on inner controls directly. Left-to-right:

- **Title text** (`row.name`) — dimmed when `!row.enabled`.
- **Move up** `IconChipButton` (`Icons.Default.ArrowUpward`) →
  `moveRow(row.id, -1)` (VM `:331`). Disabled on first row.
- **Move down** `IconChipButton` → `moveRow(row.id, +1)`. Disabled on last.
- **Style toggle pill** (`ToggleStylePill`, `:598`) — flips Poster ↔
  Landscape → `setRowCardStyle(row.id, next)` (VM `:346`).
- **Width dropdown** (`LabeledDropdown`, `:682`) — 6 `CardWidthOptions`
  (`:376`): Compact 104, Dense 112, Standard 120, Balanced 126, Comfort 134,
  Large 140. Writes via `setRowCardWidth` (VM `:350`).
- **Enabled `Switch`** → `toggleRowEnabled(row.id)` (VM `:342`).
- **Remove** `IconChipButton` (`Icons.Default.Close`) → `removeRow(row.id)`
  (VM `:329`).

### Row kinds
`LayoutRowKind` (`domain.model`) has five values: `ADDON`, `COLLECTION`,
`TRAKT`, `TMDB_DISCOVER`, `TMDB_NETWORK`. Source options produced via
`CatalogSourceOption(id, kind, name, groupLabel)` (VM `:33`).

### Reorder / focus / expand
- No drag-and-drop — reorder via ↑/↓ icon chips only. `moveRow` clamps
  the target and swaps in a mutable list (VM `:331-340`).
- `LazyColumn` uses `key = { it.id }` so reorder preserves identity.
- No expand/collapse on row items. Only popup is the Width dropdown
  (`LabeledDropdown` + focusable Popup + `BackHandler`).
- Empty state: "No rows yet — pick a source below to add one." (`:191`).

### Per-row state shape
`LayoutRowConfig` (from `domain.model`) — fields exercised here: `id: String`,
`kind: LayoutRowKind`, `name: String`, `enabled: Boolean`, `cardStyle:
LayoutCardStyle` (POSTER/LANDSCAPE), `cardWidthDp: Int` (104–140),
`metadata: Map<String, String>`, `viewContext: LayoutScreenScope` (stamped
on add, VM `:258`). All mutations route through `mutateRows` (VM `:354`).

---

## 6. Known Broken Items

Scan scope: `app/src/main/java/com/nuvio/tv/`. Counted **4 TODO markers**,
**0 FIXME/XXX/HACK**, ~292 `catch` sites, **41 `@Suppress`** annotations,
**1 `@Deprecated`**.

### 🔴 Likely Broken
None. No `FIXME`, `XXX`, `HACK`, `TODO()` placeholders, `Unit`-stub bodies,
empty function bodies, or hardcoded `"test"` strings. All four `TODO`s are
tracked and CLAUDE.md-protected ("re-enable for discovery mode later").
No silent exception swallowing in critical paths — auth/sync/repository
catches all log via `Log.e(TAG, ...)`
(e.g. `AuthManager.kt:141-153, 332-335`, `ProfileSelectionViewModel.kt:65-66`).

### 🟡 Worth Reviewing

1. Silent `catch (_: Exception) {}` blocks that may hide real bugs:
   - `ui/screens/plugin/PluginViewModel.kt:52` — `loadLogoBytes()` swallows
     resource-load failure with no log. Same pattern at
     `ui/screens/addon/AddonManagerViewModel.kt:117`.
   - `data/repository/TraktProgressService.kt:1594, 1611` — two
     consecutive ID-resolution fallbacks swallow silently inside
     `resolveTraktId`. A TMDB-API outage would never surface.
   - `core/torrent/TorrServerBinary.kt:94, 137` — stdout-pump and
     shutdown-request catches. The 137 case is fine (graceful shutdown);
     the 94 case silences daemon-thread stdout errors that would mask
     binary crashes mid-stream.
   - `ui/screens/settings/NetworkSettingsScreen.kt:238` — speed-test
     network read swallows; a single failed connection silently
     contributes 0 bytes.

2. `@Deprecated` self-override at
   `ui/screens/player/PlayerRuntimeControllerInitialization.kt:956` —
   `onCues(cues: List<Cue>)` overrides deprecated Media3 callback.
   Annotation is correct but parent API is going away; track upgrade path.

3. Magic-number `delay()` calls without explanatory comments:
   `ui/screens/library/LibraryViewModel.kt:508` (2800ms), `:517` (2200ms);
   `ui/screens/plugin/PluginScreen.kt:136` (3000ms), `:143` (5000ms);
   `ui/screens/player/PlayerRuntimeControllerMetadata.kt:349` (2200ms);
   `ui/screens/player/PlayerRuntimeControllerEngineFailover.kt:58, 125`
   (2200ms each). Likely transient-message timers — would benefit from
   named constants.

4. `core/torrent/TorrServerBinary.kt:125, 142` — `Thread.sleep(1000)` and
   `Thread.sleep(3000)` inside coroutine-friendly code. If on a coroutine,
   should be `delay()`; if on a JVM thread, add a `// blocking — runs on…`
   comment.

### 🟢 Known Tech Debt / Notes

1. Large commented-out blocks — all CLAUDE.md-protected "discovery mode"
   placeholders:
   - `ui/screens/browse/MediaTypeBrowseViewModel.kt:204-237` (29 lines) —
     `reloadContentRowsAutoDiscovery` retained for reference.
   - `ui/screens/home/HomeViewModelCatalogPipeline.kt:136-149` and
     `:844-858` — auto-fetch pipeline + pinToTop collection block, both
     flagged "re-enable for discovery mode later". CLAUDE.md explicitly
     forbids deletion.
   - Other 5+ line contiguous comments are doc-style focus/scroll/immersion
     explanations in `MainActivity.kt:417-421, 692-697, 955-961`,
     `TopNavigationBar.kt`, `HomeScreen.kt` — not dead code.

2. `@Suppress` annotations (41 total) — all benign, three categories:
   - `@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")` (10 sites) —
     overriding Compose Foundation's deprecated
     `BringIntoViewSpec.scrollAnimationSpec`. Required until API
     stabilizes. Locations: `NuvioScrollDefaults.kt:11`,
     `CollectionRowSection.kt:144`, `ContinueWatchingSection.kt:161`,
     `CatalogRowSection.kt:238`, `ClassicHomeContent.kt:134`,
     `ModernHomeContent.kt:888`, `ModernHomeRows.kt:727`,
     `TmdbEntityBrowseScreen.kt:217, 506`.
   - `@Suppress("UNCHECKED_CAST")` (10 sites) — DataStore/serialization
     generics workarounds. `LibraryViewModel.kt:358, 360`,
     `CatalogOrderViewModel.kt:168-176`, `PluginDataStore.kt:213, 220, 228`,
     `ProfileSettingsSyncService.kt:236`, `RepositoryConfigServer.kt:94`,
     `HomeViewModelContinueWatching.kt:275`.
   - `@Suppress("NAME_SHADOWING")` (8 in `ModernHomeRows.kt:449-470`) —
     intentional state-unwrapping idiom.
   - `@SuppressLint("NewApi")` (5 in `AudioOutputRouteDetector.kt`) —
     minSdk is 24 and suppressed APIs (`AudioDeviceInfo`) are 23+.
     Technically unnecessary; can be cleaned up.
   - `@Suppress("FunctionName")` at `FolderPillsDropdown.kt:279` —
     local helper aliased to match `LazyListScope.items`. Fine.
   - `@Suppress("unused")` at `StableHolders.kt:50` — Compose stability
     helper kept for future use.

**Overall:** Code is in good shape. Zero unfinished placeholders, all
swallowed exceptions are in non-critical paths (focus restoration, logo
loading, optional fallbacks), all suppressions/dead-code blocks are
documented or framework-mandated. Top three cleanup candidates: add
`Log.w` to the four silent catches above, extract timer magic numbers
into named constants, drop redundant `@SuppressLint("NewApi")` in
`AudioOutputRouteDetector.kt`.

### Additional dead/duplicate code spotted in this audit
- `Screen.SyncCodeGenerate` / `Screen.SyncCodeClaim` (`Screen.kt:162-163`)
  — never registered, never navigated.
- `Screen.ProfileSelection` — route string unused (rendered imperatively).
- `Screen.Account` — registers a composable that just calls
  `AuthQrSignInScreen` (duplicate of `Screen.AuthQrSignIn`).
- ProfileOverlay's `PILL_CHANNELS → Screen.Home.route` inner-when branch
  (`MainActivity.kt:924`) is unreachable (outer `when` catches first).

---

## 7. File Inventory

### ui/screens/

#### account/
- `AccountScreen.kt` — Account hub screen showing auth status, connected stats, and sync code features.
- `AccountSettingsContent.kt` — Account settings panel content (sign-in/out, linked devices) embedded in Settings hub.
- `AccountUiState.kt` — Data classes for AccountUiState, sync stats, and per-profile sync overview.
- `AccountViewModel.kt` — Account/Supabase auth + sync coordination ViewModel (LARGE: 658 lines).
- `AuthQrSignInScreen.kt` — QR-code-based sign-in screen (TV pairs with phone).
- `AuthSignInScreen.kt` — Initial sign-in chooser (QR vs email/password).
- `InputField.kt` — Reusable TV-styled text input component for auth flows.
- `SyncCodeClaimScreen.kt` — Screen to enter a sync code generated on another device.
- `SyncCodeGenerateScreen.kt` — Screen to generate a sync code for another device to claim.

#### addon/
- `AddonManagementAccess.kt` — Helper deciding addon management read-only and web-config mode by profile/experience.
- `AddonManagerScreen.kt` — Addon manager UI (install/remove/configure Stremio addons) (LARGE: 1323 lines).
- `AddonManagerUiState.kt` — Data class for addon manager screen state.
- `AddonManagerViewModel.kt` — Addon list + install/uninstall + config logic (LARGE: 892 lines).
- `CatalogOrderScreen.kt` — Screen to reorder catalogs across installed addons.
- `CatalogOrderViewModel.kt` — Persists catalog ordering preference.
- `EssentialAddonSetupScreen.kt` — First-run guided addon install for Essential experience mode.

#### browse/
- `MediaTypeBrowseScreen.kt` — Polymorphic browse body for a single media type; dispatches by HomeLayout (LARGE: 649 lines).
- `MediaTypeBrowseViewModel.kt` — ViewModel providing catalog tiles + folder tiles for media-type browse.

#### cast/
- `CastDetailScreen.kt` — Cast/person detail screen with bio + filmography (LARGE: 710 lines).
- `CastDetailUiState.kt` — Sealed UiState (Loading/Success/Error) for cast detail.
- `CastDetailViewModel.kt` — Loads TMDB person detail + credits.

#### collection/
- `CollectionEditorCatalogPicker.kt` — Catalog-source picker pane inside the collection editor.
- `CollectionEditorControls.kt` — Shared form controls (NuvioTextField, etc.) for the collection editor.
- `CollectionEditorFolderContent.kt` — Folder-editor pane within the collection editor (LARGE: 841 lines).
- `CollectionEditorGenreEmojiPickers.kt` — Genre + emoji picker dialogs for collection folders.
- `CollectionEditorScreen.kt` — Top-level collection editor screen orchestrating sub-panes.
- `CollectionEditorTmdbPicker.kt` — TMDB-source picker (discover/network/genre filters) for a folder (LARGE: 907 lines).
- `CollectionEditorTraktPicker.kt` — Trakt-list source picker for a folder.
- `CollectionEditorViewModel.kt` — State + persistence for the collection editor (LARGE: 1147 lines).
- `CollectionManagementScreen.kt` — Top-level Collections list management screen (LARGE: 639 lines).
- `CollectionManagementViewModel.kt` — Import/export + reorder + delete of collections.
- `CollectionRailViewModel.kt` — Drives the "collection mode" of the top channel rail (folder pills replace network pills).
- `CollectionsHomeScreen.kt` — Home-style screen rendering each collection as a row of folder cards; layout-locked.
- `CollectionsHomeViewModel.kt` — Provides collection list for CollectionsHomeScreen.
- `FolderDetailScreen.kt` — Detail screen showing the contents of a single collection folder (LARGE: 770 lines).
- `FolderDetailViewModel.kt` — Loads folder items from its underlying catalog source (LARGE: 1228 lines).

#### detail/
- `CastSection.kt` — Cast members horizontal row composable in the detail screen.
- `CollectionSection.kt` — Movie-collection (e.g. franchise) section on the detail screen.
- `CommentsSection.kt` — Trakt comments/reviews section with spoiler reveals (LARGE: 1102 lines).
- `CompanyLogosSection.kt` — Production company / network logos row.
- `DateFormat.kt` — `formatReleaseDate()` helper for ISO date strings.
- `EpisodeRatingsSection.kt` — Per-episode ratings (IMDB/MDBList) display.
- `EpisodesSection.kt` — Season + episode list with focus restore (LARGE: 1187 lines).
- `FocusRestoreUtils.kt` — Helpers to remember/restore focus when returning to detail.
- `HeroSection.kt` — Hero (backdrop + logo + buttons) section of the detail screen (LARGE: 988 lines).
- `MetaDetailsScreen.kt` — Top-level movie/series detail screen (LARGE: 2292 lines).
- `MetaDetailsUiState.kt` — Detail UI state + MoreLikeThisSource enum.
- `MetaDetailsViewModel.kt` — Detail data orchestration (addon meta + TMDB + Trakt + ratings) (LARGE: 2575 lines).
- `MoreLikeThisSection.kt` — Recommendations row using TMDB / addon similar items.
- `SharedTrailerOverlay.kt` — Trailer seek-overlay state container shared with the trailer player.
- `TrailerSection.kt` — Trailer list section composable on the detail screen.

#### home/
- `AlwaysCrossfadeTransitionFactory.kt` — Coil transition factory forcing crossfade on every image swap.
- `ChannelRailViewModel.kt` — Channel/folder pill data flow for the top channel rail.
- `ClassicFocusGradientBackdrop.kt` — Classic-layout focused-poster gradient backdrop renderer.
- `ClassicHomeContent.kt` — Classic home layout content composable (LARGE: 751 lines).
- `GridHomeContent.kt` — Grid home layout content composable (catalog-tile grid + hero strip) (LARGE: 856 lines).
- `HeroBackdropState.kt` — Global holder for current hero backdrop URL shared with the modern shell.
- `HomeScreenFocusState.kt` — Stores focus/scroll state for HomeScreen to enable restoration on back.
- `HomeScreen.kt` — Top-level home/movies/TV screen entry point delegating to layout-specific content (LARGE: 966 lines).
- `HomeUiState.kt` — Data class for home screen UI state.
- `HomeViewModelCatalogPipeline.kt` — Catalog-loading + paging pipeline (mixin for BaseHomeViewModel) (LARGE: 1213 lines).
- `HomeViewModelCatalogUtils.kt` — Catalog-key helpers and small utilities for the home VM pipeline.
- `HomeViewModelContinueWatching.kt` — Continue Watching + Next Up enrichment pipeline (LARGE: 2826 lines).
- `HomeViewModel.kt` — Concrete HOME-scope ViewModel + open BaseHomeViewModel (LARGE: 924 lines).
- `HomeViewModelLibraryActions.kt` — Library add/remove + watched-toggle mixin actions.
- `HomeViewModelPresentationPipeline.kt` — Layout-prefs presentation pipeline that merges into HomeUiState (LARGE: 888 lines).
- `ModernHomeContent.kt` — Modern home layout content composable (LARGE: 1158 lines).
- `ModernHomeHero.kt` — Modern hero section (full-screen backdrop + meta + trailer) (LARGE: 730 lines).
- `ModernHomeModels.kt` — Internal data models + constants for modern home rendering (LARGE: 709 lines).
- `ModernHomePresentation.kt` — Modern presentation input/output mapping helpers.
- `ModernHomeRows.kt` — Individual modern home row composables (LARGE: 1415 lines).
- `ModernHomeRowsList.kt` — Lazy list assembling the modern home rows.

#### library/
- `LibraryScreen.kt` — "My Stuff" library screen with list tabs (LARGE: 1079 lines).
- `LibraryViewModel.kt` — Library list state + per-type tabs (LARGE: 698 lines).

#### movies/
- `MoviesScreen.kt` — Movies screen — thin wrapper around HomeScreen scoped to MOVIES.
- `MoviesViewModel.kt` — HomeViewModel specialization for the MOVIES scope.

#### player/
- `AudioDelayMediaSource.kt` — MediaSource wrapper that injects per-device audio delay.
- `AudioOutputRouteDetector.kt` — Detects current audio output route (speakers/HDMI/Bluetooth) for per-route delay.
- `AudioSelectionOverlay.kt` — Audio-track selection overlay UI (LARGE: 615 lines).
- `CustomDefaultTrackNameProvider.kt` — Track-name provider that surfaces codec details (TrueHD/DTS-HD).
- `DisplayModeOverlay.kt` — Aspect/scale + refresh-rate overlay shown via player menu.
- `EpisodesSidePanel.kt` — Side panel listing episodes with focus + auto-scroll (LARGE: 598 lines).
- `GainAudioProcessor.kt` — Custom audio processor applying gain (dB amplification).
- `LoadingOverlay.kt` — Loading spinner overlay shown during initial player buffer.
- `NextEpisodeCardOverlay.kt` — End-of-episode "Up Next" card overlay.
- `NuvioMpvSurfaceView.kt` — SurfaceView subclass hosting the MPV native player (LARGE: 616 lines).
- `ParentalGuideOverlay.kt` — Parental guide / age-rating overlay shown before playback.
- `PauseOverlay.kt` — Pause-state overlay with metadata + scrubber.
- `PlaybackSpeedAwareAudioRenderer.kt` — Audio renderer respecting playback speed for pitch correction.
- `PlaybackSpeedAwareAudioSink.kt` — Wrapping AudioSink companion for the speed-aware renderer.
- `PlayerAspectScaleUtils.kt` — `AspectMode` enum and helpers for aspect/scale modes.
- `PlayerDisplayModeUtils.kt` — Helpers mapping display modes to ExoPlayer resize values.
- `PlayerFrameRateHeuristics.kt` — Frame-rate detection heuristics for AFR (auto frame rate).
- `PlayerLibassCompat.kt` — Libass (ASS subtitle) compatibility shim for ExoPlayer.Builder.
- `PlayerLibassExtensions.kt` — Libass render-type extension converters.
- `PlayerMediaSessionMetadata.kt` — Builds Now-Playing MediaMetadata for external controllers.
- `PlayerMediaSourceFactory.kt` — Factory for ExoPlayer MediaSource with custom extractors (LARGE: 539 lines).
- `PlayerNavigationArgs.kt` — Internal nav-arg bag passed into the player.
- `PlayerNextEpisodeRules.kt` — Rules to resolve the next episode from current playback state.
- `PlayerOverlayScaffold.kt` — Scaffold composable hosting all player overlays.
- `PlayerPlaybackNetworking.kt` — Trust-all SSL setup + OkHttp config for playback networking.
- `PlayerRuntimeControllerAfrPreflight.kt` — Auto-frame-rate preflight check before playback starts.
- `PlayerRuntimeControllerAudioDelayRoutes.kt` — Per-route audio-delay apply/persist logic.
- `PlayerRuntimeControllerEngineFailover.kt` — Auto-switch between internal players (ExoPlayer/MPV) on startup error (LARGE: 862 lines).
- `PlayerRuntimeControllerErrorRecovery.kt` — Retry + recovery overlay on playback errors.
- `PlayerRuntimeControllerInitialization.kt` — Player initialization (engine selection, audio language, AFR) (LARGE: 1028 lines).
- `PlayerRuntimeController.kt` — Top-level player runtime controller class.
- `PlayerRuntimeControllerLifecycle.kt` — Release/teardown lifecycle helpers.
- `PlayerRuntimeControllerMetadata.kt` — Fetches meta details for current playback context.
- `PlayerRuntimeControllerMpv.kt` — MPV-specific attach/initialize/release (LARGE: 501 lines).
- `PlayerRuntimeControllerObservers.kt` — Player observers (tracks, subtitles, position) (LARGE: 526 lines).
- `PlayerRuntimeControllerPlaybackEvents.kt` — Playback events + audio amplification handlers (LARGE: 1189 lines).
- `PlayerRuntimeControllerScrobble.kt` — Trakt scrobble + episode mapping cache helpers.
- `PlayerRuntimeControllerStartup.kt` — Activity attach + startup wiring.
- `PlayerRuntimeControllerStreams.kt` — Episode stream selection + next-episode stream search (LARGE: 1184 lines).
- `PlayerRuntimeControllerSubtitleTiming.kt` — Subtitle auto-sync + timing dialog logic.
- `PlayerRuntimeControllerTorrent.kt` — Torrent-stream lifecycle management.
- `PlayerRuntimeControllerTrackSelection.kt` — Audio/subtitle/video track filtering by addon/language (LARGE: 615 lines).
- `PlayerRuntimeControllerTracks.kt` — Available-track parsing/updates for ExoPlayer + MPV (LARGE: 1442 lines).
- `PlayerScreen.kt` — Top-level player Composable hosting overlays + surface (LARGE: 2647 lines).
- `PlayerSubtitleCueParser.kt` — Parser for SRT/WebVTT/SSA cue timing.
- `PlayerSubtitleUtils.kt` — Language-code normalization and subtitle util object.
- `PlayerUiState.kt` — Data class for player UI state (visibility flags, current track, etc.).
- `PlayerViewModel.kt` — Hilt ViewModel wrapping PlayerRuntimeController.
- `SkipIntroButton.kt` — Floating Skip Intro/Outro/Recap button shown during skip intervals.
- `StreamComponents.kt` — `StreamItem` row composable for stream lists.
- `StreamInfoOverlay.kt` — Stream info overlay (codec/bitrate/resolution).
- `StreamSourcesSidePanel.kt` — Side panel listing alternate stream sources mid-playback.
- `SubtitleDelayConfig.kt` — Constants for subtitle-delay min/max in ms.
- `SubtitleSelectionOverlay.kt` — Subtitle-track selection overlay (LARGE: 1901 lines).
- `SubtitleStyleSidePanel.kt` — Subtitle style (color/size/font) side panel.
- `SubtitleTimingDialog.kt` — Manual + auto subtitle timing adjustment dialog.
- `TorrentOverlay.kt` — Torrent download/peer-info overlay during torrent playback.

#### plugin/
- `PluginScreen.kt` — Plugin (scraper) manager UI (LARGE: 1496 lines).
- `PluginUiState.kt` — Data class for the plugin manager UI state.
- `PluginViewModel.kt` — Plugin install/enable/disable management ViewModel.

#### profile/
- `ProfileSelectionScreen.kt` — Profile selection / creation screen at app launch (LARGE: 2155 lines).
- `ProfileSelectionViewModel.kt` — Profile list + create/edit/delete ViewModel.

#### search/
- `DiscoverScreen.kt` — Standalone Discover screen entry point.
- `SearchDiscoverSection.kt` — Discover (trending / popular) section composable used inside Search/Discover (LARGE: 743 lines).
- `SearchEvent.kt` — Sealed `SearchEvent` interface (query/clear/select).
- `SearchScreen.kt` — Search screen with query bar + results (LARGE: 961 lines).
- `SearchUiState.kt` — Data class for search UI state.
- `SearchViewModel.kt` — Search/discovery ViewModel (LARGE: 828 lines).

#### settings/
- `AboutScreen.kt` — About / version / credits screen.
- `AddRowPickerDialog.kt` — Dialog listing catalog sources to add as a layout row.
- `AdvancedSettingsViewModel.kt` — Advanced settings (fast horizontal nav, etc.) ViewModel.
- `AnimeSkipSettingsScreen.kt` — AnimeSkip API integration settings screen.
- `AnimeSkipSettingsViewModel.kt` — ViewModel for AnimeSkip settings persistence.
- `AppearanceRowsScreen.kt` — Wrapper for the rows-only mode of NewLayoutSettingsContent, scoped to a screen.
- `ContinueWatchingSettingsContent.kt` — Continue Watching settings panel content.
- `DebugSettingsScreen.kt` — Developer/debug toggles screen.
- `DebugSettingsViewModel.kt` — Debug settings persistence ViewModel.
- `EssentialPlaybackSettingsContent.kt` — Simplified playback settings panel for Essential experience mode.
- `ExperienceModeConfirmationDialog.kt` — Confirm-switch dialog when changing experience mode.
- `ExperienceModeSettingsViewModel.kt` — ViewModel for the experience-mode picker.
- `GlobalSettingsContent.kt` — Global fallback layout + display preferences (settings hierarchy floor).
- `LayoutSettingsScreen.kt` — Old (dormant) layout settings screen — exempt per project rules (LARGE: 1012 lines).
- `LayoutSettingsViewModel.kt` — Old layout settings ViewModel (LARGE: 533 lines).
- `MDBListSettingsScreen.kt` — MDBList API key + visible-ratings settings.
- `MDBListSettingsViewModel.kt` — ViewModel for MDBList settings persistence.
- `NavigationFeelContent.kt` — Feel picker (Modern vs Legacy) — Settings > Appearance > Feel.
- `NetworkSettingsScreen.kt` — Network / cache settings screen (LARGE: 549 lines).
- `NewLayoutSettingsScreen.kt` — New layout & rows settings screen with per-screen scope (LARGE: 1019 lines).
- `NewLayoutSettingsViewModel.kt` — ViewModel for the new layout/rows settings; provides CatalogSourceOption picker data.
- `PlaybackAudioSettings.kt` — Trailer + audio-section settings items used by PlaybackSettingsScreen (LARGE: 634 lines).
- `PlaybackAutoPlaySettings.kt` — Auto-play settings items used by PlaybackSettingsScreen (LARGE: 1148 lines).
- `PlaybackSettingsScreen.kt` — Full playback settings screen for Advanced mode (LARGE: 1391 lines).
- `PlaybackSettingsSections.kt` — Playback settings section enum + dispatchers (LARGE: 1191 lines).
- `PlaybackSettingsViewModel.kt` — ViewModel persisting all player settings.
- `PlaybackSubtitleSettings.kt` — Subtitle style/color settings items (LARGE: 503 lines).
- `ProfileSettingsContent.kt` — Profile-management panel inside Settings hub.
- `ProfileSettingsViewModel.kt` — Profile settings ViewModel.
- `RowPickerDialogs.kt` — Catalog/collection/trakt picker dialogs used by the rows editor (LARGE: 608 lines).
- `SettingsDesignSystem.kt` — Constants (radii, paddings) + shared settings UI primitives (LARGE: 648 lines).
- `SettingsHubScreen.kt` — Two-pane Settings hub (left rail of categories + right content pane) (LARGE: 675 lines).
- `SettingsScreen.kt` — Older flat settings screen with category enum (LARGE: 1032 lines).
- `SettingsScrollIndicators.kt` — Up/Down chevron scroll indicators for long settings columns.
- `SupportersContributorsScreen.kt` — Supporters + Contributors tabs screen (LARGE: 1551 lines).
- `SupportersContributorsViewModel.kt` — Loads supporters + contributors data.
- `ThemeSettingsScreen.kt` — Theme/accent color picker screen.
- `ThemeSettingsViewModel.kt` — Theme settings persistence ViewModel.
- `TmdbSettingsScreen.kt` — TMDB API key + per-group enrichment toggles screen.
- `TmdbSettingsViewModel.kt` — TMDB settings persistence ViewModel.
- `TopBarSettingsContent.kt` — Top Bar settings — pill order, display mode, visibility.
- `TraktScreen.kt` — Trakt connection + sync settings screen (LARGE: 781 lines).
- `TraktViewModel.kt` — Trakt connection mode + sync ViewModel (LARGE: 518 lines).

#### stream/
- `StreamScreen.kt` — Stream source list screen (manual source picker) (LARGE: 1204 lines).
- `StreamScreenUiState.kt` — Data class for stream screen UI state.
- `StreamScreenViewModel.kt` — Aggregates streams from addons + plugins (LARGE: 795 lines).

#### tmdb/
- `TmdbEntityBrowseScreen.kt` — TMDB entity (network / company / keyword) browse screen (LARGE: 616 lines).
- `TmdbEntityBrowseUiState.kt` — Sealed UI state for TMDB entity browse.
- `TmdbEntityBrowseViewModel.kt` — Loads TMDB entity items with pagination.

#### tv/
- `TvShowsScreen.kt` — TV Shows screen — thin wrapper around HomeScreen scoped to TV.
- `TvShowsViewModel.kt` — HomeViewModel specialization for the TV scope.

#### (top-level)
- `CatalogSeeAllScreen.kt` — "See all" grid screen for a single catalog source.
- `ExperienceModeSelectionScreen.kt` — First-run picker between Essential and Advanced experience modes.
- `LayoutSelectionScreen.kt` — First-run picker for default HomeLayout (Classic/Grid/Modern).

### ui/components/
- `AccentFocusHighlight.kt` — Shared accent-fill focus highlight modifier + `AccentToggleRow` / `AccentActionRow` composables.
- `AutoResizeText.kt` — Single-line text that shrinks font size to fit width.
- `AvatarPickerGrid.kt` — Avatar selection grid (color swatches + image avatars) for profile creation.
- `CatalogRowSection.kt` — Horizontal catalog row (LazyRow of ContentCard) with scroll-into-view behavior.
- `CategoryPillsViewModel.kt` — Source of truth for Modern TopBar pill order + visibility (Hilt VM).
- `CollectionCardGlow.kt` — Artwork-backed CardGlow color computation from sampled image bitmap.
- `CollectionFolderCardMedia.kt` — Helper picking the cover image URL for a collection folder card.
- `CollectionRowSection.kt` — Horizontal collection-folder row composable.
- `CollectionsDropdown.kt` — Dropdown popup listing collections (opened on Collections pill long-press).
- `ContentCard.kt` — Primary poster/landscape content card with focus animation + selection check (LARGE: 600 lines).
- `ContinueWatchingProgressLabel.kt` — `formatContinueWatchingProgressLabel()` formatter (percent / time-left).
- `ContinueWatchingSection.kt` — Continue Watching horizontal row (LARGE: 706 lines).
- `EmptyScreenState.kt` — Generic empty-state placeholder with icon + title + subtitle.
- `ErrorState.kt` — Generic error state with retry button.
- `FolderPillsDropdown.kt` — Dropdown popup for choosing which folder pills appear on the channel rail.
- `GridContentCard.kt` — Grid-layout content card variant (taller landscape style).
- `GridContinueWatchingSection.kt` — Continue Watching row variant tuned for Grid layout.
- `HeroCarousel.kt` — Hero carousel composable used by Classic/Grid layouts.
- `LayoutPreviewAnimation.kt` — Animated Classic/Grid/Modern layout preview canvases for the picker.
- `LoadingIndicator.kt` — Circular progress indicator with brand color.
- `MonochromePosterPlaceholder.kt` — Placeholder graphic shown when a poster fails to load.
- `NuvioDialog.kt` — Themed dialog scaffold (title + subtitle + content slot).
- `NuvioScrollDefaults.kt` — Custom BringIntoViewSpec spring tuning for smooth scrolling.
- `NuvioTopBar.kt` — Simple branded top bar with app name (used on auth/setup screens).
- `P2pConsentDialog.kt` — Consent dialog asking the user to enable P2P torrent streaming.
- `PlaceholderShimmer.kt` — Shimmer animation modifier for skeleton placeholders.
- `PosterCardDefaults.kt` — `PosterCardStyle` data class + default dims for poster cards.
- `ProfileAvatarCircle.kt` — Circular profile avatar showing image or color+initial fallback.
- `ProfileOverlay.kt` — Modern-feel glassmorphism overlay panel (Profile/Search/Discover/Settings/Pill Channels).
- `SidebarNavigation.kt` — Stub file (sidebar removed; just a comment).
- `SideRail.kt` — Legacy Prime-Video-style side rail navigation with `SideRailItem` enum.
- `Skeletons.kt` — Skeleton placeholders (streams list, posters, rows).
- `SourceStatusFilterChip.kt` — FilterChip with per-source loading/success/error status indicator.
- `TopBarImmersionState.kt` — Global object holding TopBar visibility state for immersion mode.
- `TopNavBar.kt` — Older top nav bar (`NavItem` data class + simple pill row).
- `TopNavigationBar.kt` — Current TopBar composable for both feels (avatar + categories + channels) (LARGE: 830 lines).
- `TrailerPlayer.kt` — Embedded trailer player using ExoPlayer + PlayerView.

#### posteroptions/
- `PosterOptionsController.kt` — Reusable controller backing the long-press poster options menu.
- `PosterOptionsDialog.kt` — Poster options dialog (Library/Watched/Lists actions).
- `PosterOptionsState.kt` — Immutable state for the poster options dialog.
- `PosterOptionsViewModel.kt` — Standalone Hilt VM wrapping PosterOptionsController for screens lacking their own VM.

### ui/navigation/
- `NuvioNavHost.kt` — NavHost wiring + composable destinations for all screens (LARGE: 1232 lines).
- `Screen.kt` — Sealed class of route definitions + URL-encoded `createRoute()` builders.
- `TvDpadNavigation.kt` — `dpadUpToTopNav()` modifier routing D-pad Up to top nav when no focusable above.

### domain/model/
- `Addon.kt` — Stremio addon manifest model + CatalogDescriptor + behavior hints.
- `AppFont.kt` — App font enum (Inter, DM Sans, Open Sans).
- `AppTheme.kt` — App accent-color theme enum (Crimson, Ocean, Violet, etc.).
- `AuthState.kt` — Sealed auth state (SignedOut / Loading / FullAccount).
- `CatalogDescriptorExtensions.kt` — Catalog descriptor extension fns (`supportsExtra`, `skipStep`).
- `CatalogRow.kt` — Per-catalog row model (items + paging state).
- `CategoryPill.kt` — Category pill enum + visibility + order entry models for Modern TopBar.
- `Collection.kt` — Collection + folder + collection-source models (addon catalog / TMDB / Trakt).
- `ContentType.kt` — Content type enum (MOVIE/SERIES/CHANNEL/TV).
- `ExperienceMode.kt` — Experience mode enum (ESSENTIAL / ADVANCED).
- `Feel.kt` — Modern / Legacy navigation feel enum + storage value parser.
- `FocusedPosterTrailerPlaybackTarget.kt` — Enum for trailer playback target (expanded card vs hero).
- `FolderViewMode.kt` — Folder view-mode enum (TABBED_GRID / ROWS / FOLLOW_LAYOUT).
- `HomeLayout.kt` — Home layout enum (CLASSIC / GRID / MODERN).
- `LayoutRowConfig.kt` — Per-row layout config + `LayoutRowKind` + `LayoutRowKey` builder.
- `LibraryModels.kt` — Library entry + list/tab models + MetaPreview conversion.
- `MDBListRatings.kt` — MDBList ratings data class + result wrapper.
- `MDBListSettings.kt` — MDBList settings data class.
- `Meta.kt` — Full meta details model (cast, videos, ratings, links, trailers, etc.).
- `MetaPreview.kt` — Lightweight meta preview model used in catalog rows.
- `PersonDetail.kt` — TMDB person detail model with movie + tv credits.
- `Plugin.kt` — Plugin / repository / manifest models (NUVIO_JS + EXTERNAL_DEX).
- `PosterShape.kt` — Poster shape enum with aspect ratio (POSTER/LANDSCAPE/SQUARE).
- `ProfileAvatarColors.kt` — Constant list of default avatar color hex values.
- `SavedLibraryItem.kt` — Saved-library entry model (legacy local library shape) with MetaPreview conversion.
- `Stream.kt` — Stremio stream source model + helpers (URL / torrent / YouTube detection).
- `Subtitle.kt` — Subtitle source model with language display formatting.
- `TmdbSettings.kt` — TMDB enrichment settings (per-group toggles + language).
- `TraktCommentReview.kt` — Trakt comment/review data class with spoiler flag.
- `UserProfile.kt` — User profile model (id, name, avatar color/image).
- `WatchedItem.kt` — Watched-history item record.
- `WatchProgress.kt` — Watch progress record with local + Trakt source variants and thresholds.
