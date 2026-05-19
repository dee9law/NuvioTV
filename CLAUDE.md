# NuvioTV — Project Rules & Architecture Reference

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

## 📅 Session log — 2026-05-19 (Upstream cherry-pick marathon)

### Headline

Cherry-picked **68 upstream commits** across 7 phases from
`UPSTREAM_RELEASES_AUDIT.md`. 5 session fix-up commits to handle missing
prerequisites. 23 commits intentionally skipped (conflict on
Feel-system files or too-complex restructures). 1 EOD-protocol doc
commit. Total **75 commits pushed** to `origin/dev`. Build green
throughout. APK built and installed to TV at 192.168.8.116.

### Features shipped (user-visible)

- **Still-Watching prompt** (Phase 3, commits `5455e764` / `e299e980`
  / `a9a6e08d`) — prompts user after N consecutive auto-played episodes.
  Settings live in Settings → Playback inline content (auto-rendered
  via existing `PlaybackAutoPlaySettings`).
- **Autoplay timeout 15/20/25/30s options** (Phase 4, 12-commit bundle
  `06fd4dbc` → … → `4199ddac`) — `PlayerSettings`
  `STREAM_AUTOPLAY_TIMEOUT_VALUES` + migration helper + discrete-value
  slider UI.
- **5-profile support** (`13569b08`) — raised cap from 4 → 5.
- **Debrid integration** (Phase 7, 7-commit bundle `8b77cc5b` → …
  → `dbfd4038`) — new `core/debrid/` module (Real-Debrid + Torbox
  direct-debrid resolvers, formatter web UI, sort/filter, precache).
  Wired into Settings → Extensions → **Debrid** (new sub-item at
  `SettingsHubScreen.kt:546` content + `:646` list entry).
- **Mark previous seasons as watched** (`4e38c5f2`) — affordance on
  series detail EpisodesSection.
- **More Like This source toggle** (`315a1709`) — Trakt vs TMDB picker
  in Trakt settings.
- **Audio amplification with HDMI passthrough** (`d84f704b`, `d3a0a8e3`)
  — amplification no longer forces PCM, surround formats can still
  bitstream.
- **Forced subtitle flag respect** (already in HEAD as `011c7ca1`) plus
  scoring (`08663af4` — skipped, port pending).
- **Continue Watching air-date relative labels** ("today/tomorrow/in N
  days") via `AirDateUtils.kt` — already merged in commit `8a17c5f0`
  from prior session.

### Critical bugs fixed

- **ExoPlayer resume race** (`91fcb312` / upstream `114fb05f`) — fixes
  buffering hang / 0:00 resume bug.
- **Un-pushed local progress wipe** (`bc75a31b` / `74f5ccc0` / `1afde382`
  — upstream `d9df6242` + `0688ec76` + `2e9fec9b`) — adds
  `lastSuccessfulPushMs` failsafe so local watched items / progress
  entries created after the last push are preserved across a fresh
  pull. **Data-loss prevention** — highest priority of the session.
- **ProfileSettingsSyncService ClassCastException** (`428d176a`) — wraps
  bracket access with `runCatching {…}.getOrNull()`.
- **ExoPlayer teardown order** (`911d4c99` / upstream `4b9350e5` +
  `d4f3ad73`) — drains renderers before detaching surface;
  hardens audio fallback.
- **A/V desync at playback start** (`5351ad3c`) — defers playback
  until first video frame via `playWhenReady=false` + onRenderedFirstFrame.
- **Tunneled-playback first-frame bypass** (`2487b75e`) — skips deferral
  when tunneled decoding is in use.

### New files created

- `app/src/main/java/com/nuvio/tv/core/debrid/` — 14 files
  (`DirectDebridResolver.kt`, `DirectDebridStreamPreparer.kt`,
  `RealDebridDirectDebridResolver.kt`, `TorboxDirectDebridResolver.kt`,
  `DebridFileSelection.kt`, `DebridProvider.kt`, `DebridStreamFormatter.kt`,
  `DebridStreamFormatterDefaults.kt`, `DebridStreamTemplateEngine.kt`,
  `DirectDebridConfigEncoder.kt`, `DirectDebridStreamFilter.kt`,
  `DirectDebridStreamSource.kt`, `RealDebridFileSelector.kt`,
  `TorboxFileSelector.kt`).
- `app/src/main/java/com/nuvio/tv/core/server/DebridFormatterConfigServer.kt` +
  `DebridFormatterWebPage.kt` — in-app web UI for formatter config.
- `app/src/main/java/com/nuvio/tv/ui/screens/settings/DebridSettingsScreen.kt`
  + `DebridSettingsViewModel.kt`.
- `app/src/main/java/com/nuvio/tv/ui/util/AirDateUtils.kt` —
  `parseEpisodeReleaseDate()` + `computeAirDateBadgeText()`.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PostPlayOverlay.kt`,
  `PlayerAutoplaySessionRules.kt`, `PlayerRuntimeControllerStillWatching.kt`.
- `app/src/main/java/com/nuvio/tv/data/local/BingeGroupCacheDataStore.kt`.
- `APP_STATUS_REPORT.md`, `UPSTREAM_REVIEW.md`,
  `UPSTREAM_RELEASES_AUDIT.md` — planning/audit artifacts at repo root.

### Architectural decisions

- **Settings wiring rule applied:** Debrid settings live under Settings
  → Extensions (per the user's directive: TMDB/enrichment → Extensions).
  Still-watching settings auto-render inside Playback's existing
  `PlaybackAutoPlaySettings.autoPlaySettingsItems` LazyListScope — no
  new sub-item needed.
- **Skip rule honored:** No commits picked into `MainActivity.kt`,
  `SettingsHubScreen.kt` body, `TopNavigationBar.kt`, `SideRail.kt`,
  `HomeScreen.kt`. Conflicts in those files → abort + skip.
- **`SettingsPickerOption<T>` data class** ported from upstream
  `SettingsDesignSystem.kt` to unblock the debrid bundle without
  pulling in the larger `a78c4bcc` SettingsMultiChoiceDialog refactor.
- **`StreamRepositoryImpl` plugin helpers** (`ScraperInfo.pluginAddonName`,
  `LocalScraperResult.toPluginStream`, `Stream.dedupKey`) taken from
  upstream verbatim — required imports added to bridge to our existing
  `domain/model/Plugin.kt` types.
- **Reverted `e12a0592`** (DiscoverLocation cross-profile sync) because
  the foundational `5ce6f798` (DiscoverLocation enum + DataStore
  migration) conflicted and was skipped.
- **Old `SettingsScreen.kt`**: kept it compiling by adding the new
  `integrationDebridFocusRequester` param wiring. Note: this screen is
  no longer the canonical settings entry (SettingsHubScreen is).
  Eventual cleanup candidate.

### Pending follow-ups from this session

1. **Phase 8 — Localization sweep** (~25 commits) — all locale-only
   `values-*/strings.xml` updates batched per the audit. Plan a single
   focused day to pick them all together.
2. **23 skipped upstream commits** — listed by phase in the final
   cherry-pick report in chat. Highest-value ones to port manually:
   - `daf4546c` (player exit after CW) — NuvioNavHost + `PlaybackEnded`
     callback rewire.
   - `5b2f0819` (next-episode end overlay) — NuvioNavHost + new
     `NextEpisodeEndPromptOverlay.kt`.
   - `f8840d57` (Parental Guide setting) — PlayerSettings schema +
     PlaybackSettingsScreen UI.
   - `08663af4` + `1dfa38ad` (forced-subtitle scoring) —
     `PlayerSubtitleUtils.kt` + `PlayerRuntimeControllerTracks.kt`.
   - DiscoverLocation bundle (`5ce6f798` + `7e3d4953` + `39a23fb2` +
     `d179b69d` + `99a1b307`) — needs hand-port through our Feel system.
3. **Debrid TV testing** — APK installed but no smoke test performed.
   Verify Real-Debrid API key dialog works, sort/filter UI navigates
   on D-pad, precache fires on stream-list load.
4. **Still-watching TV testing** — same caveat: untested. Verify
   "Are you still watching?" prompt fires after threshold episodes
   auto-play, and that toggling the setting hides it.
5. **5-profile support TV testing** — verify profile picker exposes
   the 5th slot.

### Notes for future sessions

- The session added **75 commits** in one push (`fd22bb35..4290ef81`).
  Future EODs should keep push count down per session to make
  bisection feasible.
- The `core/debrid/` module is now in the codebase but its web-UI
  server (`DebridFormatterConfigServer`) needs a port-binding decision
  before it can serve formatter config on the LAN.
- `SettingsScreen.kt` is dual-maintained with `SettingsHubScreen.kt`.
  When a future setting needs wiring, prefer `SettingsHubScreen.kt`
  (the canonical hub) per existing rule in this file.

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
