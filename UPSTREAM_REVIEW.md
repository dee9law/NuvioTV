# Upstream Cherry-Pick Review

**Generated:** 2026-05-19
**Source branch:** `upstream/dev`
**Target branch:** `dev`
**Merge-base:** `68b4a34e` (Merge PR #1779 from harrydbarnes/fix-sidebar-icon-shift)

Read-only audit of 9 upstream commits considered for cherry-pick. Each row
reflects analysis of the full diff against our fork's 76 modified files.

---

## Summary Table

| # | Commit | Feature | Type | What Changed (specific) | Functionality / Purpose | Core or UI | Importance | Conflict Risk |
|---|--------|---------|------|-------------------------|--------------------------|------------|------------|----------------|
| 1 | `f5ae7d2e` | Audio amplification + passthrough | Fix / Removed | Drops the `forceAudioProcessingPcmProvider` ctor param + `requiresPcmForAudioProcessing()` helper from `PlaybackSpeedAwareAudioSink.kt`. Removes the early-return in `shouldRejectDirectPlayback()` that flipped audio to PCM whenever the gain processor was enabled. `PlayerRuntimeControllerInitialization.kt` constructs the sink without that provider. | Enabling amplification (gain) no longer forces ExoPlayer to decode to PCM. Surround formats (AC3/EAC3/DTS/TrueHD) can still bitstream-passthrough via HDMI to a receiver while the gain processor runs. Previously, any non-1.0 amplification silently killed passthrough — costly for home-theater users. | Core (player audio pipeline) | **High** | ✅ Safe — both files untouched in our fork |
| 2 | `50f9ce68` | Forced-subtitle flag respected by ExoPlayer | Fix | In `PlayerRuntimeControllerInitialization.kt::initializePlayer`, after track selector setup, calls `setIgnoredTextSelectionFlags(currentFlags or C.SELECTION_FLAG_FORCED)` when `playerSettings.subtitleStyle.useForcedSubtitles == false`. Mirrored in `applySubtitlePreferences()` so the flag flips live. Also tightens addon-subtitle restore: only restores on exact `addonName + id` match (removes the loose "same language" fallback) and clears the stored preference on no-match so auto-selection (which prefers embedded) can run. | When the user disables forced subtitles, ExoPlayer now actually ignores forced tracks instead of auto-selecting them (forced tracks typically only show foreign-language signs/alien dialogue — surprising to users who said "no subs"). Plus fixes a stale-preference bug where a previously-chosen addon subtitle would override a better embedded track on a different file. | Core (player track selection) | **High** | ✅ Safe — both files untouched in our fork |
| 3 | `c7d37d68` | Binge-group source reuse during Manual auto-play | Fix | Single 2-line change in `core/player/StreamAutoPlayPolicy.kt::isEffectivelyEnabled`: adds `if (streamAutoPlayReuseBingeGroup && streamAutoPlayPreferBingeGroupForNextEpisode) return true` before the `MANUAL → false` branch. | When a user sets "Auto play next episode" to Manual but also enables "Reuse last source for binge group", that combination is now honored — the same source/quality is automatically queued for the next episode of a binge. Previously the Manual setting silently disabled the binge-group reuse. | Core (auto-play policy) | **Medium** | ✅ Safe — file untouched in our fork |
| 4 | `9638b2fa` | Direct-debrid streams resolved before playback | Fix | Injects `DirectDebridResolver` into `PlayerRuntimeController` ctor + `PlayerViewModel.@Inject`. New extension `resolveDirectDebridStreamIfNeeded()` in `PlayerRuntimeControllerStreams.kt` calls `directDebridResolver.resolveToPlayableStream(stream, season, episode)`. In `switchToSourceStream`, `switchToEpisodeStream`, and `playNextEpisode`: when a stream has `isDirectDebrid()` and a blank URL, launch the resolver, then recursively call the switch with the resolved stream (or show invalid-URL error on failure). | Direct-debrid sources (Real-Debrid / AllDebrid / Premiumize "direct" links) typically ship with placeholder URLs that need to be resolved against the user's debrid API key. Before this fix, those sources errored immediately with "invalid URL". Now resolution happens just-in-time and the stream plays. | Core (player streams) | **High** | ✅ Safe — all 3 files untouched in our fork. **Must precede #5.** |
| 5 | `5847e6c9` | Debrid resolve job lifecycle + loading state | Fix (follow-up to #4) | Adds `debridResolveJob: Job?` field to `PlayerRuntimeController`. `releasePlayer()` cancels and nulls it. In both `switchToSourceStream` and `switchToEpisodeStream` debrid branches: cancels any in-flight `debridResolveJob`, flips `isLoadingSourceStreams`/`isLoadingEpisodeStreams = true` and clears error before launching the resolve, then clears loading on error. | Hardens #4. Without this, switching streams mid-resolve leaks coroutines; users see no spinner during the (sometimes slow) debrid API round-trip; failure leaves the UI in a permanent "loading" state. With this, the spinner shows during resolve, stale jobs are cancelled, and the loading flag clears on both success and failure. | Core (player streams) | **High** | ✅ Safe — files untouched. **Must follow #4.** |
| 6 | `0f17247e` | Locale XML fixes (de + es-419) | Fix | `values-de/strings.xml`: fixes a broken closing tag (`Gerät/string>` → `Gerät</string>`); removes two stray German strings (`playback_player_auto`, `playback_player_auto_desc`) that had been accidentally pasted inside the `collections_*` block. `values-b+es+419/strings.xml`: removes a duplicate `collections_empty` key. | Repairs a malformed XML in the German locale that could cause an `aapt2` resource compilation failure on German builds, and dedupes a Spanish (LATAM) collections key. No behavior change for English users. | UI (localization) | **Medium-High** (German build risk) | ✅ Safe — non-default locale XMLs not modified in our fork |
| 7 | `80daab45` | Subtitle color picker focus + clipping | Fix | `PlaybackSettingsScreen.kt::ColorSelectionDialog`: computes `focusedColorIndex` from the current chip's ARGB, passes `rememberLazyListState(initialFirstVisibleItemIndex = focusedColorIndex)` so the LazyRow opens already scrolled to the user's color. Adds `contentPadding = PaddingValues(horizontal = 8dp)` so the focused chip's outer glow ring isn't clipped. Moves the `focusRequester` from "color equals currentChipColor" to "index == focusedColorIndex" (prevents two same-color chips from competing). Replaces bare `requestFocus()` with `requestFocusAfterFrames()` helper and changes the `LaunchedEffect` key from `Unit` to `focusedColorIndex` so re-opens refocus correctly. | When the user opens "Subtitle text color" dialog, it now scrolls to and focuses the currently-selected color, and the highlighted chip's glow no longer clips against the dialog edge. Small but visible polish. | UI | **Medium** | ⚠️ Caution — `PlaybackSettingsScreen.kt` modified in our fork at lines 441-461 (`ToggleSettingsItem`); upstream edits are at lines 1107-1296 (`ColorSelectionDialog`). ~600 lines apart, only realistic conflict is in import block. **Verify**: `requestFocusAfterFrames` helper from `ui/screens/detail/` must exist in our branch. |
| 8 | `fa6e3bed` | TMDB language list adds en-AU / en-CA / en-GB | New | `PlayerSettingsDataStore.kt`: defines `AVAILABLE_TMDB_LANGUAGES = AVAILABLE_SUBTITLE_LANGUAGES + listOf(en-AU, en-CA, en-GB)`. `PlaybackSettingsScreen.kt::LanguageSelectionDialog`: branches the dropdown list by comparing the dialog `title` to `stringResource(R.string.tmdb_language_dialog_title)` — TMDB gets the expanded list, subtitle dialogs keep the original. `TmdbSettingsScreen.kt`: swaps `AVAILABLE_SUBTITLE_LANGUAGES` → `AVAILABLE_TMDB_LANGUAGES` for the display-name lookup. | Australian, Canadian, and British English users can now pick their regional variant for TMDB metadata. TMDB returns slightly different titles/availability info per regional code. Subtitle language picker is unchanged (no en-GB subtitles in the wild). | UI + Data | **Medium** | ⚠️ Caution — same `PlaybackSettingsScreen.kt` overlap as #7 (different section). **Verify**: the dialog-title comparison relies on `R.string.tmdb_language_dialog_title` existing in our resources — confirm before picking. |
| 9 | `97ca15f5` | Relative "Airs today / tomorrow / in N days" labels | New | New `ui/util/AirDateUtils.kt` with `parseEpisodeReleaseDate()` (extracted verbatim from `HomeViewModelContinueWatching.kt`) + `computeAirDateBadgeText(context, releasedIso, airDateLabel)` that returns: `cw_airs_today` (0d), `cw_airs_tomorrow` (1d), `cw_airs_in_days` plural (2-7d), `cw_airs_date <label>` (>7d), or null when no ISO date. `ContinueWatchingSection.kt` (2 callsites) and `ModernHomeModels.kt::buildContinueWatchingItem` swap from `stringResource(R.string.cw_airs_date, label)` to `computeAirDateBadgeText(context, info.released, info.airDateLabel)`. `HomeViewModelContinueWatching.kt` deletes the now-relocated `parseEpisodeReleaseDate` body. Adds 3 string resources to `values/strings.xml` + 27 locale variants (28 files total, with proper plural forms per language). | Continue Watching cards for episodes airing within a week now show "Airs today", "Airs tomorrow", "Airs in 3 days" instead of "Airs May 20" — easier to scan and more emotionally resonant for high-engagement series. Beyond 7 days, the existing absolute-date label is retained. | UI | **High** (high-traffic surface) | ❌ High — `HomeViewModelContinueWatching.kt` was wholesale renamed `HomeViewModel.X` → `BaseHomeViewModel.X` on 17 receivers in our fork. The pick will likely apply (it touches a different block — the deleted `parseEpisodeReleaseDate` function), but the new `AirDateUtils.kt` is callable from any receiver type, so no semantic clash there. **Real risk:** `ModernHomeModels.kt` (we added `HeroCarouselRow` near line 120; upstream touches lines 11 + 292 — different blocks, should be clean) and `values/strings.xml` adjacency (we added keys near line 100; upstream adds at line 151 — likely OK but XML conflicts on adjacent lines are common). Budget time for a manual hunk resolution and a post-pick compile check. |

---

## Recommended pick order

```
1. f5ae7d2e   # safe, audio amplification (ordering anchor for #2 — same file)
2. 50f9ce68   # safe, forced-flag (after #1, both touch PlayerRuntimeControllerInitialization.kt)
3. c7d37d68   # safe, single-file binge-group fix
4. 9638b2fa   # safe, debrid resolve (MUST precede #5)
5. 5847e6c9   # safe, debrid lifecycle fix (depends on #4)
6. 0f17247e   # safe, locale XML repairs
7. 80daab45   # caution, color picker — different hunk in PlaybackSettingsScreen.kt
8. fa6e3bed   # caution, TMDB locales — different hunk in PlaybackSettingsScreen.kt (after #7 to share any import merge)
9. 97ca15f5   # high-risk, relative air dates — manual resolution likely; compile + test CW row after
```

---

## Importance vs. effort tier list

| Tier | Picks | Why |
|------|-------|-----|
| **Must ship** (high importance, low risk) | 1, 2, 4, 5 | Material player-engine fixes (amplification, forced subs, debrid). No conflicts. Highest user-visible payoff per minute of work. |
| **Should ship** (medium-high importance, low risk) | 3, 6 | Binge-group reuse fix; German build-safety. Trivial picks. |
| **Polish ship** (medium importance, mild caution) | 7, 8 | Subtitle color picker UX; AU/CA/UK TMDB. Easy to verify visually after picking. |
| **Care ship** (high importance, high risk) | 9 | Relative air dates is visible everywhere on Continue Watching, but the receiver rename in our fork plus 28 XML changes warrants slow, careful merging. Run after a clean compile on items 1-8. |

---

## Verification checklist before picking

- [ ] **#7 `80daab45`**: confirm `requestFocusAfterFrames` exists at `ui/screens/detail/` in our branch (it's an extension fn on `FocusRequester`).
- [ ] **#8 `fa6e3bed`**: confirm `R.string.tmdb_language_dialog_title` exists in our `values/strings.xml`.
- [ ] **#9 `97ca15f5`**: after pick, verify `AirDateUtils.kt` compiles given our `BaseHomeViewModel` receiver rename (it should — it's a free function, no receiver).
- [ ] After each pick: `./gradlew :app:assembleFullDebug` per CLAUDE.md rule "Compile after every group/step before moving to the next."
- [ ] Commit each pick individually so a bad merge can be reverted cleanly.

---

## Skipped from this review

- `9c0a547d` (merge commit "Merge branch 'NuvioMedia:dev' into indonesian-locale") — drags in 97 unrelated files. If the Indonesian locale string is wanted, pick the second parent `041856a2` instead.
