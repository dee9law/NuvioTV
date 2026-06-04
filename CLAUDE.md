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

## 📅 Session log — 2026-06-03/04 (Cinema card style, backdrop images, focus-driven hero collapse, landscape resize fix, corner-radius UI)

### Headline

Added a third card style — **`LayoutCardStyle.CINEMA`** — and the supporting
rendering/settings work, then fixed three follow-on issues the new style
exposed (backdrop images, hero collapse, landscape sizing). All compiled green
and installed to the Jawwy TV (`192.168.8.170`); **not yet smoke-tested**.

### Cinema card style (`LayoutCardStyle.CINEMA`)

- `LayoutRowConfig.LayoutCardStyle` is now `{ POSTER, LANDSCAPE, CINEMA }`.
  Gson round-trips safely (`valueOf(...).getOrDefault(POSTER)` at both the row
  serializer and `globalCardStyle`); no exhaustive `when` on the enum existed
  to break.
- **Fixed size 380×285 (4:3)** — `CINEMA_CARD_WIDTH_DP` / `CINEMA_CARD_HEIGHT_DP`
  in `RowDisplayConfig.kt`. (Started 420×236/16:9, retuned to 380×285/4:3 for an
  Apple-TV feel.) Every layout that computes card dimensions short-circuits to
  these for CINEMA: the resolver forces width; Classic (`resolvePosterCardStyle`),
  Spotlight (`resolveRowCardHeight`/`resolveRowPosterCardStyle`), Modern
  (`ModernRowSection` per-row scaling block + `ModernHomeRowsList` prefetch),
  the home uniform Grid (`HomeScreen.gridPosterCardStyle`, gated on
  `globalCardStyle == CINEMA`), and the Movies/TV grid (`MediaTypeBrowseScreen`).
- `ModernHomePresentation.rowEffectiveLandscape` returns true for CINEMA too so
  Modern bakes the landscape/backdrop image URL.
- **Single fixed size** — the per-row Size picker hides when a row is CINEMA;
  the global Size header hides when **all** rows in scope are CINEMA. LANDSCAPE
  keeps every size option.

### 3-state style selector (Rows Manager, `NewLayoutSettingsScreen.kt`)

- The orientation `▯/▭` toggle became a **3-state style selector** cycling
  **Poster → Landscape → Cinema → Poster** (`nextCardStyle()`), via plain-rectangle
  glyphs whose proportions communicate the style (Poster tall, Landscape wide,
  Cinema ultra-wide). `OrientationGlyph`/`OrientationShapeButton` → `StyleGlyph`/
  `StyleShapeButton`. Applies per-row (`ManagerRowItem`) and globally
  (`ColumnHeaderRow` cycles all rows at once). Legacy ALL-mode `RowItem` +
  `ToggleStylePill` updated to match (3-label, width hidden for cinema).

### Backdrop image + permanent logo overlay for Landscape/Cinema (`ContentCard.kt`, `CatalogRowSection.kt`)

- **Audit first (vs `upstream/dev`):** upstream `ContentCard`/`GridContentCard`
  always load `item.poster`; backdrop is used only during expand-on-focus.
  Upstream's "landscape" is **item-driven** via `item.posterShape` (addon supplies
  a landscape poster as `item.poster`) — there's no per-row card-style override,
  so it never needed backdrop-selection. Our fork's per-row LANDSCAPE/CINEMA is a
  deliberate divergence.
- `ContentCard` gained `cardStyle: LayoutCardStyle = POSTER` (`isWideCardStyle` =
  LANDSCAPE||CINEMA). Image URL: `when { expand && expanded → backdrop; wide →
  backdrop; else → poster }`. The bottom-left scrim+logo overlay (previously only
  during expand) is now **permanent** for wide styles:
  `if (isBackdropExpanded || isWideCardStyle)`. Falls back to title text when no
  logo. `CatalogRowSection` threads `cardStyle` through; Classic + Spotlight
  compute `rowCardStyle` from the resolver and pass it. `MediaTypeBrowseScreen`
  also threads `rowConfig.cardStyle`.
- ⚠️ **Caveat:** the condition keys on `cardStyle`, not `posterShape`, so a
  catalog whose items are *natively* `posterShape == LANDSCAPE` (curated landscape
  posters, sometimes with baked-in titles) will also be swapped to `backdropUrl`.
  If an addon serves such art, gate with `&& item.posterShape != LANDSCAPE`.

### Focus-driven hero collapse (Fix — Spotlight + Modern)

- There was **no** static "cinema row exists → hide hero" check to replace —
  added an explicit **focus-driven** rule reading the *currently focused row's*
  style only.
- **Spotlight** (`SpotlightHomeContent`): new `focusedRowIsCinema` (derived from
  `catalogRows[focusedRowIndex]`'s resolved style); the hero state machine returns
  **State C (HIDDEN)** when the focused row is Cinema, restoring to
  CAROUSEL/CONSTRAINED on any non-Cinema row.
- **Modern** (`ModernHomeContent`): new `focusedRowIsCinema` (from `activeRowKey`
  → `rowByKey` → `layoutConfigKey` → config); an animated `heroCinemaAlpha`
  (220ms) fades the hero backdrop + `HeroTitleBlock` out on a Cinema row and back
  in otherwise. **Caveat:** rows do not reflow — the hero is an absolute backdrop
  layer, so while a Cinema row is focused the top area shows the page background.
- **Classic** left untouched (per spec).

### Landscape sizing bug (Fix — `ContentCard.kt`, `MediaTypeBrowseScreen.kt`)

- **Root cause:** the resolver was correct (`LANDSCAPE → row.cardWidthDp`), but
  `ContentCard.baseCardWidth/Height` derived size **solely from `item.posterShape`**,
  and `PosterShape.LANDSCAPE → 260×148` is hardcoded. So a catalog whose items are
  natively landscape-shaped rendered a LANDSCAPE row at a fixed 260×148, ignoring
  the size selection (POSTER-shape items happened to resize, which masked it).
- **Fix:** when the row's `cardStyle` is an explicit wide override
  (`isWideCardStyle`), size from the resolved `posterCardStyle` instead of the
  `posterShape` hardcode — LANDSCAPE = the selected width (fully resizable),
  CINEMA = the fixed 380×285. Also threaded `cardStyle` into the Movies/TV grid's
  direct `ContentCard` call so its landscape rows resize too.

### Corner radius setting exposed (`GlobalSettingsContent.kt`, `LayoutPreferenceDataStore.kt`)

- The setting already existed in the fork (`poster_card_corner_radius_dp`,
  consumed by the home pipeline) but was only reachable from the dormant
  `LayoutSettingsScreen.kt`. Exposed it under **Appearance → Global → "Card Style"**
  as a **visual selector** (real corner-clipped previews: Sharp 0 / Subtle 4 /
  Classic 8 / Rounded 12 / Pill 16) on `GlobalSettingsViewModel`.
- Made it **truly global:** `posterCardCornerRadiusForScope` now falls non-HOME
  scopes back to the base global key before the hard default, so one value covers
  Home/Movies/TV/Collections. The global card-style picker label was generalized
  to a 3-value `LayoutCardStyle.displayLabel()`.

### Architectural decisions

- **CINEMA dimensions live in two `const`s** (`RowDisplayConfig.kt`); every
  consumer references them symbolically, so retuning size is a one-line change.
- **`isWideCardStyle` (an explicit per-row override) wins over the item's intrinsic
  `posterShape`** for both image selection (backdrop) and sizing — that's the
  hinge for both the backdrop and landscape-resize fixes.
- **Hero collapse is focus-state, never layout-content** — it reads the focused
  row, not whether Cinema rows exist anywhere.

### Pending follow-ups

1. **On-device smoke test** of all Cinema/Landscape changes (sizes, backdrop art,
   logo overlay, hero collapse/restore, landscape resize Compact→Large).
2. **Modern hero alpha fade during Cinema** — top area shows page background, rows
   don't reflow into the freed space. Revisit if it reads poorly.
3. **`posterShape == LANDSCAPE` caveat** — if any addon serves native landscape
   posters with baked-in titles, the backdrop swap may degrade them; gate on
   `posterShape` if observed.
4. Prior follow-ups (Apple-TV "Cinema" *width preset* idea now superseded by the
   real Cinema style; instant-expand flicker; TMDB picker loop-wrap; dead
   `bottomScrimMaxAlpha`/`openProfileOverlay`; Phase 8 localization; 23 skipped
   upstream commits; ContinueWatching render in Classic; SideRail order consumption)
   still open.

### Notes for future sessions

- **CINEMA is a per-row style** — it reaches Modern/Classic/Spotlight rows and the
  Movies/TV grid via Rows Manager. The home *uniform* Grid honors it only through
  the **global** card-style picker (`gridPosterCardStyle`).
- **`ContentCard` now takes `cardStyle`** (default POSTER). To get backdrop +
  permanent logo + per-row sizing on a wide card, the caller must thread the
  resolved style; the default keeps every other `ContentCard` caller poster-only.
- **`upstream/dev` has no per-row card style** — don't expect to port landscape/
  cinema behavior from upstream; it's fork-specific.
