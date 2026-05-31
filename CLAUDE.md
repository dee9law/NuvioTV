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

---

## 📅 Session log — 2026-05-30/31 (State B scrim saga, TopBar pill redesign, Spotlight nav + Show All)

### Headline

Two-day session. Iterated the Spotlight State B hero bottom-scrim fix to a
clean end-state, redesigned the TopBar channel pills (capsule → dynamic
underline) plus six other TopBar tweaks, then landed three Spotlight nav/
content fixes (Up-flood throttle, full catalog rows, leading "Show All" card).
All changes compiled green and installed on the Skyworth TV.

### Features shipped

- **Spotlight State B uses ModernHeroScene exactly like Modern.** Final state
  of the scrim saga: State B's `ModernHeroScene` modifier switched from
  `.fillMaxSize()` to `.height(animatedHeroHeight)` and dropped all scrim
  customization — renders identically to Modern's non-fullscreen hero. The
  expand/shrink dynamics (`animatedHeroHeight`, `constrainedAlpha` cross-fade)
  are untouched.
- **TopBar channel-pill redesign (7 changes, `TopNavigationBar.kt`):**
  1. Removed the channel-pill capsule — `containerColor`/`focusedContainerColor`
     → `Transparent`, `focusedBorder` → `Border.None`, deleted the focus-glow
     shadow + all glow vals.
  2. Dynamic-color underline under selected/focused channel text — 2.5dp,
     width = measured text width (via `onGloballyPositioned`), color from
     `rememberArtworkBackedGlowColor(enabled = hasLogoUrl, fallbackColor =
     channel.brandColor)`. Reserved height (transparent when idle) = no vertical
     jump.
  3. Category pills untouched.
  4. Channel pills edge-to-edge — moved the bar's `start` inset off the outer
     Row onto the Main Section inner Row; Legacy LazyRow `contentPadding` start
     2dp→0dp.
  5. `NavBarHeight` 60dp→54dp (floor for logo+caption+underline pills; lower
     clips logos).
  6. Uniform channel logos — every logo in a fixed `Box(48×24)` + `ContentScale.Fit`.
  7. Profile avatar shrunk — Card 36→26dp, circle 32→22dp.
- **Spotlight row title breathing room** — `compactTitle` rows give the title
  `Text` an 8dp top padding (`CatalogRowSection.kt`).
- **Fix 1 — held D-pad Up no longer floods/skips rows** (`SpotlightHomeContent.kt`).
  (1a) `onPreviewKeyEvent` on the rows `LazyColumn` throttles Up autorepeats to
  one step / 200ms (initial press passes through). (1b)
  `LaunchedEffect(heroHasFocus){ if (heroHasFocus) rowsAreaHasFocus = false }`
  clears a stale rows-focus flag so the next Down re-enters State B.
- **Fix 2 — Spotlight shows the full catalog** (`HomeViewModelCatalogPipeline.kt`).
  The pipeline truncated non-Modern rows to 25 items; extended the Modern
  full-row exemption (`shouldKeepFullRow`) to `HomeLayout.SPOTLIGHT`.
- **Fix 3 — leading "Show All" card on D-pad Left from first poster**
  (`CatalogRowSection.kt`, gated by new `leadingSeeAllEnabled`; Spotlight passes
  `true`). Hidden 1dp/alpha-0 sliver left of the first poster; Left from poster 0
  reveals + focuses it (replacing the profile-menu gesture there), collapses on
  blur; Select → `onSeeAll` → `Screen.CatalogSeeAll`. `Icons.Default.GridView` +
  "Show All". Classic/Grid/Search/FolderDetail default `false` → unchanged.

### Bugs fixed

- **Channel-pill underline showed static sky blue.** `rememberArtworkBackedGlowColor`
  was deleted with the capsule; underline fell back to a flat color. Restored it
  wired to the underline with `enabled = hasLogoUrl` (not the old glow/bloom gate)
  so artwork extraction always runs when a logo exists.
- **Spotlight nav "regression" investigation** — confirmed via `git diff` that the
  focus/back wiring was byte-identical to the last commit; the only delta was the
  State B hero modifier. No wiring was lost; user re-tested.

### New files

None — all edits to existing files.

### Architectural decisions

- **State B = Modern parity over bespoke scrim.** After three scrim approaches
  (`bottomScrimLiftDp`, `backdropRightOnly`, `bottomScrimMaxAlpha`), settled on
  rendering State B identically to Modern's non-fullscreen hero. Simpler and
  consistent; any future title-readability tuning happens in one place.
- **Channel focus = underline + scale, not capsule.** Per redesign; the artwork-
  backed dynamic color ties the indicator to each channel's logo.
- **Fix 1b keyed on `heroHasFocus`, not `heroState == CAROUSEL`.** `heroState ==
  CAROUSEL` is *defined as* `!rowsAreaHasFocus`, so the literal request was a
  no-op; keying on the real hero-focus signal achieves the stated goal.
- **Fix 2 lives in the ViewModel pipeline, not the `CatalogRowSection` call.** The
  25-item cap was upstream in `computedDisplayRows`; fixing it there gives true
  Modern parity (the row carries the full list).

### Pending follow-ups

1. **Dead `bottomScrimMaxAlpha` param in `ModernHomeHero.kt`.** `ModernHeroScene`/
   `ModernHeroGradientLayer` still carry `bottomScrimMaxAlpha: Float = 1.0f`,
   now unused by every caller (default 1.0f = `bgColor.copy(alpha=1f)` = original
   behavior, so harmless). Remove for cleanliness next pass.
- **On-device verification** of all of today's changes (installed, not smoke-tested
  by Claude): Fix 1 Up-throttle + Down-return, Fix 2 long catalogs, Fix 3 Show All
  reveal, TopBar `NavBarHeight=54dp`/avatar `26dp` (most likely to need a dial),
  underline artwork color per channel.
2. **Fix 3 details to eyeball:** the revealed Show All card keeps
   `tvLeftFromFirstItemToSideRail` (one more Left still opens the profile menu —
   change to a hard stop if undesired); collapsed sliver pushes poster 0 ~17dp right.
3. Prior follow-ups (Phase 8 localization, 23 skipped upstream commits,
   ContinueWatching render in Classic, SideRail order consumption) still open.

### Notes for future sessions

- **`nativeKeyEvent` is a member property** of `KeyEvent` — accessed as
  `event.nativeKeyEvent.repeatCount`, **no separate import** (importing
  `androidx.compose.ui.input.key.nativeKeyEvent` fails to resolve).
- **`material-icons-extended` is on the classpath** (`Icons.Default.Tune`,
  `Icons.Default.GridView`, etc. resolve).
- **TopBar sits at `y=0`** in `MainActivity` (no top padding there); the only
  vertical lever is `NavBarHeight` in `TopNavigationBar.kt`. The bar can't go much
  under 54dp without clipping the logo+caption channel pills.
