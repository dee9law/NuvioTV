# NuvioTV — Project Rules & State

> Companion docs:
> - **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** — subsystem architecture, key
>   files, layouts, pipelines, enums. Read it before touching a subsystem.
> - **[`SESSION_HISTORY.md`](./SESSION_HISTORY.md)** — full per-session logs.
>
> Complements the per-user global rules in `~/.claude/CLAUDE.md`.

---

## Project Overview

- **App:** NuvioTV fork (Android TV, Kotlin + Jetpack Compose)
- **Architecture:** Clean — `core/`, `data/`, `domain/`, `ui/`
- **Branch:** work on `dev`; PRs usually target `main`
- **Test device:** Skyworth/Jawwy TV, `com.nuviodebug.com`, 960×540dp (1080p @2x)
- **Forks:**
  - `origin` → `git@github.com:dee9law/NuvioTV.git` (your fork)
  - `upstream` → `https://github.com/NuvioMedia/NuvioTV.git` (original)
- **Headline systems:** Feel dual-nav shell (Modern/Legacy), 5 home layouts
  (Classic/Modern/Immersive/Spotlight/Grid), configurable Rows Manager,
  Continue Watching subsystem, Trakt catalog pipeline, For You screen. See
  `ARCHITECTURE.md` for how each works.

---

## Build / Deploy

```bash
./gradlew :app:assembleFullDebug          # build
./gradlew :app:compileFullDebugKotlin     # compile check (use after each step)
./gradlew :app:installFullDebug           # build + ADB push to connected TV
adb connect <tv-ip>:5555                  # IP drifts — check `adb devices` first
```
- Target APK variant: `app-full-armeabi-v7a-debug.apk`
- Skyworth GPU no-ops `RenderEffect` (blur) and TV M3 `Card.glow` — use Coil
  `BlurTransformation` and `Modifier.shadow` respectively.

---

## 🚫 Working Rules (authoritative — do not violate)

### Architecture discipline
- **3-tier setting resolution (per-row → per-screen → global) is authoritative
  — never bypass it.**
- Do NOT use `collectAsState()` for layout flows — use
  `collectAsStateWithLifecycle()`.
- Collections screen (`CollectionsHomeScreen.kt`) is **exempt** from layout
  changes — never touch it.
- `LayoutSettingsScreen.kt` (Old Layout) is **dormant** — don't reference or
  delete until told.
- Never delete commented-out code marked
  `// TODO: re-enable for discovery mode later`.
- CW / Up Next / scrobble pipeline (`HomeViewModelContinueWatching.kt`,
  `TraktProgressService.kt`, `TraktScrobbleService.kt`) is load-bearing —
  leave untouched unless the task is explicitly about it.

### Process
- **Compile after every group/step** before moving on. **Never push broken code.**
- Always create a git commit checkpoint before any major refactor.
- Do NOT fan out subagents when tasks touch `LayoutPreferenceDataStore.kt`,
  `MainActivity.kt`, or `NuvioNavHost.kt` simultaneously.
- Before coding a new feature, consult `REFERENCE_APPS.md` for an
  ARVIO-equivalent and offer to adopt/adapt (ARVIO is the feature-reference app).
- Back from any SideRail-reachable screen's first content item → top nav in one
  press; never via a sub-nav pane.

### Safety (from global rules)
- Ask before `rm -rf`/destructive commands, and before any network call
  (`curl`/`wget`/etc.). Flag with 🚩 "Council Claude Recommended — confirm".

---

## 📌 Current State (as of 2026-06-10)

### Recently shipped (2026-06-10 — all ON-DEVICE VERIFIED unless noted)
- **Global Back-trap fix** — new `LocalContentBackFallback` (MainActivity.kt):
  all four home-content composables' terminal "Back → TopBar" steps defer to
  it; FolderDetail provides its route-pop. Cures the Back trap in folders for
  every layout/folder/entry point. See ARCHITECTURE.md → FolderDetail Back trap.
- **Per-folder presentation + Spotlight in FolderDetail** — 3-tier resolution
  (folder `folder_layout` metadata in COLLECTIONS scope → collection.viewMode
  → TABBED_GRID); unified picker Default·Tabs·Rows·Classic·Modern·Immersive·
  Spotlight·Grid (COLLECTIONS scope only); Spotlight is a real FolderDetail
  layout (was Classic fallback); editor View Mode subtitle = tier-2 default.
- **Collections 3-level accordion in Rows Manager** — collection→folder→catalog
  tree with block move/enable/delete, folder visibility (pipeline
  `collectionFolderVisibility` filtering — per-folder rows finally real),
  data-level folder/source reorder+delete (confirm dialogs + sync push),
  Edit→CollectionEditor nav. COLLECTIONS scope tab now functional (was dead).
  LEVEL 3 Orient/Size/On-Off persist but are render-inert (follow-up #1).
- **Nav-polish 4-fix batch**: (1) Immersive first-card "no image" = AIO
  Metadata addon's btttr.cc auto-generated rating-posters for brand-new
  titles — NOT an app bug, no code change; (2) **closed horizontal rows**
  everywhere (`tvLeftFromFirstItemToSideRail` always consumes;
  `tvStopRightAtLastItem` on last items) with TopBar channel loop intact —
  note: the avatar is a hard left edge (old avatar-wrap doc was stale);
  (3) category↔channel divider vertically centred; (4) selected category dash
  NEUTRAL white, icon/text stay accent.
- Previous (2026-06-09): TopBar pill rebuild, custom buffer engine, Fusion
  Style/Size badges, upstream Phase-1 engine picks — see SESSION_HISTORY.md.

### Upstream sync status (see `PHASE1_PICKLIST.md`)
- Latest upstream = `0.7.4-beta`. Cherry-pick-based sync is EXHAUSTED —
  remaining value requires manual review-then-port (buffer engine + badges
  were the first such ports).
- i18n not viable via cherry-pick (wholesale locale refresh = Phase 8 debt).
- Fork lacks upstream `DolbyVision`/HDR-strip + `CloudLibrary` subsystems.

### Open follow-ups (priority order)
1. **Wire LEVEL 3 source Orient/Size/On-Off into FolderDetail rendering**
   (FolderDetail card style is hardcoded `PosterCardDefaults`).
2. **Spotlight home layout: render collection rows** (`SpotlightHomeContent`
   has no `HomeRow.CollectionRow` branch — collection rows invisible there).
3. Folder-editor deep link from accordion LEVEL 2 Edit (optional `folderId`
   nav-arg on CollectionEditor, ~5 lines).
4. Eyeball-verify (code-verified only): home-row-card folder entry point,
   Movies-scope accordion Layout-chip hidden, editor View Mode subtitle.
5. Optional badge polish — import a real badge JSON URL, confirm Style chips.
6. Performance + Apple-TV animations session.
7. ~76 conflict-prone player/stream upstream commits — manual review-then-port.
8. On-device verify of the 13 engine picks (Let's Encrypt TLS, AFR,
   NuvioDialog CEC, subtitle urlencode, AniSkip).
9. Modern State-2 hero proportions; CW data quality (phantom Up Next); i18n
   wholesale locale refresh; settings(30)/visual(29) sync buckets; misc.

## EOD Protocol

When the user says **EOD**, execute the full End of Day protocol:

1. **Compile check:** if code changed since last green build, run
   `./gradlew :app:compileFullDebugKotlin`. Never push broken code.
2. `git add -A && git status`
3. Unstage any `.idea/`, `.DS_Store`, or IDE artifacts.
4. Update docs per **Session Rules** below.
5. Commit with a descriptive message summarizing the session.
6. `git push origin dev`
7. **Verify:** `git log --oneline -5` and `git status` — clean tree + pushed.
8. **Print session summary:** files changed; features shipped (mark
   **tested on TV** vs **untested**); bugs fixed; pending follow-ups; anything
   promised but not delivered; last build status; last APK deployed (yes/no +
   what was tested).

---

## Session Rules

At the end of every session — before the final commit/push — automatically:

- **Append a full session log to `SESSION_HISTORY.md`** (new files + purpose,
  architectural decisions + why, features shipped, bugs fixed + root cause,
  pending follow-ups).
- **Update `ARCHITECTURE.md`** if any subsystem architecture / key files /
  enums changed.
- **Update `CLAUDE.md` "Current State"** (recently shipped + open follow-ups)
  and any rule changes.

Commit the doc updates as part of the final push. **Do not ask — just do it.**
Keep `CLAUDE.md` lean (target < 15k chars); detail belongs in the companion docs.
