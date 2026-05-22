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

---

## 📅 Session log — 2026-05-20 (TopBar / Modern Feel polish marathon)

### Headline

Visual-polish sprint focused on the Modern Top Bar, content-card focus
treatment, and a handful of layout regressions from the 05-19 cherry-pick
session. Twelve sub-fixes across nine batched task lists. Introduced
`CardFocusStyle` enum, CompositionLocals (`LocalIsModernFeel`,
`LocalTopBarOverlayHeight`, `LocalPosterGlowEnabled`, `LocalCardFocusStyle`),
DataStore keys (`modern_top_bar_enabled`, `poster_glow_enabled`,
`card_focus_style`), and a real-blur glassmorphism path for the TopBar
via Coil `BlurTransformation`. **Full feature/bug/architecture detail in
`SESSION_HISTORY.md`.**

### Key shipped highlights

- Settings ▸ Appearance ▸ **Top Bar** — Modern Top Bar glassmorphism toggle
  (default OFF). 9-item unified pill catalogue with 3-state display modes.
- Settings ▸ Appearance ▸ **Global** — Card Focus Style cycle (Accent /
  Poster Glow / Border Bloom). Sampling reuses `CollectionCardGlow.kt`.
- Modern edge-to-edge: 16dp consistent buffer on both screen edges.
- TopBar carousel takeover (300ms slide+fade), 6dp dot indicator,
  channel-pill logo fallback to text on Coil error.
- Profile Overlay: 85% panel height + vertical scroll, long-press promote.
- Folder picker: focusable section headers + Select all / Deselect all.
- `CollectionsHomeScreen` wired into `NuvioNavHost` (BackHandler +
  immersion reset).
- Settings Hub left rail: focus highlight repaired + single-expand
  accordion.

### New files

- `domain/model/CardFocusStyle.kt` — focus style enum.
- `domain/model/CategoryPill.kt` (extended) — `CategoryPillDisplayMode` +
  9-entry pill enum with per-pill `defaultVisibility`.

### Pending follow-ups from this session

1. **Test glassmorphism on TV.** APK installed but visual verification
   needed for: (a) blurred hero image actually showing through the
   TopBar in Modern mode, (b) bottom-12dp fade melting into hero,
   (c) text legibility against the worst-case bright hero.
2. **Card Focus Style on TV.** Verify Accent → Glow → Bloom cycles
   correctly in Global settings, and that each mode renders
   distinctly on focused cards in Home / Movies / TV / Discover /
   My Stuff / channel pills.
3. **`HeroBackdropState` ↔ `TopBarImmersionState.backdropUrl` sync.**
   They're maintained in parallel. Future cleanup: consolidate into
   one holder.
4. **`SettingsScreen.kt` (Old)** — still dual-maintained with
   `SettingsHubScreen.kt`. Cleanup candidate when next-touched.
5. **Phase 8 Localization sweep (~25 commits)** — carried over from
   05-19 session, untouched today.
6. **23 skipped upstream commits** — full list in 05-19 session log.
   Highest-value ones still pending:
   `daf4546c` (player exit after CW), `5b2f0819` (next-episode end
   overlay), `f8840d57` (Parental Guide), `08663af4`+`1dfa38ad`
   (forced-subtitle scoring), DiscoverLocation bundle (5-commit).
7. **Hero text inset CompositionLocal unused.**
   `LocalTopBarOverlayHeight` was added speculatively for hero TEXT
   that overlapped the TopBar — but hero text is `BottomStart`
   aligned in all current home variants. Keep the Local around for
   when a future hero design pushes text near the top.
8. **`SidebarNavigation.kt` stub** + the orphaned DataStore keys
   (`modern_sidebar_enabled`, `legacyModernSidebarEnabledKey =
   "glass_sidepanel_enabled"`, `modern_sidebar_blur_enabled`) — dead
   wiring from a previous design iteration. Leave for now; cleanup
   when next-touched.

### Notes for future sessions

- **One push, many small fixes.** Today's session was nine task-list
  rounds with a compile + install after each, plus one EOD push.
  Easier to bisect than the 05-19 75-commit firehose.
- **The user's TV is a Skyworth Android 12 box.** `RenderEffect`
  silently does nothing on its GPU. Stack-blur Coil transform is
  the established fallback pattern (`BlurTransformation.kt`).
- **`LocalCardFocusStyle` is the canonical reading site.**
  `LocalPosterGlowEnabled` is kept only because three earlier
  call sites reference it; new code should prefer the enum directly.
- **The "Modern Top Bar" toggle is OFF by default.** Anyone testing
  needs to flip it in Settings → Appearance → Top Bar → Modern Top
  Bar before the glassmorphism path activates.

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

## 📅 Session log — 2026-05-21 (Long-cycle: Modern hero polish, ARVIO trace + revert, settings reorg, Spotlight layout)

### Headline

Marathon day across ~12 sub-sessions. Two major outcomes:

1. **Modern hero / rows layout** — went through ARVIO-inspired tunings
   (rowsViewport 36%, offset positioning, Arrangement.Bottom, dynamic
   TopBar inset), discovered them to be broken when stacked, and
   surgically reverted the layout patches back to stock while keeping
   the non-layout improvements (text shadows, description cap, 8dp
   row-title gap, focusable titles, dynamic TopBar measurement infra).
   Net: Modern's layout math matches HEAD; only readability/UX tweaks
   stuck.
2. **New SPOTLIGHT layout** — a fourth `HomeLayout` enum value. Fixed
   full-bleed hero (reuses `HeroCarousel` with a new `heroHeight: Dp`
   parameter, defaults preserve Classic / Grid) plus a Modern-style
   bottom strip of rows that animates from 35% → 85% screen height
   when the user moves focus into them. Focused row card drives the
   hero via a single-item items list + `key()` reset and a 140ms
   debounce.

Plus a sizeable settings reorg and a Back-navigation rewrite.

### Key shipped highlights

> **Full feature/bug/architecture detail in `SESSION_HISTORY.md`.**

- **`HomeLayout.SPOTLIGHT`** — fourth layout. New `SpotlightHomeContent.kt`
  (~270 lines). `HeroCarousel` gained `heroHeight: Dp = 400.dp` param;
  `CatalogRowSection` gained `compactTitle: Boolean = false`.
- **Modern hero/rows** — ARVIO-inspired layout tunings attempted then
  surgically reverted; kept: text shadows (`HeroTextShadow`), 72dp
  description cap, 8dp row-title gap, dynamic TopBar measurement infra
  (`TopBarImmersionState.topBarHeightDp` + `LocalTopBarOverlayHeight`).
- **Settings hub reorg** — Appearance sub-items: Cards, Side Rail,
  Detail Page. Ordering: Feel → Layout → Rows → Top Bar → Side Rail →
  Global → Theme → CW → Cards → Detail Page. **Collections** promoted to
  Extensions sub-item w/ Folder icon. **Advanced** flattened via new
  `HubCategory.directContentId`. **About** extracted as top-level
  category. **CardFocusStyle** reduced to `ACCENT`/`BLOOM` (Glow split
  into separate `posterGlowEnabled` boolean).
- **Card focus rendering** — All cards + channel pills now use
  `Modifier.shadow` (TV M3 `Card.glow` no-ops on Skyworth GPU).
- **Navigation** — New `NavHostController.popBackToMainScreen()` helper
  replaces 22 `popBackStack()` lambdas in `NuvioNavHost.kt`. Discover
  Back uses scoped `BackHandler` so it falls through to navhost handler.
  Modern row titles are focusable; addon rows navigate to
  `Screen.CatalogSeeAll`.

### New files

- `ui/screens/home/SpotlightHomeContent.kt` — Spotlight layout host.

### Pending follow-ups

1. **Spotlight on-device testing.** Initial focus retry was added on
   the last cycle but never verified on the TV. Need to confirm the
   slide-up animation, hero swap-on-row-focus, and focus chain all
   work end-to-end.
2. **Spotlight row cycling.** Currently the bottom strip renders the
   full LazyColumn so multiple rows are accessible vertically via
   spatial focus. The "Show Hero Carousel" toggle exists but doesn't
   yet drive initial focus (V2: when toggle = ON, start on the hero;
   when OFF, start on the first row).
3. **Glow clearance vs title gap.** With LazyRow contentPadding at
   4dp the title-to-cards gap is tight, but `Modifier.shadow` with
   elevation 24dp will get clipped. Either re-tune to ~16dp (small
   regression in gap) or migrate to a shadow path that doesn't depend
   on parent contentPadding clearance.
4. **Modern landscape card sizes barely change.** Compact (104) →
   Large (140) maps to landscape height 59dp → 79dp — only a 20dp
   visible delta. Either drop the landscape-size dropdown or add a
   landscape multiplier to `effBaseWidth` in `ModernRowSection`.
5. **Untested everywhere.** The hero text shadows / description cap /
   description-Box constraint / Discover Back / Spotlight everything
   landed on JAWWY-TV-2.0 but no on-device verification this session.
6. **Phase 8 localization sweep** still pending from prior sessions.
7. **23 skipped upstream commits** from 05-19 — still pending. Highest
   value: `daf4546c` (player exit after CW), `5b2f0819` (next-episode
   end overlay), `f8840d57` (Parental Guide), `08663af4`+`1dfa38ad`
   (forced-subtitle scoring), DiscoverLocation 5-commit bundle.

### Notes for future sessions

- The Modern hero layout went through 5 iterations and 1 surgical
  revert this session — resist ARVIO-style rebuilds in this area
  without thoroughly tracing every dp value
  (`rowsViewportHeight` / `heroBackdropHeight` / `heroBottomPadding`)
  first.
- `CardFocusStyle.GLOW` no longer exists — only `ACCENT` / `BLOOM`.
  Legacy persisted value `"glow"` falls back to `ACCENT` via
  `fromStorageValue`. Poster Glow is now the separate
  `posterGlowEnabled` boolean.
