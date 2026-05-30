# NuvioTV — Project Rules & Architecture Reference

> **Full session history archived in [`SESSION_HISTORY.md`](./SESSION_HISTORY.md).**
> Old session logs are summarized below as one-liners; the last 2 sessions
> are kept in full.

This file complements the per-user global rules in `~/.claude/CLAUDE.md`.
It documents project-wide conventions and the architecture of the
**Feel** dual-navigation system shipped on 2026-05-18.

---

## Project Overview

- **App:** NuvioTV fork (Android TV, Kotlin + Jetpack Compose)
- **Build:** `./gradlew :app:assembleFullDebug`
- **Deploy:** `./gradlew :app:installFullDebug` (ADB push to connected TV)
- **ADB connect:** `adb connect <tv-ip>:5555` (IP changes — check device list first)
- **Target device APK variant:** `app-full-armeabi-v7a-debug.apk`
- **Architecture:** Clean — `core/`, `data/`, `domain/`, `ui/`
- **Forks:**
  - `origin` → `git@github.com:dee9law/NuvioTV.git` (your fork)
  - `upstream` → `https://github.com/NuvioMedia/NuvioTV.git` (original)

---

## 🚦 Feel — Dual Navigation System (2026-05-18)

The app ships **two completely separate navigation shells** controlled by
a single per-profile DataStore key (`navigation_feel` in
`LayoutPreferenceDataStore`). Switching feels is live — no restart.

### Feel.MODERN — new default for fresh installs

- SideRail **hidden entirely** (not deleted from code — gated)
- Content area runs **edge-to-edge**
- TopBar carries: **profile avatar (far left)** → **category pills**
  (icon-on-top, text-below; reorderable; per-pill icons-only toggle) →
  **channel pills** (Pill Channels, horizontally scrollable to the right
  screen edge)
- **Profile Overlay** (glassmorphism panel cascading from top-left) is
  the home for Search / Discover / My Stuff / Settings / Pill Channels +
  any demoted category pills (Hidden Items section)
- D-pad Left at content carousel index 0 → opens Profile Overlay
  (reuses the `LocalSideRailController` plumbing — same gate, new
  destination)
- Long-press on Home/Movies/TV Shows → enters **Edit Mode** (Collections
  long-press still opens `CollectionsDropdown`)
- Loop scrolling on the TopBar: avatar ↔ first category ↔ last channel
  ↔ avatar

### Feel.LEGACY — preserved verbatim

- Prime-Video–style SideRail with Profile → Search → Home → My Stuff →
  **Pill Channels (new)** → Settings
- TopBar with side-by-side icon+text category pills + channel pills
- All current focus chains, immersion mode, and Back Level-Up
  hierarchy unchanged
- Loop scrolling: first category ↔ last channel ↔ first category
- No Edit Mode, no Profile Overlay

### Gate point

`MainActivity.TopNavBarScaffold` reads `navigationFeel` via
`LayoutPreferenceDataStore.navigationFeel`, computes `isModernFeel`,
and branches:
- SideRail render: `if (showTopNav && !isModernFeel) { SideRail(...) }`
- `LocalSideRailController` provides `openProfileOverlay` (Modern) or
  `openSideRail` (Legacy)
- ProfileOverlay only mounted when Modern
- Category pill list source: dynamic from `CategoryPillsViewModel`
  (Modern) vs fixed `["Home", "Movies", "TV Shows", "Collections"]`
  (Legacy)

---

## 📁 New files (2026-05-17 → 2026-05-18)

### domain/model/

| File | Purpose |
|---|---|
| `Feel.kt` | `enum class Feel(val storageValue: String) { MODERN, LEGACY }` + `fromStorageValue()` that defaults to `MODERN` for null/unknown. Foundation for the navigation-feel branching. |
| `CategoryPill.kt` | `enum class CategoryPill(storageId, displayLabel) { HOME, MOVIES, TV_SHOWS, COLLECTIONS }` + `PillVisibility { TOPBAR, DRAWER }` + `data class CategoryPillOrderEntry(pill, visibility, iconsOnly)` (the last for G-E icons-only display mode). |

### data/local/

| File | Purpose |
|---|---|
| `CategoryPillOrderDataStore.kt` | Per-profile persistence (via `ProfileDataStoreFactory`) of the ordered pill list with per-pill visibility + iconsOnly. Reconciles persisted JSON with current `CategoryPill.entries` so a future build adding a fifth pill doesn't lose user state. |

### ui/components/

| File | Purpose |
|---|---|
| `CategoryPillsViewModel.kt` | Source of truth for the Modern TopBar's pill list. Exposes `order` / `topbarPills` / `drawerPills` flows. Methods: `swap`, `moveUp`, `moveDown`, `demote`, `promote`, `setVisible`, `setIconsOnly`. Enforces Home-protected + ≥2-on-TopBar invariants. |
| `ProfileOverlay.kt` | Glassmorphism panel (~280dp, 0.85-alpha black, slide-down + fade-in 250ms FastOutSlowInEasing). Rows: Profile header → Search → Discover → My Stuff → Settings → Pill Channels → (optional) Hidden Items. Vertical wrap with `focusProperties { up/down }` on first/last rows. |
| `AccentFocusHighlight.kt` | Shared focus highlight modifier (`Modifier.accentFocusHighlight`) + `AccentToggleRow` / `AccentActionRow` composables. Toggles now color label with theme accent when ON, dim when OFF. (Pre-Feel session.) |

### ui/screens/settings/

| File | Purpose |
|---|---|
| `NavigationFeelContent.kt` | Feel picker — Settings → Appearance → "Feel". Two preview cards (schematic mockups of Modern vs Legacy shells). Writes through `LayoutPreferenceDataStore.setNavigationFeel`. |
| `TopBarSettingsContent.kt` | Top Bar settings — Settings → Appearance → "Top Bar". Per-row: pill name, ↑/↓ reorder buttons, Icon-only display-mode toggle, visibility Switch (Home disabled, ≥2 invariant). Syncs with `CategoryPillsViewModel`. |

---

## 🎯 Profile Overlay triggers (Modern only)

1. **Profile avatar Select** — `MainActivity` sets `showProfileOverlay = true`
   on the avatar's `onProfileClick` callback.
2. **D-pad Left at content carousel index 0** — the existing
   `LocalSideRailController` mechanism (used by all home/movies/TV
   screens via the `tvLeftFromFirstItemToSideRail` pattern). In
   Modern feel, MainActivity provides `openProfileOverlay` instead of
   `openSideRail` — same call site, new destination.

Dismiss: Back key (overlay's own `BackHandler`), D-pad Right anywhere
inside the panel (`onPreviewKeyEvent` traps it), or tapping the scrim.
On dismiss, focus restores to the TopBar (`navBarFr.requestFocus()`).

---

## ✏️ Edit Mode mechanics

Entry: long-press on **any non-Collections category pill** in Modern
feel. Long-press detection: `KeyEvent.nativeKeyEvent.repeatCount == 1`
(fires once after the system's ~500ms threshold). Collections pill
long-press still opens `CollectionsDropdown` (preserved per the original
spec — CLAUDE.md rule).

State (lives in `MainActivity.TopNavBarScaffold`):
- `var editMode: Boolean` — true when reordering
- Focused pill = grabbed pill (no separate grab state)
- Each pill wrapped in `key(label) { CategoryTabItem(...) }` so the
  same Card instance moves on reorder — **focus + internal state
  follow the pill, not the position**

Key bindings while editMode is true (handled in `CategoryTabItem.onEditKey`):
- **D-pad Left/Right** — swap with adjacent pill; **wraps within
  category group only** (never spills into channel pills). Position 0
  Left wraps to last category; lastIndex Right wraps to 0
- **D-pad Down** — `pillsVm.demote(pill)` (with Home + ≥2 guards)
- **Select / OK** — exit edit mode (drop in place)
- **Back / Escape** — exit edit mode (composed in MainActivity as
  `BackHandler(enabled = editMode)`)

Visual cues:
- Every pill gets a faint accent border (the "moveable" hint)
- Grabbed pill: scale 1.12 + 2dp accent border
- "Done" pill renders after the category zone — Select exits edit mode

Persistence: every swap/demote/promote saves to
`CategoryPillOrderDataStore` immediately (no commit step).

Invariants enforced in `CategoryPillsViewModel`:
- `CategoryPill.HOME` cannot be demoted (silently rejected)
- TopBar pill count cannot drop below 2 (demote rejected if it would)

Promotion: `ProfileOverlay`'s Hidden Items section renders demoted
pills with a `+` icon. Tapping promotes back to the rightmost TopBar
position **and** navigates to that pill's route in one motion.

---

## 🔄 Loop scrolling scope (Task G-C)

### TopBar — both feels
- **Modern:** avatar (leftmost) ↔ first category ↔ last channel
  (rightmost) → wraps back to avatar
- **Legacy:** first category (leftmost) ↔ last channel (rightmost) →
  wraps back to first category
- Implementation:
  - Avatar / first category pill: `onPreviewKeyEvent` on Left key →
    calls `wrapToLastChannel()` which does
    `listState.scrollToItem(channels.lastIndex) + withFrameNanos + channelFr.requestFocus()`
  - Last channel pill: `onPreviewKeyEvent` on Right key →
    `wrapToLeftmostBarItem()` which focuses the avatar (Modern) or
    first category pill (Legacy)
  - Edit Mode L/R: wraps within the category list (the `onEditSwap`
    callback computes the target index modulo `categories.size`)

### Popups & dropdowns
- **`ProfileOverlay`** — wired via `focusProperties { up / down }` on
  the boundary rows (Profile header = wrapTop, last visible row =
  wrapBottom which is either Pill Channels or the last hidden item)
- **`CollectionsDropdown`** — first/last `DropdownItem` get
  `wrapUpTo` / `wrapDownTo` requesters
- **`FolderPillsDropdown`** — first/last `FolderRow` get
  `wrapUpTo` / `wrapDownTo` (skipping section headers since they're
  not focusable)

### Not yet wired (see Pending Follow-ups)
- Settings hub left rail (`SettingsHubScreen`'s `LazyColumn` of
  category cards — long list, would need scroll-into-view helper)
- Misc confirmation dialogs and any other vertical menu

---

## ⚙️ Top Bar settings (Settings → Appearance → "Top Bar")

Visible for **both feels** (Legacy spec is explicit: shows the four
category pills only). For each pill:
- Name on the left
- ↑/↓ reorder buttons (call `pillsVm.moveUp/Down`)
- **Display mode** single-press toggle: "Icon + text" ↔ "Icon only"
  (writes `iconsOnly` flag on the `CategoryPillOrderEntry`; rendered
  by `CategoryTabItem`'s new `iconsOnly` short-circuit path)
- Visibility **Switch** — Home disabled; remaining pills disabled when
  removing them would drop topbar count below 2

Changes apply immediately. Reorder syncs with Edit Mode (same
`CategoryPillsViewModel` / `CategoryPillOrderDataStore`).

---

## 📺 Pill Channels (renamed from "Networks")

The old `+ Networks` button at the right edge of the TopBar is **gone
in both feels**. Channel pills now scroll free to the right screen edge.
Pill Channels management (the `FolderPillsDropdown` for toggling which
folder pills appear) moves to:

- **Modern feel:** `Pill Channels` row in the Profile Overlay menu
  (icon: `Icons.Default.Tune`). Routes to
  `ProfileOverlayDestination.PILL_CHANNELS` → MainActivity closes the
  overlay and opens the existing `FolderPillsDropdown` popup.
- **Legacy feel:** `Pill Channels` item in the SideRail expanded menu,
  between My Stuff and Settings (new `SideRailItem.PillChannels` enum
  value + `onPillChannelsClick` parameter on `SideRail`).

`FolderPillsDropdown.FolderRow` also gained an inline `AsyncImage` for
the folder's `titleLogoUrl` so picker rows visually match the channel
pills they govern.

---

## 🐛 Collections long-press timing fix

`CollectionsDropdown` now waits **4 frames + 280ms** before
auto-focusing the first item. The previous 4-frame delay was short
enough that the user's D-pad-Center release (from the long-press) would
land on the now-focused item, immediately firing its onClick and
dismissing the dropdown. The 280ms buffer ensures the release happens
before focus shifts.

`CollectionRailViewModel.folderChannels` now passes
`folder.titleLogoUrl` through to each `ChannelTab` so when the user
selects a collection from the dropdown, the resulting TopBar channel
pills show their logos (parity with the default channel rail).

---

## 📅 Pending Follow-ups

These were trimmed for scope on 2026-05-18 and are flagged for a
follow-up pass:

1. **G-D: Smooth 200ms pill swap animation.** Today's swap is logically
   correct (focus follows, state survives) but visually instant. A
   proper slide needs a custom `Modifier.animatePlacement` inside a
   `LookaheadScope` — Compose doesn't ship this for non-Lazy rows.
   Estimated ~30 lines in `TopNavigationBar.kt`.

2. **G-E: Modern Top Bar settings should also list overlay items.**
   Spec calls for Search / Discover / My Stuff / Settings to be
   toggleable TopBar pills in Modern feel. Today the settings screen
   shows the four category pills only. To do this properly:
   - Expand `CategoryPill` enum to 8 values
   - Make `ProfileOverlay`'s static menu data-driven (read from
     `CategoryPillsViewModel.drawerPills`)
   - Update `MainActivity`'s pill → icon + route maps for the 4 new
     pills
   - Default ordering: 4 categories on TopBar, 4 overlay items in
     DRAWER

3. **G-C: Loop scroll on remaining vertical menus.** Wired today on
   `ProfileOverlay` / `CollectionsDropdown` / `FolderPillsDropdown`.
   Still needs:
   - `SettingsHubScreen` left rail (long `LazyColumn` — needs
     scroll-into-view helper like the TopBar uses, since
     `focusProperties` alone won't auto-scroll an offscreen target)
   - Any confirmation dialogs and misc popup menus discovered during
     QA

---

## 📅 Archived session summaries

> Full details in [`SESSION_HISTORY.md`](./SESSION_HISTORY.md).

- **2026-05-19 — Upstream cherry-pick marathon.** 68 upstream commits across 7 phases + 5 fix-ups (75 pushed total). Shipped Still-Watching prompt, autoplay timeout options, 5-profile support, `core/debrid/` module (Real-Debrid + Torbox), Mark previous seasons watched, More Like This source toggle, audio amplification w/ HDMI passthrough. Critical fixes: ExoPlayer resume race, un-pushed local progress wipe (data-loss prevention), ExoPlayer teardown order, A/V desync at start. 23 commits skipped (Feel-system conflicts) — see archive for hand-port queue.
- **2026-05-20 — TopBar / Modern Feel polish marathon.** Twelve sub-fixes across nine batched task lists. Introduced `CardFocusStyle` enum, CompositionLocals (`LocalIsModernFeel`, `LocalTopBarOverlayHeight`, `LocalPosterGlowEnabled`, `LocalCardFocusStyle`), DataStore keys (`modern_top_bar_enabled`, `poster_glow_enabled`, `card_focus_style`), real-blur glassmorphism via Coil `BlurTransformation`. Shipped: Modern Top Bar toggle (default OFF), Card Focus Style cycle (Accent/Glow/Bloom — later reduced to Accent/Bloom on 05-21), 16dp edge-to-edge, carousel takeover, 6dp dot indicator, channel-pill logo fallback, 85% Profile Overlay, focusable folder-picker section headers, `CollectionsHomeScreen` wired into `NuvioNavHost`, Settings Hub left-rail focus + single-expand accordion. The "Modern Top Bar" toggle stays OFF by default — flip in Settings → Appearance → Top Bar. Skyworth TV's GPU silently no-ops `RenderEffect`; stack-blur Coil transform is the established fallback (`BlurTransformation.kt`). Full details in archive.
- **2026-05-21 — Modern hero polish, ARVIO trace + revert, settings reorg, Spotlight layout.** ARVIO-inspired hero layout attempted then surgically reverted; kept text shadows, 72dp description cap, dynamic TopBar measurement. New `HomeLayout.SPOTLIGHT` (fourth layout) with full-bleed hero + single-row swap strip. Settings hub reorg (Appearance sub-items: Cards, Side Rail, Detail Page). `CardFocusStyle` reduced to ACCENT/BLOOM. `Modifier.shadow` for all cards (TV M3 `Card.glow` no-ops on Skyworth). `NavHostController.popBackToMainScreen()` helper.
- **2026-05-23 — Spotlight rework, tab-nav fix, COLLECTION root-cause, Prime-style row spacing.** Spotlight reshaped from animated slide-up strip into fixed one-row container + fixed tab navigation. Movies tab first-tap no-op fixed (dropped `saveState`/`restoreState`). COLLECTION blanket filter removed (upstream `viewContext` filter already sufficient). Prime-style tight row-title→cards rhythm (2dp title bottom padding, 0dp LazyRow top) across all layouts.

---

## 🚫 Carry-over rules from global CLAUDE.md

- Compile after every group/step before moving to the next
- Never delete commented-out code marked `// TODO: re-enable for discovery mode later`
- Collections screen (`CollectionsHomeScreen.kt`) is **exempt** from
  layout changes — never touch it
- `LayoutSettingsScreen.kt` (Old Layout) is **dormant** — do not
  reference or delete until explicitly told
- Always create a git commit checkpoint before starting any major
  refactor
- The 3-tier setting resolution (per-row → per-screen → global) is
  authoritative — never bypass it
- Do NOT use `collectAsState()` for layout flows — use
  `collectAsStateWithLifecycle()`
- Do NOT fan out subagents when tasks touch `LayoutPreferenceDataStore.kt`,
  `MainActivity.kt`, or `NuvioNavHost.kt` simultaneously

---

## 📚 Key files reference (Feel system additions only)

For the full app-wide reference, see the global `~/.claude/CLAUDE.md`.

### Navigation shell
- `MainActivity.TopNavBarScaffold` — gate point for Modern/Legacy
  branching; owns `editMode` + `showProfileOverlay` state
- `ui/components/TopNavigationBar.kt` — single composable for both
  feels, gated by `isModernFeel` param
- `ui/components/SideRail.kt` — Legacy-only render path (gated in
  MainActivity), now includes Pill Channels entry
- `ui/components/ProfileOverlay.kt` — Modern-only

### Data layer
- `data/local/LayoutPreferenceDataStore.kt` — added
  `navigationFeel: Flow<Feel>` + `setNavigationFeel(Feel)`
- `data/local/CategoryPillOrderDataStore.kt` — new, per-profile
- `data/local/ChannelRailDataStore.kt` — unchanged structurally; the
  `titleLogoUrl` field added in a prior session still in use

### ViewModels
- `ui/components/CategoryPillsViewModel.kt` — pill ordering
- `ui/screens/home/ChannelRailViewModel.kt` — channel pills
  (`titleLogoUrl` now flows into `FolderPillOption`)
- `ui/screens/collection/CollectionRailViewModel.kt` — collection
  folders (`titleLogoUrl` now flows into `ChannelTab`)

### Settings
- `ui/screens/settings/SettingsHubScreen.kt` — adds `appearance.feel`
  and `appearance.topbar` sub-items under Appearance
- `ui/screens/settings/NavigationFeelContent.kt` — Feel picker
- `ui/screens/settings/TopBarSettingsContent.kt` — pill order /
  display mode / visibility

---

## EOD Protocol

When the user says **EOD**, execute the full End of Day protocol:

1. **Compile check:** If code changed since the last successful compile or
   `installFullDebug`, run `./gradlew :app:compileFullDebugKotlin`. If no
   code changed since last green build, skip. **Never push broken code.**
2. `git add -A && git status`
3. Unstage any `.idea/` files, `.DS_Store`, or IDE artifacts.
4. Update this `CLAUDE.md` per **Session Rules** (below).
5. Commit with descriptive message summarizing today's session.
6. `git push origin dev`
7. **Verify:** `git log --oneline -5` and `git status` — confirm clean
   tree + successful push.
8. **Print session summary:**
   - Files changed
   - Features shipped (mark which were **tested on TV** vs **untested**)
   - Bugs fixed
   - Pending follow-ups for next session
   - Anything promised but not delivered
   - Last build status
   - Last APK deployed to TV (yes/no + what was tested)

---

## Session Rules

At the end of every session — before the final git commit and push —
automatically update this `CLAUDE.md` file with:

- Any new files created (path + one-line purpose)
- Architectural decisions made (what + why)
- Features shipped (user-visible changes)
- Bugs fixed (root cause + fix)
- Any pending follow-ups (what was scope-trimmed, what to revisit)

Commit the `CLAUDE.md` update as part of the final push. **Do not ask
— just do it.**

---

## 📅 Session log — 2026-05-25 (Hero sizing, TopBar immersion, settings expansion, container architecture)


### Headline

Three rounds of focused fixes (22 total) across hero presentation,
TopBar immersion, navigation loop scoping, settings management, and
the row configuration pipeline. Skyworth screen confirmed at 960×540dp
(1080p at 2x density). Spotlight hero sizing root-caused to 277dp
(vs Classic's 400dp default). Major new settings infrastructure:
TopBar master toggle, full SideRail management (reorder + display mode
+ visibility), Continue Watching as addable row, scope-filtered catalog
picker, and populate/clear-all toggle.

### Features shipped

- **Hero description: 3 lines + bodySmall** — `maxLines` 4→3,
  `bodyMedium`→`bodySmall`, `heightIn(max=72dp)`→`56dp` in both
  `HeroCarousel.kt` and `ModernHomeHero.kt`.
- **Proportional hero metadata padding** — `HeroCarousel` bottom
  padding now `(heroHeight * 0.12).coerceIn(16dp, 48dp)`.
  Classic (400dp) → 48dp (unchanged). Spotlight (277dp) → ~33dp.
- **TopBar immersion fade** — Fade out 400ms, fade in 300ms (was
  snap/600ms). Spotlight now toggles immersion from
  `rowsAreaHasFocus`. Long-press Back from rows → shows TopBar +
  focuses TopBar pill.
- **Removed LocalTopBarOverlayHeight from hero metadata** — TopBar
  fades out in rows, so clearance padding unnecessary.
- **clipToBounds on rows containers** — Spotlight + Modern both clip
  at the rows boundary. Modern: moved clipToBounds before padding +
  added on parent modifier.
- **Row title fixed height** — `Box(height=28dp, wrapContentHeight)`
  in Modern titles; `height(28/36dp)` in CatalogRowSection. Focus
  chrome stays within the fixed slot.
- **Channel pills loop isolation** — Loop scrolling only within
  channel pills. Category Left = hard stop (or avatar in Modern).
  Avatar Left = hard stop.
- **TopBar master toggle** — `top_bar_enabled` DataStore key.
  Toggle in Settings → Appearance → Top Bar (Legacy only). Modern
  always shows TopBar.
- **SideRail full management** — Per-item reorder (↑/↓), display
  mode cycle (Icon+Text / Icon / Text), visibility toggle. Profile
  and Home always visible. Order + display modes stored in DataStore.
- **Continue Watching as addable row** — `LayoutRowKind.CONTINUE_WATCHING`
  + `HomeRow.ContinueWatching`. Appears in add-row bar; reorderable
  and removable. Pipeline inserts at user's chosen position.
- **Catalog scope filtering** — `CatalogPickerDialog` filters by
  scope: Movies → movie-type only, TV → series-type only, Home → all.
- **Populate/Clear All toggle** — Side-by-side buttons in Rows
  settings. Clear shows confirmation dialog. `clearAllRows()` on VM.
- **HomeLayoutSizing.kt** — Shared sizing helpers
  (`singleRowContainerHeight`, `multiRowContainerHeight`,
  `heroHeightForRowsContainer`) extracted from per-layout inline math.
- **UniversalHomeNavigation.kt** — Shared Back-Level-Up hierarchy
  (L0–L5) wired once at HomeScreen dispatch level.

### New files

- `ui/screens/home/HomeLayoutSizing.kt` — shared hero/row sizing
- `ui/screens/home/UniversalHomeNavigation.kt` — universal Back handler

### Bugs fixed

- **Spotlight D-pad Down escaping to TopBar** — `onPreviewKeyEvent`
  only consumed `KeyDown`; `KeyUp` leaked through to spatial focus.
  Now consumes both. Removed inline `requestFocus()` (timing race
  with `key()` recomposition) — `LaunchedEffect(currentRowIndex)`
  with frame waits is the reliable path. Debug logging added.
- **Movies tab first-tap no-op** — `saveState`/`restoreState`
  dropped in prior session; carried forward.

### Architectural decisions

- **Proportional padding over fixed** — Hero metadata bottom padding
  scales with `heroHeight` so shorter heroes (Spotlight 277dp) get
  more backdrop visible while taller heroes (Classic 400dp) keep
  their existing spacing.
- **TopBar always visible in Modern** — The toggle only gates Legacy
  feel. Modern has no SideRail alternative, so hiding the TopBar
  would trap the user.
- **Channel-only loop scroll** — Category pills are a fixed set
  that doesn't benefit from wrapping. Channel pills are a potentially
  long scrollable list where wrap improves navigation.
- **ContinueWatching as HomeRow** — Data object variant in the sealed
  class. Pipeline recognizes `continue_watching` key and inserts
  `HomeRow.ContinueWatching`. Rendering delegated to existing
  `ContinueWatchingSection` (Classic currently no-ops the render;
  next step is wiring the actual section).

### Pending follow-ups

1. **ContinueWatching row rendering** — `HomeRow.ContinueWatching`
   is recognized in the pipeline and dispatched in Classic's `when`
   block but currently renders nothing (empty `{ }` branch). Needs
   wiring to the actual `ContinueWatchingSection` composable with
   the correct callbacks.
2. **SideRail reads order/display mode** — Settings UI stores order
   and display modes but `SideRail.kt` doesn't yet read the order
   from DataStore to reorder its items. The visibility toggles work.
3. **Spotlight D-pad Down** — Debug logging deployed (`SpotlightNav`
   tag). Check `adb logcat -s SpotlightNav` to verify the handler
   is intercepting events. If it still escapes, investigate whether
   `CatalogRowSection`'s internal `onPreviewKeyEvent` consumes Down
   before the parent Box sees it.
4. **Phase 8 localization sweep** + 23 skipped upstream commits
   still pending from prior sessions.

### Notes for future sessions

- **Skyworth JAWWY-TV-2.0 confirmed at 960×540dp** (1080p at 2x
  density). Hero sizing math: `singleRowContainerHeight` = cardHeight
  + 74dp; Spotlight heroHeight = 540 − containerHeight.
- **clipToBounds order matters** — must come before `padding()` in
  the modifier chain to clip to the outer bounds, not the padded
  inner area.
- **Long-press Back uses `nativeKeyEvent.repeatCount`** — accessed
  via `val native = event.nativeKeyEvent` (not a separate import).

---

## 📅 Session log — 2026-05-26/27 (Spotlight three-state hero, per-row card scaling, back hierarchy, TopBar brand glow)

### Headline

Multi-day session across four rounds of fixes (~30 total). Spotlight
rewritten from scratch: Box→Column layout, three-state hero (Carousel /
Constrained / Hidden), LazyColumn rows with Compose-native D-pad. Per-row
card scaling fixed in Modern (`ModernRowSection` raw-vs-scaled unit
mismatch). Universal back hierarchy wired for all four layouts (Classic,
Grid, Modern, Spotlight). TopBar: `SelectionDashIndicator` removed,
replaced with top-edge brand-color dash + glow for selected channel pill.

### Features shipped

- **Spotlight Column layout** — Vertical stack: hero section on top
  (variable height via `animateDpAsState`), rows below (`weight(1f)`).
  No overlap, no gap. State A=400dp Classic hero, State B=Modern
  non-fullscreen hero (backdrop right, metadata left, reactive to
  focused card), State C=hero hidden, rows full screen.
- **Spotlight three-state hero** — `SpotlightHeroState` enum. Carousel
  (HeroCarousel, TopBar visible), Constrained (ModernHeroScene +
  HeroTitleBlock, TopBar hidden), Hidden (0dp, TopBar hidden). Per-row
  detection: `constrainedHeroHeight < 250dp` → HIDDEN. Transitions:
  `animateDpAsState`/`animateFloatAsState` 300ms tween.
- **Spotlight rows: homeRows priority** — Swapped `catalogRows` (3 raw
  addons) / `homeRows` (10+ configured rows) priority to match Classic.
  Root cause of "only 3 rows" bug.
- **Per-row card scaling in Modern** — `ModernRowSection` applied raw
  `cardWidthDp` from per-row config without Modern's scaling factors
  (`0.84f * 1.08f` portrait, `1.24f * 1.34f` landscape). Fixed in both
  `ModernRowSection` and `ModernHomeRowsList` prefetch.
- **Back hierarchy for all layouts** — Local `BackHandler` in each:
  Classic (gated on `classicContentHasFocus`), Grid (gated on
  `gridContentHasFocus`), Spotlight (L5→L4→L2 with per-row
  `firstItemFocusRequester`), Modern (L5+immediate `activeItemIndex`
  update for rapid double-Back, L4 jump to first row).
- **TopBar brand glow** — `SelectionDashIndicator` deleted. Selected
  channel pill now renders a Canvas overlay at the top screen edge: 3dp
  horizontal dash + 50dp downward gradient in the channel's brand color.
  Position tracked via `onGloballyPositioned`, animated with
  `animateFloatAsState`/`animateColorAsState` 300ms. Color from
  `ChannelTab.brandColor` or `rememberArtworkBackedGlowColor` fallback.
- **Spotlight expand card** — Passed `focusedPosterBackdropExpandEnabled`,
  `focusedPosterBackdropTrailerEnabled`, `focusedPosterBackdropTrailerMuted`,
  `posterLabelsEnabled` etc. from `uiState` (was hardcoded `false`).
- **Spotlight State B SideRail padding** — Legacy feel: 48dp start
  padding on metadata. Modern: 16dp.
- **Spotlight State B metadata clip** — `heightIn(max = heroHeight -
  bottomPad - 16dp)` on `HeroTitleBlock` + `clipToBounds()` on hero Box.

### Bugs fixed

- **Spotlight only showed 3 rows** — `catalogRows` checked
  `uiState.catalogRows.isNotEmpty()` first, using raw auto-populated
  catalogs (~3) and skipping configured `homeRows` (~10+). Swapped
  priority to match Classic.
- **Modern per-row card sizes invisible** — Raw `cardWidthDp` (e.g.
  140dp) replaced pre-scaled globals (~114dp). Applied same
  `0.84*1.08` / `1.24*1.34` factors to per-row overrides.
- **Classic/Grid back consumed at TopBar** — `BackHandler(enabled=true)`
  consumed Back even at TopBar, blocking exit dialog. Gated on content
  focus via `onFocusChanged`.
- **Modern double-Back stuck on L5** — Rapid Back presses both hit L5
  because `activeItemIndex` hadn't updated between them. Fixed by
  immediately setting `activeItemIndex = 0` in L5 handler.
- **Spotlight focus requesters stale after settings** — Added
  `LaunchedEffect(catalogRows)` to prune `firstItemRequesters` map.

### New files

None — all edits to existing files.

### Architectural decisions

- **Column over Box for Spotlight** — Vertical stack eliminates
  overlap/gap issues from the Box+align approach. `weight(1f)` on rows
  gives natural remaining-space behavior. Hero height animations drive
  layout changes automatically.
- **Local BackHandlers over universal ViewModel plumbing** — Each layout
  knows its own focus state best (Classic's `currentFocusSnapshot`,
  Grid's `lastFocusedGridItemKey`, Modern's `activeItemIndex`,
  Spotlight's `focusedRowIndex`/`focusedItemInRow`). Local handlers read
  these directly without ViewModel round-trips.
- **Brand glow via Canvas** — Pure `drawRect` + `Brush.verticalGradient`.
  No shaders, no RenderEffect, works on Skyworth/Android 6+. Position
  tracked via `onGloballyPositioned` + `positionInRoot()`.

### Pending follow-ups

1. **On-device verification** of all changes. APK installed but no
   manual smoke test in-session. Key test paths: Spotlight three-state
   transitions, per-row card size changes in Modern, Back hierarchy on
   all four layouts, TopBar brand glow animation.
2. **Phase 8 localization sweep** + 23 skipped upstream commits.
3. **ContinueWatching row rendering** in Classic (pipeline dispatch
   exists but renders empty).
4. **SideRail reads order/display mode** from DataStore (settings UI
   stores them but SideRail doesn't consume).
5. **Spotlight D-pad between rows** — LazyColumn approach needs TV
   verification. If focus escapes, the spatial-focus fallback is the
   Column + verticalScroll approach from the spec.

---

## 📅 Session log — 2026-05-29/30 (Brand-glow revert, Spotlight lazy-load + back-nav + State B scrim fixes)

### Headline

Removed the TopBar brand-glow Canvas (it broke channel back-nav), then
fixed several Spotlight issues across multiple rounds: back-from-3rd+-card,
rows 4+ shimmer, channel-carousel back-nav, and the State B row-title chop.
Several attempts rode **incorrect initial hypotheses** (the title chop took
four tries — zIndex, top padding, dark plate, then the real fix: lifting the
hero bottom scrim off the seam). The gotchas below capture what was actually
true so they don't recur.

### Bugs fixed

- **Channel carousel glow reverted** (`TopNavigationBar.kt`) — removed the
  `onGlowPositionReported` callback, glow state, animated glow position/
  color, the `Canvas` overlay + `Box` wrapper, and the `onGloballyPositioned`
  reporters. Kept `SelectionDashIndicator` deleted (no dot/dash under pills).
- **Spotlight back from 3rd+ poster didn't snap to first card**
  (`SpotlightHomeContent.kt`) — from card 3+, the inner LazyRow had recycled
  card 0, detaching its `FocusRequester`, so `requestFocus()` silently
  failed. Fix: register each row's inner `LazyListState` in `rowListStatesMap`;
  the Back handler now `scrollToItem(0)` → `withFrameNanos` → `requestFocus()`.
- **Spotlight rows 4+ stuck as shimmer placeholders**
  (`SpotlightHomeContent.kt` + `HomeScreen.kt`) — Spotlight had no lazy-load
  trigger. Added a `snapshotFlow`-on-scroll-settle effect (cloned from
  Classic) firing `onRequestLazyCatalogLoad(key)` for visible/next
  placeholder rows; added the param and wired it to
  `viewModel.requestLazyCatalogLoad`.
- **Channel-pill carousel back-nav broken in Spotlight**
  (`SpotlightHomeContent.kt`) — see gotcha #1.
- **Spotlight State B row title "chopped" — REAL root cause: the hero
  bottom scrim, not layout** (`ModernHomeHero.kt` + `SpotlightHomeContent.kt`).
  Three wrong attempts first: `zIndex(2f)` on the title Row, then
  `padding(top=12dp)` on the rows LazyColumn (Fix A), then a
  `background(Color.Black.copy(alpha=0.5f))` plate behind the title text
  (Fix B). Fix A + Fix B were reverted. On-device `SpotlightSize` logging
  proved `overlap = 0dp` (hero/rows abut cleanly — no layout overlap at all).
  The title was rendered but invisible: `ModernHeroGradientLayer`'s bottom
  vertical scrim ended at `endY = size.height` with its darkest stop
  (`bgColor`) landing exactly on the hero/rows seam, drowning the row title
  just below. **Fix:** added `bottomScrimLiftDp: Dp = 0.dp` to
  `ModernHeroScene` / `ModernHeroGradientLayer`; the scrim now ends at
  `scrimBottomY = (size.height - lift).coerceAtLeast(bottomStripStartY)` for
  BOTH the gradient `endY` and the draw rect, leaving the bottom `lift`-dp
  clean. Spotlight State B passes `40.dp`; default `0.dp` keeps Modern home
  (the other caller of that same non-fullscreen branch) byte-identical.
  NOTE: the leftover `zIndex(2f)` on the title Row is harmless and was left
  in place (out of scope for the Fix A/B revert).

### ⚠️ Gotchas / notes for future sessions

1. **Spotlight's BackHandler must gate on real content focus, never on the
   `heroState` proxy.** `heroState == CAROUSEL` is just `!rowsAreaHasFocus`,
   so it is ALSO true when focus is up on the TopBar channel pills. Because
   content composes after the TopBar, Spotlight's BackHandler wins the
   `OnBackPressedDispatcher` LIFO and steals Back from the TopBar's own
   two-step channel-carousel chain (mid-carousel → first pill → category
   pills). Fix was a dedicated `heroHasFocus` flag (`onFocusChanged` on the
   hero `Box`) → `BackHandler(enabled = rowsAreaHasFocus || heroHasFocus)`.
   Classic/Grid/Modern already gate on `*ContentHasFocus`, so only Spotlight
   had this bug. **Corollary:** the channel back-chain lives entirely in
   `TopNavigationBar.kt` (`BackHandler(enabled = focusInCarousel)`); it was
   never touched by the glow revert — don't go looking for it there.

2. **A row title that looks "chopped" in Spotlight State B is a SCRIM
   contrast problem, not layout/z-order.** Confirmed empirically: temporary
   `SpotlightSize` logging (`onGloballyPositioned` on the hero Box + rows
   LazyColumn) showed `overlap = heroBottomY - rowsTopY = 0dp`. The Column
   structurally cannot overlap — a fixed-`height(animatedHeroHeight)` hero
   child + a `weight(1f)` rows child abut exactly; `clipToBounds` on the hero
   only clips the hero's own content. The culprit was `ModernHeroGradientLayer`
   fading to opaque `bgColor` right at the seam. Two corollaries that misled
   the earlier attempts: (a) `Modifier.zIndex` only reorders siblings of the
   SAME parent, so it can never lift a row title above the hero (different
   parents) — and the rows LazyColumn already draws above the hero anyway;
   (b) when adjusting a `verticalGradient` scrim, moving only `endY` makes the
   region past it CLAMP to the last color stop (here opaque `bgColor`) — you
   must move the draw rect's bottom too, or the "fix" darkens instead of
   lightens.

### New files

None — all edits to existing files.

### Pending follow-ups

- **On-device verification** of all fixes (installed, not smoke-tested by
  Claude): Spotlight back from 3rd+ card, rows 4+ loading, channel-pill
  carousel back-nav, the in-content Spotlight back chain (regression check),
  and the State B row-title readability after the scrim lift. If 40dp of
  `bottomScrimLiftDp` reads as too little/too much, it's a one-line dial at
  the Spotlight `ModernHeroScene` call site.
- Prior follow-ups (Phase 8 localization, 23 skipped upstream commits,
  ContinueWatching render in Classic, SideRail order consumption) still open.
