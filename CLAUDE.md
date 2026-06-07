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
- **2026-05-25 — Hero sizing, TopBar immersion, settings expansion, container architecture.** 22 fixes: hero description 3-line/`bodySmall`, proportional hero metadata padding, TopBar immersion fade (400/300ms), `clipToBounds` on rows, fixed 28dp row-title height, channel-pill loop isolation, TopBar master toggle (`top_bar_enabled`, Legacy-only), full SideRail management (reorder/display-mode/visibility), Continue Watching as addable row, catalog scope filtering, populate/clear-all. New files: `HomeLayoutSizing.kt`, `UniversalHomeNavigation.kt`. Skyworth confirmed 960×540dp (1080p @2x).
- **2026-05-26/27 — Spotlight three-state hero, per-row card scaling, back hierarchy, TopBar brand glow.** Spotlight rewritten Box→Column with three-state hero (Carousel/Constrained/Hidden) + LazyColumn rows. Per-row card scaling fixed in Modern (`ModernRowSection` raw-vs-scaled mismatch). Universal Back hierarchy wired for all four layouts via local `BackHandler`s. TopBar `SelectionDashIndicator` removed → brand-color dash+glow Canvas (later reverted 05-29/30).
- **2026-05-29/30 — Brand-glow revert, Spotlight lazy-load + back-nav + State B scrim fixes.** Removed the TopBar brand-glow Canvas (broke channel back-nav). Fixed Spotlight back-from-3rd+-card (register inner `LazyListState`s), rows 4+ shimmer (added `snapshotFlow` lazy-load trigger), channel-carousel back-nav (`heroHasFocus` gate), and the State B row-title chop — real fix was lifting the hero bottom scrim off the hero/rows seam via `bottomScrimLiftDp` (three wrong hypotheses first: zIndex, top padding, dark plate).
- **2026-05-30/31 — State B scrim saga, TopBar pill redesign, Spotlight nav + Show All.** State B hero now renders identically to Modern's non-fullscreen hero (dropped bespoke scrim). TopBar channel-pill redesign: capsule → artwork-backed dynamic underline + 6 tweaks (`NavBarHeight` 60→54dp, avatar 36→26dp, edge-to-edge channels, uniform 48×24 logos). Spotlight: held-Up flood throttle (200ms), full catalog rows (extended `shouldKeepFullRow` to SPOTLIGHT), leading "Show All" card on D-pad Left from first poster.
- **2026-05-31 — Hard-stop D-pad Left from first content item (Modern feel).** D-pad Left from the leftmost poster no longer opens the Profile Overlay — it hard-stops (overlay still reachable via avatar Select). One-line fix: `MainActivity.kt:846` `LocalSideRailController provides if (isModernFeel) null else openSideRail`. Per-layout trace (Classic/Modern 1-press, Spotlight after Show-All, Grid unaffected). Full detail in archive.
- **2026-06-01/02 — Phase 3 upstream review-port, build unblock, Phase 4 settings placement.** Reviewed 8 upstream commits, ported the 5 genuinely-live (thread-safe DateFormatter, CW Next-Up thumbnail guard, CW launcher channel refresh adapted to our flow-based sync service, extended-poster focus, CEC long-press `LongPressKeyTracker.kt`), skipped 3 that conflict with the fork's reimplemented Modern hero. Build unblock: a pre-existing `lintVitalFullDebug` failure (translated-but-missing-from-default strings) blocked `installFullDebug` — added the missing English defaults + a `lint-baseline.xml`. Phase 4 settings placement: most were already shipped; only CW sort-mode toggle + Licenses & Attributions screen needed work. Full detail in archive.
- **2026-06-02/03 — Rows Manager redesign, enhanced pickers, loop scroll, centralized resolver, settings reorg + 13 UX fixes.** Multi-pass overhaul of the Rows Manager (`appearance.rows`, ROWS_ONLY) and its source pickers. New `LayoutRowConfig.expandEnabled` (3-state per-row override); per-scope + per-row expand via new pure resolver `ui/screens/home/RowDisplayConfig.kt` (`resolveRowDisplayConfig`). Rows Manager redesigned (scope tabs / source pills / table header doubling as global controls); enhanced multi-select pickers with `PickerActionBar` (Populate All / Follow Order / Delete All); reusable `Modifier.dpadLoopWrap` (`ui/screens/settings/LoopScroll.kt`). Settings reorg (Cards pane deleted, new Trailers sub-item, Theme "Focus highlight" group). New files: `RowDisplayConfig.kt`, `LoopScroll.kt`. Full detail in archive.
- **2026-06-03/04 — Cinema card style, backdrop images, focus-driven hero collapse, landscape resize, corner-radius UI.** Third card style `LayoutCardStyle.CINEMA` (fixed-size) + per-row rendering/settings; 3-state style selector (Poster→Landscape→Cinema). Backdrop image + permanent logo overlay for Landscape/Cinema (`ContentCard.cardStyle`, `isWideCardStyle`); focus-driven hero collapse (Spotlight State C / Modern alpha fade on a Cinema row); landscape-resize fix (`isWideCardStyle` wins over `item.posterShape`); corner radius exposed under Appearance → Global → Card Style. Full detail in archive.

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

## 📅 Session log — 2026-06-04/05 (Spotlight cinema-hero fix, Modern State-2 cinema reflow, Modern State-1 one-row crossfade pager, image tuning, cinema 16:9)

### Headline

Five home-layout fixes, all compiled green and installed to the Jawwy TV
(`192.168.8.170`); **not yet smoke-tested**. Highlight: a new Modern **State-1
one-row crossfade pager** (`ModernHomeRowsPager.kt`). Two fixes are flagged for
revision next session (Modern State-2 proportions, Cinema dimensions). Pushed to
`origin/dev`.

### Fix 1 — Spotlight cinema hero stuck-hidden (`SpotlightHomeContent.kt`) ✅

Root cause: three **stale-capture** bugs in the 06-03/04 cinema-collapse code.
`focusedRowIsCinema` + `focusedRowCardHeight` were `remember { derivedStateOf {} }`
with **no keys** (captured the first composition's `catalogRows`/`rowConfigLookup`
forever); `heroState` was a remembered `derivedStateOf` closing over
`showHeroForFocusedRow` — a plain non-state `val` it could never observe changing,
so it stayed `HIDDEN`. Fix: keyed the two deriveds on
`catalogRows`/`rowConfigLookup`/`posterCardStyle`; made `heroState` a plain
per-recomposition `val`. Leaving a Cinema row now restores the hero — purely
focus-driven.

### Fix 2 — Modern State-2 cinema dead space (`ModernHomeContent.kt`, `ModernHomeModels.kt`) ⚠️ needs polish

State 2 = non-fullscreen hero (`modernHeroFullScreenBackdropEnabled` OFF). The
06-03/04 code faded the hero to alpha 0 on a focused cinema row in **both** hero
modes, leaving black dead space + clipped cinema cards. Fix: moved
`focusedRowIsCinema` up; made **`rowsViewportHeight` cinema-aware + animated**
(`animateDpAsState` 220ms) — on a focused cinema row in State 2 it grows to
`singleRowContainerHeight(CINEMA)` down to a `MODERN_CINEMA_STATE2_HERO_MIN = 200dp`
hero floor; the hero shrinks to the complement. Every downstream consumer already
reads `rowsViewportHeight`, so the reflow propagates for free. Gated the alpha-fade
to **State 1 only**; added a **stable `heroBackdropRequestHeight`** (base, non-animated)
for the hero image request so the 220ms visual animation doesn't rebuild the
`ImageRequest` every frame. **FOLLOW-UP:** on-device the metadata renders behind the
TopBar and there's too much free space — must match **Spotlight State B** proportions
exactly.

### Fix 3 — Modern State-1 one-row crossfade pager (NEW `ModernHomeRowsPager.kt`, `ModernHomeContent.kt`) ✅

State 1 = fullscreen backdrop. New behavior: **ONE row visible at a time**, fixed at
the bottom, `Crossfade` between rows on D-pad up/down, **dimmed prev/next row-name
hints** above and below, adapts to each row's card style/size, **backdrop + hero
unchanged**. Implementation: `ModernHomeContent` branches `fullScreenBackdrop` →
`ModernHomeRowsPager` else → `ModernHomeRowsList` (existing State-2 list untouched).
The pager **reuses `ModernRowSection`** via a thin `ModernPagerRow` wrapper that
replicates the list's `stableOnRowItemFocused`/`stableOnCatalogSelectionFocused`.
Up/down are intercepted at the container (`onPreviewKeyEvent`): swap the visible row
index, update `activeRowKey`/hero immediately, re-drive focus onto the new row via
its `rowFocusRequester` (`focusRestorer` lands on the saved item) with a
`withFrameNanos` retry loop; **up at row 0 is NOT consumed** → escapes to the TopBar;
**down at the last row is consumed** (no-op). Autorepeat throttled
(`MODERN_PAGER_REPEAT_MS = 180`). Per-row lazy-load + L1/L3 back triggers replicated.
`heroCinemaAlpha` retired (constant `1f`) — the hero is never faded now.

### Fix 4 — Image loading fork tuning (`NuvioApplication.kt`) ✅

**Audit:** our Coil `ImageLoader` / `ContentCard` request building / Modern row
prefetch are **byte-identical to upstream/dev** — nothing to port.
`LocalVerticalScrollSuppressImages` has **no provider** → dead code, not a slowness
cause. Genuine fork-cost: landscape/cinema rows load heavier `backdropUrl`, and
`CatalogRowSection` (Classic/Spotlight) has **no prefetch**. Applied the two global
levers: memory cache `0.33 → 0.45`, `bitmapFactoryMaxParallelism 2 → 4` (Coil's
default). ⚠️ In-code note: dial parallelism back to `3` if scroll janks on weak panels.

### Fix 5 — Cinema sizing (`RowDisplayConfig.kt`) ⚠️ needs revision

`CINEMA_CARD_HEIGHT_DP` `285 → 214` (380×285 4:3 → 380×214 16:9, width unchanged =
no horizontal layout shift). **FOLLOW-UP:** revise to **260×370** (tall premium card).

### New files

- `ui/screens/home/ModernHomeRowsPager.kt` — Modern State-1 single-row crossfade
  pager (reuses `ModernRowSection`; manual up/down row-switch + focus handoff).

### Architectural decisions

- **Modern stays a `Box`** (absolute hero layer + bottom rows). State-2 cinema reflow
  rides the single **animated `rowsViewportHeight`** (all consumers already read it);
  the hero **decode** height is kept stable separately to avoid per-frame re-decode.
- **State 1 uses a separate pager composable** — isolated, never touches the working
  `ModernHomeRowsList`; branch at the one call site. Reverting Fix 3 = drop the pager
  file + restore the call site, nothing else.
- **The hero is never faded for a Cinema row anymore** — State 2 reflows, State 1
  shows the cinema row like any other with the backdrop/hero unchanged.

### Pending follow-ups

1. **Modern State 2** — metadata behind the TopBar + too much free space; match
   **Spotlight State B** proportions exactly.
2. **Cinema dimensions → 260×370** (tall premium card) in `RowDisplayConfig.kt`.
3. **Image loading** — on-device validation pending (parallelism jank check;
   consider the Classic/Spotlight backdrop prefetch as the next real win).
4. **On-device smoke test** of all 5 fixes — focus timing on the State-1 pager
   especially (row-switch handoff, up-at-row-0 → TopBar, down-at-last-row,
   return-from-detail landing on the right row).
5. Prior open follow-ups (instant-expand flicker; TMDB picker loop-wrap; Phase 8
   localization; 23 skipped upstream commits; ContinueWatching render in Classic;
   SideRail order consumption) still open.

### Build / deploy

`BUILD SUCCESSFUL`; installed `app-full-armeabi-v7a-debug.apk` on the Jawwy TV
(`192.168.8.170`). Commits: `26aee19c` (Fixes 1/5/4/2 checkpoint), `031fc030`
(Fix 3 pager), `bc86d812` (lint baseline) — all on `origin/dev`. **Not smoke-tested.**

---

## 📅 Session log — 2026-06-06 (Immersive standalone layout, Continue Watching row subsystem + Series/Movies split, staged Trakt submenu, two fixes)

### Headline

Promoted **Immersive** to a standalone layout, built the full **Continue
Watching row subsystem** (configurable, in every layout), split it into
**Series / Movies**, added a gated **Trakt** submenu with a functional **Up
Next** row, and fixed two follow-on issues. Five feature commits on `dev`,
all compiled green and installed to the Jawwy TV (`com.nuviodebug.com` on
`192.168.8.170`). Smoke-tested via ADB remote — no crashes; FIX 1 verified
on-device.

### Immersive layout promoted to standalone (commit `e5be1683` — prior-session WIP)

- New `HomeLayout.IMMERSIVE` + `usesModernPresentation` extension (Modern and
  Immersive share the content pipeline; differ only in hero State 1 vs State 2).
  Every `HomeLayout.MODERN` gate switched to `usesModernPresentation`.
- **Removed the "Fullscreen Hero Backdrop" toggle** — fullscreen is now its own
  Immersive layout. `modernHeroFullScreenBackdropEnabled` is derived from
  `layout == IMMERSIVE` in the presentation pipeline.
- **Cinema card dims retuned** `380×214` → **`260×370`** (tall premium portrait)
  in `RowDisplayConfig.kt` (`CINEMA_CARD_WIDTH_DP` / `CINEMA_CARD_HEIGHT_DP`).

### Continue Watching row subsystem (commit `4c5cf314`)

- New `ContinueWatchingCardStyle { POSTER, CARD, WIDE }` stored on the CW row's
  `metadata["cw_style"]` (CW-specific — deliberately separate from
  `LayoutCardStyle`, whose CINEMA semantics clash with "Wide"). The unified
  `ui.components.ContinueWatchingCard` (already shared by Classic + Modern)
  renders all three orientations with a per-style progress bar;
  `continueWatchingCardFootprint(style, base)` is the single sizing source.
- **Rows Manager CW controls:** CW rows get a CW-specific Orient selector
  (Poster/Card/Wide), keep Size (Compact→Large), and **hide Expand**.
- **Toggle fix (STEP 6):** Modern/Immersive built CW from `continueWatchingItems`,
  ignoring the row's `enabled` flag + order. Now driven by the configured
  `HomeRow.ContinueWatching` (default-on only when no CW row is configured).
- **Spotlight (STEP 5):** CW now renders as a leading row with hero↔CW↔row0
  focus chaining.
- **One-shot seed** of a default enabled CW row at top, guarded to never create
  a *lone* CW row (would flip the home into rows-only mode showing only CW).

### CW Series / Movies split (commit `652b0604`)

- New kinds `CONTINUE_WATCHING_SERIES` / `CONTINUE_WATCHING_MOVIES` (+
  `TRAKT_UP_NEXT`), `ContinueWatchingFilter { SERIES, MOVIES, UP_NEXT }`, and a
  `List<ContinueWatchingItem>.forContinueWatchingFilter()` partition by
  `WatchProgress.contentType` ("movie" vs series). `HomeRow.ContinueWatching`
  now carries the filter; the pipeline emits up to 3 CW rows; Modern builds one
  `HeroCarouselRow` per filter; Spotlight chains focus across multiple CW rows.
- **"+ Continue Watching" pill opens a Series/Movies submenu** (max one each,
  dimmed when added) — modelled on `SizePopover`.
- One-shot migration: legacy `CONTINUE_WATCHING` rows → `CONTINUE_WATCHING_SERIES`
  across all scopes. Fixed a latent Classic toggle gap (default-on standalone CW
  is now gated on "no CW configured").
- **Pill reorder:** `Catalogs | TMDB | MDBList | Trakt | Continue Watching | Collections`.

### Staged Trakt submenu (commit `c0cf26d1`)

- **"+ Trakt" is a submenu gated behind Trakt sign-in** (toast otherwise), via
  `NewLayoutSettingsViewModel.traktSignedIn` (`TraktAuthDataStore.isAuthenticated`).
- **Up Next is fully functional:** a `TRAKT_UP_NEXT` row that rides the existing
  CW pipeline (`UP_NEXT` filter = NextUp / next-unwatched-episode items),
  rendered by the shared `ContinueWatchingCard`. Honours Orient/Size/On-Off/Delete,
  no Expand.
- **Watchlist Shows/Movies, New Episodes/Movies, Recommended Shows/Movies** are
  shown as **"Soon"** — deferred. ⚠️ **Critical finding:** Trakt (and
  TMDB_DISCOVER / TMDB_NETWORK) rows are **non-functional stubs** today — the
  `applyConfiguredHomeRows` branch for them is empty `{}`, so they fetch/render
  nothing. The catalog rows need a **from-scratch Trakt→home-row pipeline**
  (fetch → `MetaPreview` → non-addon `CatalogRow` injection + caching). The
  mappers already exist (`TraktRelatedService.toMetaPreview`,
  `TraktLibraryService.fetchWatchlistEntries`) which makes that pass tractable.

### Two fixes (commit `f4119b09`)

- **FIX 1 — seeded CW row missing from Rows Manager:** the pre-split build set
  `continueWatchingDefaultSeeded = true`, so upgraders could end up with the flag
  set but no CW-family row, and the seed never re-ran. Added a dedicated
  `cw_split_seeded` flag that re-ensures a **Series** CW row once on the split
  build (a later manual delete stays sticky). **Verified on-device** — the CW row
  now shows in the Rows Manager with Orient/Size/On-Off/Delete from launch.
- **FIX 2 — Spotlight CW cards now drive the hero:** added
  `ContinueWatchingItem.toSpotlightFocusMeta()` (title + episode info in
  `description` + backdrop) and a `cwCardFocused` flag that forces the hero into
  CONSTRAINED (State B) — CW rows aren't in `catalogRows`, so `focusedRowIndex`
  was stale on them. Focusing a CW card now updates the Spotlight hero like a
  catalog card; catalog focus clears the flag. **Needs watch history to verify
  on-device** (this profile has none).

### Architectural decisions

- **CW orientation is its own enum in `metadata`** — leaves the generic
  `cardStyle`/CINEMA size-hiding logic untouched; CW keeps Size on all three
  orientations.
- **Up Next ≠ a Trakt catalog row** — it's CW-derived (NextUp filter), which is
  why it works while the true Trakt catalog rows don't.
- **`HomeRow.ContinueWatching` carries a `ContinueWatchingFilter`** — one render
  path, three slices; renderers resolve style/size/items + config key per filter.

### Pending follow-ups

1. **"Both" option** in the CW submenu (Series / Movies / Both).
2. **Trakt catalog pipeline:** Watchlist, New Episodes, New Movies, Recommended
   Shows/Movies (the "Soon" items) — build the from-scratch Trakt→home-row
   pipeline + 30-min (calendars) / 60-min (watchlist/recommendations) caching.
3. **Performance + Apple-TV animations** session.
4. **Manual verification of FIX 2** (Spotlight CW hero) with real watch history.
5. Prior open follow-ups (Modern State-2 proportions, Cinema dims revisit, 23
   skipped upstream commits, ContinueWatching render in Classic, SideRail order
   consumption) still open.

### Build / deploy

5 feature commits on `dev` — `e5be1683` (Immersive WIP checkpoint), `4c5cf314`
(CW row subsystem), `652b0604` (Series/Movies split + pill reorder), `c0cf26d1`
(staged Trakt), `f4119b09` (two fixes). `BUILD SUCCESSFUL`; force-stopped +
installed `app-full-armeabi-v7a-debug.apk` (`com.nuviodebug.com`) on the Jawwy
TV (`192.168.8.170`). Smoke-tested via ADB remote control — **no crashes**;
**FIX 1 verified on-device**; FIX 2 + Up Next need watch history to exercise.

---

## 📅 Session log — 2026-06-06 (Part 2: "For You" standalone screen shell + CW "Both" filter)

### Headline

Added a new top-level **For You** navigation screen (STEP 1 shell — no Trakt
API yet, additive) and the missing **"Both"** Continue-Watching filter. Compiled
green, installed on the Jawwy TV (`com.nuviodebug.com` @ `192.168.8.187` — IP
drifted from .170). Smoke-tested via ADB: For You is the authed landing route,
renders pure rows (no hero), CW **Both** shows mixed series + movies, no crashes.

### PART 1 — CW "Both" filter (mixed series + movies, original CW behavior)

- New `ContinueWatchingFilter.BOTH`; `LayoutRowKind.CONTINUE_WATCHING` now maps
  to **BOTH** (was Series). `LayoutRowKey.forContinueWatchingBoth()` reuses the
  legacy `"continue_watching"` id. `forContinueWatchingFilter(items)` → full list.
- Pipeline: `updateCatalogRowsPipeline` maps the `"continue_watching"` key → BOTH.
- **Migration made one-shot:** `seedDefaultContinueWatchingRowIfNeeded` now (a)
  early-returns for non-HOME scopes and (b) gates the legacy CONTINUE_WATCHING→
  SERIES migration behind `cw_split_seeded` (was ungated/every-launch). Required
  so user-added "Both" rows (kind CONTINUE_WATCHING) aren't rewritten to Series.
  Safe because existing users already migrated on `f4119b09`.
- UI: "+ Continue Watching" submenu gains **Both**; `addContinueWatchingRow(BOTH)`.

### PART 2 — For You screen shell (additive)

- `LayoutScreenScope.FOR_YOU` ("for_you" / "For You") + `CategoryPill.FOR_YOU`
  (first enum entry → first pill on fresh installs) + `Screen.ForYou`.
- **Reuses HomeScreen wholesale** via a thin wrapper (like MoviesScreen): the
  FOR_YOU scope is forced to **Classic + hero-off** by defaults in
  `LayoutPreferenceDataStore.selectedLayoutForScope` / `heroSectionEnabledForScope`
  (the FOR_YOU layout key is never written — Rows Manager opens it ROWS_ONLY).
  No new rendering/pipeline code.
- Default seed (`forYouSeeded` flag, **only when Trakt authed**): CW **Both** +
  **Up Next**. Not authed → seeds nothing, flag stays false (seeds later on sign-in).
- **Default tab:** Trakt authed → For You is the start destination (+ one-shot
  `CategoryPillOrderDataStore.promoteForYouToFrontOnce()` for upgraders); else Home.
  MainActivity gates first frame on Trakt auth resolving.
- Rows Manager: FOR_YOU scope tab appears automatically (`LayoutScreenScope.entries`);
  **excluded from the layout-picker** scope tabs (new `ScopePills.showForYou=false`
  on the ALL/LAYOUT_ONLY path) so its fixed pure-rows layout can't be changed.
- MainActivity wiring: `layoutRoutes`, `rootRoutes`, `pillForRoute`/`routeForPill`/
  `iconForPill` (sparkle `Icons.Default.AutoAwesome`), both legacy pill sets.

### New files

- `ui/screens/foryou/ForYouViewModel.kt` — BaseHomeViewModel @ FOR_YOU scope + seed.
- `ui/screens/foryou/ForYouScreen.kt` — thin HomeScreen wrapper.

### Smoke test (ADB, Jawwy TV `192.168.8.187`, `com.nuviodebug.com`)

- ✅ Lands on `Screen: for_you` (Trakt authed) — confirmed via logcat.
- ✅ Pure rows, no hero/backdrop. ✅ CW **Both** = mixed series (Detective Conan,
  According to Jim) + movies (Balls Up, INVINCIBLE, Spartacus) in one row.
- ✅ Up Next row present (NextUp items). ✅ BACK exits to launcher (For You is root).
- ✅ No crashes. ✅ TopBar renders with pills.

### Cosmetic fixes (follow-on commit — Classic shared components)

1. **FIXED — CW row title.** `ContinueWatchingSection` gained a `title: String? =
   null` param (null → the default "Continue Watching" label). `ClassicHomeContent`
   now passes each CW row's configured name (`rowConfigLookup[...].name`), so For
   You's Up Next row reads **"Up Next"** instead of a second "Continue Watching"
   (matches Modern, which already titles from the row config). Backward compatible.
2. **FIXED — no-hero top overlap.** Classic's LazyColumn now reserves
   `LocalTopBarOverlayHeight` (measured TopBar height + 8dp, 68dp fallback) as top
   content padding when `heroVisible == false` (was a flat 24dp), so the first row
   title clears the TopBar. General fix for any hero-disabled Classic screen.

### Remaining follow-up

3. Trakt catalog rows (Recommended/Watchlist/Calendars) still deferred — the
   addable-but-not-default rows from the For You spec need the from-scratch Trakt→
   home-row pipeline (research done; `/calendars/my/*` is genuinely complementary to
   Up Next, not redundant — see PART 3 analysis).

### Build / deploy

`BUILD SUCCESSFUL` (`installFullDebug`, exit 0). Installed
`app-full-armeabi-v7a-debug.apk` (`com.nuviodebug.com`) on the Jawwy TV
(`192.168.8.187`). Committed on `dev` (not pushed). Smoke-tested via ADB.

---

## 📅 Session log — 2026-06-07 (Trakt catalog pipeline — 6 functional rows, For You seed + recommendations)

### Headline

Built the **Trakt catalog pipeline** for the six rows previously staged as
"Coming Soon" in the **+ Trakt** submenu — all now fully functional, fetching
real Trakt data → `CatalogRow`s of `MetaPreview`s. Updated the **For You**
default seed to include Recommended Shows/Movies. Compiled green after every
step; installed + smoke-tested on the Jawwy TV (`com.nuviodebug.com` @
`192.168.8.187`) with **real Trakt data on-device, no crashes**. The Continue
Watching / Up Next / scrobble pipeline was **left completely untouched**
(verified: zero diff in `HomeViewModelContinueWatching.kt`,
`TraktProgressService.kt`, `TraktScrobbleService.kt`).

### The six new row kinds (all auth-gated, TTL-cached)

`TRAKT_RECOMMENDED_SHOWS` / `TRAKT_RECOMMENDED_MOVIES` →
`/recommendations/{type}?extended=full,images`; `TRAKT_WATCHLIST_SHOWS` /
`TRAKT_WATCHLIST_MOVIES` → `/sync/watchlist/{type}?extended=full,images`;
`TRAKT_NEW_EPISODES` / `TRAKT_NEW_MOVIES` → `/calendars/my/{type}/{start}/{days}`
(±7d shows, ±14d movies). TTL: **30 min calendars, 60 min watchlist +
recommendations**.

### Architecture

- **New `core/trakt/TraktHomeCatalogResolver.kt`** (modeled on
  `TraktPublicListSourceResolver`): one `resolve(kind): CatalogRow?` with a
  **profile-keyed** TTL cache (`"<profileId>:<kind>"` — no cross-profile leak),
  auth-gated, returns stale-but-present row on a transient blip, maps
  Trakt show/movie DTOs → `MetaPreview` via the shared `TraktImageUtils`
  helpers + `normalizeContentId`. No extra image fetching (uses Trakt's
  `extended=full,images` payload).
- **Recommendations need no new DTO** (endpoint returns the media objects
  directly → reuse `List<TraktShowDto>` / `List<TraktMovieDto>`). **Watchlist**
  reuses `TraktListItemDto` (added a backward-compatible `extended` query param
  to `getWatchlist`). **Calendars** got two new DTOs
  (`TraktCalendarShowItemDto` / `TraktCalendarMovieItemDto`).
- **Pipeline injection (separate from CW/addon paths):** new
  `observeTraktCatalogRowsPipeline()` fetches configured Trakt kinds →
  `traktCatalogRowsByKey` (a `ConcurrentHashMap` on `BaseHomeViewModel`) →
  `scheduleUpdateCatalogRows()`. The `applyConfiguredHomeRows` `TRAKT`-kinds
  branch reserves each row's order slot; `updateCatalogRowsPipeline` injects
  `HomeRow.Catalog(traktRow)` by key (new branch, after CW / before
  collections — CW branch untouched). Resolver injected into
  `BaseHomeViewModel` + all 4 subclasses (Home/Movies/TV/ForYou).

### New / changed files

- **New:** `core/trakt/TraktHomeCatalogResolver.kt`.
- **Changed:** `TraktApi.kt` (4 endpoints + `getWatchlist` `extended`),
  `TraktSyncDtos.kt` (2 calendar DTOs), `LayoutRowConfig.kt` (6 kinds +
  `LayoutRowKey` helpers + `isTraktCatalogRow` / `TRAKT_CATALOG_KINDS`),
  `LayoutPreferenceDataStore.kt` (`forYouRecommendedSeeded` flag),
  `HomeViewModel.kt` + `HomeViewModelCatalogPipeline.kt` (field + observe
  pipeline + injection), `MoviesViewModel.kt` / `TvShowsViewModel.kt` /
  `ForYouViewModel.kt` (resolver param; ForYou also seeds recommendations),
  `NewLayoutSettingsViewModel.kt` (`addTraktCatalogRow`),
  `NewLayoutSettingsScreen.kt` (functional + Trakt submenu; Expand hidden for
  the 6 kinds), `AddRowPickerDialog.kt` (section labels).

### For You default seed (updated)

Fresh seed: **CW Both → Up Next → Recommended Shows → Recommended Movies**.
New `forYouRecommendedSeeded` flag adds the two recommendation rows **once** to
users who already received the original 2-row seed (skips any already present;
respects later deletion). Still auth-gated.

### Rows Manager (+ Trakt submenu)

All 6 options + Up Next are functional (no "Coming Soon"); each dims once added
(max one of each per scope). The six Trakt-catalog rows show **Orient / Size /
On-Off / Delete, Expand hidden** (gated via `isTraktCatalogRow`).

### On-device verification (Jawwy TV `192.168.8.187`, `com.nuviodebug.com`)

- ✅ For You lands (`Screen: for_you`), recommendation rows seeded + rendered:
  **Recommended Shows** (Rick and Morty, BoJack, Invincible, Archer, Midnight
  Gospel), **Recommended Movies** (Big Hero 6, Dragon, Wreck-It Ralph, Kung Fu
  Panda, Lego Movie).
- ✅ Added via submenu + rendered real data: **Watchlist Shows** (Invincible,
  Landman, 911, Band of Brothers…), **New Episodes** (FROM, Criminal Minds,
  Your Friends & Neighbors…).
- ✅ Clicking a Trakt row item → Detail page loads (Invincible, prime video).
- ✅ No `FATAL`/`AndroidRuntime` across the session.
- ⚠️ **Watchlist Movies** + **New Movies** not added on-screen — parity-proven
  (identical resolver branch, only the `type`/DTO differ from the verified
  shows variants).

### Earlier research this session (no code)

- **CW data-quality investigation:** stale CW = (a) abandoned `<80%` Trakt
  playback rows lingering for the 60-day window + edge cases where the ≥80%
  completion scrobble never lands, and (b) ended/fully-watched-series phantom
  Up Next (metadata-derived, no Trakt/TMDB `status` guard). Scrobbling itself
  works on the happy path.
- **Direct-`/sync/playback` feasibility:** it's a strict subset of "Continue
  Watching" (no Up Next, no artwork, no resume-ms) — not a drop-in replacement.

### Pending follow-ups

1. **Performance + Apple-TV animations** session.
2. **CW data quality** — phantom Up Next on ended shows (low priority; needs
   ended-series + all-aired-watched guard before computing Up Next).
3. **Manual on-screen verification** of `Watchlist Movies` + `New Movies`
   (parity-confirmed by code, not yet seen on TV).
4. Prior open follow-ups (Modern State-2 proportions, 23 skipped upstream
   commits, ContinueWatching render in Classic, SideRail order) still open.

### Build / deploy

Compiled green after each step; `installFullDebug` (exit 0) on the Jawwy TV
(`192.168.8.187`). Committed + pushed to `origin/dev` this session. CW /
scrobble pipeline untouched; no crashes on-device.
