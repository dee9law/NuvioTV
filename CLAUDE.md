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

## 📌 Current State (as of 2026-06-08)

### Recently shipped (on `origin/dev`, compiled green; ⚠️ engine sync NOT yet on-device)
- **Upstream sync Phase 1 (engine likely-clean)** — 13 upstream commits
  incorporated: player AFR/24fps-judder fixes, AFR-for-m3u8, subtitle filename
  urlencode, AniSkip `types[]` + skip types, episode-rating dup-key fix, updater
  border, SPL language map, user-agent, **Let's Encrypt legacy-Android TLS**
  (bundled ISRG certs + `network_security_config.xml`), **NuvioDialog CEC
  select-key state machine** (manual port). Compiled green; **smoke test pending**.
- **Docs restructured** — CLAUDE.md trimmed; new `ARCHITECTURE.md`;
  `PHASE1_PICKLIST.md` upstream audit (`0.6.18..0.7.4`, 237 commits).
- **Trakt catalog pipeline** / **For You** screen / **CW subsystem** /
  **Immersive** 5th layout — prior sessions, on-device-verified.

### Upstream sync status (see `PHASE1_PICKLIST.md`)
- Latest upstream = `0.7.4-beta`. Phase 1 i18n = **not viable** via cherry-pick
  (all locales diverged → conflicts/malformed XML; needs wholesale locale
  refresh — the lint-baselined Phase 8 debt).
- **Next: the 76 conflict-prone engine commits** — dedicated review-then-port
  session (45 player / 14 stream-debrid / 11 other / 6 dolby-vision).
- **Fork lacks** upstream's `DolbyVision`/HDR-strip, `StreamBadge`, `CloudLibrary`
  subsystems (0 files each) — commits depending on them are unpickable.

### Open follow-ups (priority order)
1. **On-device smoke test** of the 13 engine picks (Let's Encrypt TLS, AFR,
   NuvioDialog CEC, subtitle urlencode, AniSkip).
2. **The 76 conflict-prone upstream engine commits** — dedicated session.
3. **Performance + Apple-TV animations** session.
4. **Modern State-2 hero proportions** — metadata behind TopBar; match Spotlight
   State B exactly.
5. **CW data quality** — phantom Up Next on ended/fully-watched shows.
6. i18n wholesale locale refresh (optional); settings(30)/visual(29)/engine-
   high-risk(6) sync buckets; 23 older skipped commits; `Watchlist/New Movies`
   on-screen verify; Classic CW render; SideRail order; misc loop-scroll.

---

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
