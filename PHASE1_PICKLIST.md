# Phase 1 Upstream Pick-List — Auto-Safe Batch

> **Report/planning artifact only — NO cherry-picks performed.**
> Generated 2026-06-08. Source range: `0.6.18-beta..0.7.4-beta`
> (our last sync → current upstream latest `0.7.4-beta`).
> Spans 4+ release windows: 0.6.20 / 0.6.21 / 0.7.0 / 0.7.1 / 0.7.2 / 0.7.3 / 0.7.4.

## How to use
- **Order = top-to-bottom (oldest → newest).** Cherry-pick in this order to
  minimize conflicts (`git cherry-pick <hash>`).
- Phase 1 = **i18n (82) + clean engine/bug (94)** — auto-safe to batch.
- The **18 sync/progress/Trakt** commits are listed separately: they pass the
  file-safety filter but touch the protected progress/scrobble area
  (`HomeViewModelContinueWatching` / `TraktProgressService` / `TraktScrobbleService`
  rule). **Review each before taking.**
- **Excluded from Phase 1:** 12 SETTINGS/UI, 26 VISUAL, 5 engine-HIGH-RISK
  (see Phase 2/3). Not in this file.
- ⚠️ After picking i18n: re-run `./gradlew updateLintBaseline` + commit
  `lint-baseline.xml` (default-only strings fail `lintVitalFullDebug`).

---

## A. i18n / strings — auto-safe (82 commits)

Bulk-pickable in one batch. Locale `values-*` / `strings.xml` only.

  1. `94c26872d`  2026-05-18  update Indonesian locale
  2. `8c4ef1a5d`  2026-05-18  changed "Who's watching?" translation
  3. `142749b60`  2026-05-18  added translations
  4. `ec5153eee`  2026-05-18  add Tamil translation
  5. `c00de61b3`  2026-05-18  translated serval strings to german
  6. `8ccfe0484`  2026-05-18  fix(i18n): translate Debrid integration and FFmpeg downmix settings to French
  7. `fd3189d36`  2026-05-18  fix(i18n): shorten French donate button so it is not truncated
  8. `03fb63732`  2026-05-18  Latin American Spanish (es-419) Translation Update
  9. `2e64ba786`  2026-05-19  Add "Reuse Binge Group" setting
 10. `97ca15f5c`  2026-05-18  feat: relative air date labels for Continue Watching (today/tomorrow/in X days)
 11. `7183945b9`  2026-05-19  add 83 missing Indonesian translation
 12. `0f17247ee`  2026-05-19  Fixes for translations errors
 13. `290ea6426`  2026-05-19  Add Italian translations for various strings
 14. `5d0e8b2c8`  2026-05-19  Update strings.xml
 15. `7f518f897`  2026-05-19  chore: adding prefetch warning note
 16. `e61da0ea8`  2026-05-19  Fix broken > in pt-BR translation
 17. `463718c69`  2026-05-20  update Indonesian localization
 18. `af1143333`  2026-05-20  fix(i18n): translate 3 missing French strings (binge group reuse, debrid warning)
 19. `9a2bbf96a`  2026-05-20  Normalize Arabic resource filename for Android
 20. `b40eb2434`  2026-05-20  Update strings.xml
 21. `487c92a37`  2026-05-20  i18n(el): add 416 missing Greek string translations
 22. `1fe536e05`  2026-05-20  fix(i18n): replace awkward French 'par récence' with natural phrasing
 23. `4a9f0acbb`  2026-05-20  fix(i18n): fix three awkward/incorrect French translations
 24. `f73e68f42`  2026-05-21  Update string resources for consistency in casing
 25. `ddc883576`  2026-05-21  Title: Update Hungarian translation add missing strings
 26. `c6d696435`  2026-05-21  feat: cloud service
 27. `de583c765`  2026-05-22  airs in translation
 28. `7e5783df4`  2026-05-22  feat: add support for addon enable/disable flag
 29. `327117832`  2026-05-23  feat: adding cloudservice
 30. `e8892fadb`  2026-05-23  feat: remove deprecated UI components
 31. `4ca5fe17c`  2026-05-23  ref(cloud): card and emty state layout changes
 32. `8c619a96e`  2026-05-23  update Indonesian localization to follow recently added features
 33. `e40ca799f`  2026-05-23  feat: integrating qr code gen
 34. `f8c2ec240`  2026-05-23  update Indonesian translation in accordance to the v0.6.20 update
 35. `48b589e9e`  2026-05-23  fix(i18n): polish Greek terminology
 36. `fce899c94`  2026-05-23  fix(i18n): complete Greek locale parity
 37. `3a0f2813c`  2026-05-23  i18n: synchronize Turkish translations and add missing keys
 38. `fa806d9a7`  2026-05-24  Update Polish translations
 39. `f8a36fcf0`  2026-05-24  i18n(fr): consolidate translations - extract Debrid settings, add cloud library, quality audit
 40. `5214cdf4e`  2026-05-24  fix(i18n): remove orphaned Indonesian string that breaks lint
 41. `53b96fc78`  2026-05-25  feat(i18n): add 34 missing Indonesian translation
 42. `07508aa6f`  2026-05-24  Added Simplified Chinese Locale support.
 43. `5e25788bc`  2026-05-25  i18n(fr): extract 7 remaining hardcoded strings + refactor safeApiCall with Context
 44. `00c9d1216`  2026-05-25  Add a blank line to xml.
 45. `28e5f6a79`  2026-05-25  Update strings.xml
 46. `7d1a3516b`  2026-05-25  Update Spanish (LatAm) localization strings
 47. `58ebfee3f`  2026-05-25  Add Italian Debrid translations
 48. `e68547f86`  2026-05-26  Update strings.xml
 49. `5edeec650`  2026-05-27  Support for subtitles from addons for external players
 50. `14eddbc0a`  2026-05-27  Add Polish Translation
 51. `29e2ca9d1`  2026-05-27  Fix latest translations
 52. `a24c38b4c`  2026-05-27  feat: DV7 Profile 7 to 8.1 conversion and custom playback buffer engine
 53. `1c7ce1252`  2026-05-29  feat(cloud): preserving orginal order as a sort option
 54. `f5e0175fc`  2026-05-29  Update audio&video translation
 55. `9404a8bca`  2026-05-29  Missing Polish translations
 56. `ef545d6fc`  2026-05-28  i18n(fr): extract Debrid stream filters + subtitle unknown (batch7)
 57. `9719fa8ed`  2026-05-30  Add option to strip DV from hybrid HDR10+ files
 58. `a02402e43`  2026-05-31  Updated Russian translation
 59. `75c9cd78a`  2026-05-31  Update strings.xml
 60. `39dda222b`  2026-06-01  i18n(fr): extract Buffer & Network playback settings (batch8)
 61. `1e2cac3ef`  2026-06-01  feat: watched items sync mutex protection
 62. `f902f2cde`  2026-06-01  fix(ru): remove extra translations not in default locale
 63. `67c5981af`  2026-06-02  feat: global stream badge
 64. `1ddcd631b`  2026-06-01  Missing Polish Translations
 65. `cf211af15`  2026-06-02  Rest of the missing Polish translations
 66. `e01388402`  2026-06-02  Strip DV from all streams
 67. `d94c60f4b`  2026-06-02  Update Spanish (LatAm) localization strings
 68. `dbbda64ad`  2026-06-02  i18n(fr): extract ExternalPlayback notification + Debrid/StreamBadge web pages + Fusion badges (batch8 extended)
 69. `919f2019b`  2026-06-02  Complete Greek localization parity
 70. `0e51e1106`  2026-06-02  Address Copilot review feedback
 71. `f64332d7b`  2026-06-03  Added missing Strings +1100 strings
 72. `533eb3c6c`  2026-06-03  Update HDR10 base layer description to remove reference to stripping DV and more accurately describe the functionality (it does not strip DV, it only attempts to ignore DV metadata)
 73. `4592238de`  2026-06-04  Proofreading
 74. `c94cbd8f3`  2026-06-04  Update Hungarian translation add missing strings
 75. `d84e2f6aa`  2026-06-04  Add auto-play next episode for external players
 76. `68de4684a`  2026-06-05  Add missing Polish Translations
 77. `f3175dee7`  2026-06-05  Fix issues in translations
 78. `ae346d4c9`  2026-06-05  Move Strip DV to Dolby Vision Handling option
 79. `8054972d6`  2026-06-05  feat(i18n): add missing Turkish translations
 80. `b25a7e644`  2026-06-05  Add Traditional Chinese Support
 81. `33531a61d`  2026-06-06  Update Polish Translation
 82. `ded4b2a3b`  2026-06-07  feat(badges): change pos - top,bottom

---

## B. Clean engine / bug fixes — auto-safe (94 commits)

Player, subtitles, debrid, collections, framerate, dialogs, packaging.
No overlap with our heavily-modified files.

> ⚠️ **WebView-subtitle round-trip:** `2afe26970` adds WebView HW subtitle
> rendering, then `c40d6437b` + `72c23096f` revert it to Canvas. Take the
> **net final state**, not the round-trip, if picking individually.

  1. `5ab727ef9`  2026-05-18  Add support of progress tracking to external players
  2. `9bc10dde8`  2026-05-18  Wait for the results when launching from the player menu
  3. `e1b161482`  2026-05-18  Let amplification try active player output
  4. `50f9ce683`  2026-05-19  Disable Forced Flag in exo
  5. `9638b2fa0`  2026-05-18  fix(player): resolve direct-debrid streams before playback handoff
  6. `5847e6c9f`  2026-05-18  fix(player): scope debrid resolves with debridResolveJob and loading state
  7. `c7d37d68e`  2026-05-19  Add ReuseBingeGroup to the "Play manually" rules
  8. `b43804fb8`  2026-05-19  Don't fallback to original title if there is no translation
  9. `0ac3fc2b9`  2026-05-19  Fix Paretnail Guide setting race condition
 10. `1cb25870e`  2026-05-19  ref: player soruce panel to respect pre-resolving of debrid streams
 11. `ae70d6c5d`  2026-05-19  Improve stream auto selection logic
 12. `75db72974`  2026-05-20  fix(data): make player settings migrations profile-aware to prevent ExoPlayer initialization failures on app update
 13. `2c8f60a99`  2026-05-20  fix(player): resolve settings migration timing race condition on cold start
 14. `85d3a4c43`  2026-05-20  style(player): remove unused flow and emitAll imports from PlayerSettingsDataStore
 15. `748129b82`  2026-05-21  Don't block player initianization with EpisodeRemapper
 16. `ca1c7c42b`  2026-05-21  fix(player): gate retry budget reset behind stable playback
 17. `89cc46c02`  2026-05-21  Break Next Episode stream search early if binge group is already found
 18. `f058032eb`  2026-05-23  ref: adjust formatting
 19. `2282de2c6`  2026-05-23  update CONTRIBUTING.md - Halt PR Notice
 20. `93cf972a2`  2026-05-23  ref(cloud): adding observer for lirabry changes
 21. `67600f7a8`  2026-05-23  fix: persist binge group on initial playback start
 22. `97bbbfefa`  2026-05-23  fix: defer addon subtitle restore until addons are loaded
 23. `f808a3974`  2026-05-23  bump version
 24. `9eee33614`  2026-05-23  fix(player): resolve 24fps judder and duplicate AFR preflight execution
 25. `8cb90d123`  2026-05-23  fix: allow binge group match in MANUAL mode
 26. `9efccedb7`  2026-05-23  fix: avoid duplicate keys in episode ratings list
 27. `257ad6d04`  2026-05-24  fix(ui): resolve unclear toggle selection and improve D-pad focus on addons screen
 28. `da9d8d015`  2026-05-24  Fix subtitle addons - filename was not urlencoded
 29. `70d4fb76b`  2026-05-24  Ignore externalUrl sources for auto selection and wait for CACHED to be resolved before throwing an timeout
 30. `075517c11`  2026-05-24  Fix first dialog click after CEC long press
 31. `4418344f0`  2026-05-24  fix(ui): resolve RTL text alignment and vertical overflow in P2pConsentDialog
 32. `c4a668018`  2026-05-24  fix(player): enable auto frame rate matching for m3u8 streams
 33. `07c54542b`  2026-05-24  fix(anime-skip): update AniSkip query parameter to types[]
 34. `40834a3bb`  2026-05-24  fix(updater): use correct shape for focused-disabled border on download button
 35. `457c3ca79`  2026-05-24  fix(stream): use AtomicInteger for concurrent completedJobs count
 36. `755dbf63f`  2026-05-25  update baseline prof and bump version
 37. `473db62cb`  2026-05-25  Fix Android TV Debrid stream presentation
 38. `94597d8de`  2026-05-27  Beter overlay for External Player
 39. `412908d4d`  2026-05-27  Handle Reuse Last link as well with overlay
 40. `09fce0cac`  2026-05-27  Fix Addon Subtitles for Torbox Instant
 41. `6e9bf21f3`  2026-05-27  Add SPL as Latino Espanol
 42. `5017a1596`  2026-05-28  fix(stream): background fetch cancellation and resume on StreamScreen
 43. `277e876f0`  2026-05-28  fix(tv): use event-driven state machine to suppress select key leakage in NuvioDialog without delays
 44. `dd3be7376`  2026-05-28  Remember if selected by user subtitle track was forced
 45. `524442084`  2026-05-28  Enable signal-only DV5 codec rewrite to DV5 toggle for MKV on certain devices
 46. `9d1147c27`  2026-05-28  fix: DV5 green/pink color fix for MKV playback
 47. `89034bb35`  2026-05-29  Don't let non-force subtitles be selected
 48. `7d8094365`  2026-05-29  ref: memoize streambadge
 49. `1a1b87ea8`  2026-05-29  ref: gate remaining strings to ShowLoadingStatus
 50. `2f353f312`  2026-05-29  fix: clear debrid resolving overlay when internal player launches
 51. `b5a521186`  2026-05-29  ref(cloud): support for original stream format
 52. `eb8e8390b`  2026-05-30  fix: removal of maxlines in streamcard
 53. `f8190bd9b`  2026-05-30  fix: prevent card clipping on bigger stream cards
 54. `a45b80cca`  2026-05-30  ref: move badges regex calculation off main thread
 55. `1cd9131e0`  2026-05-30  Bump version
 56. `38cb8b8a9`  2026-05-30  cleanup
 57. `290feb736`  2026-05-30  Strip DV from hybrid HDR10+ files after conversion
 58. `c477fda24`  2026-05-31  Fix subtitles for non imdb ids after DV7 PR
 59. `a757e9266`  2026-05-31  Fix libass after DV7 merge
 60. `3ab690f7b`  2026-05-31  Remove max lines from StreamComponents
 61. `9d78bed1c`  2026-05-31  bump version
 62. `35752cfc4`  2026-05-31  Add missing skip types (from aniskip and anime-skip)
 63. `c8e7b5133`  2026-05-31  Show ConfirmNextEpisode overlay when binge group for next episode is turned on
 64. `dd8730ab7`  2026-05-31  Fix local plugins for Kitsu anime streams
 65. `601dba3f1`  2026-05-31  Updates to strip DV from hybrid HDR10+ files
 66. `7f6217b8c`  2026-05-31  Added fix
 67. `c824f2e36`  2026-06-01  feat: adding user agent
 68. `f0e78bdf3`  2026-05-31  fix(collections): Extend vote_count.desc option to person/director builders
 69. `723cb28b6`  2026-05-31  fix(collections): extend Most Voted sorting to list, collection, and discover
 70. `a3aac6c06`  2026-06-01  fix(player): restore background codec-crash recovery deferral
 71. `2afe26970`  2026-06-01  perf(player): use webview for hardware-accelerated subtitle rendering
 72. `579a7945f`  2026-06-01  Fix stale next up displayed on start of the next episode
 73. `49182691a`  2026-06-02  Improvements for external players
 74. `c40d6437b`  2026-06-02  Revert "perf(player): use webview for hardware-accelerated subtitle rendering"
 75. `5bf370199`  2026-06-02  Store language in Stream Cache
 76. `4a72f3ab8`  2026-06-02  Save also year
 77. `974048ef4`  2026-06-02  fix: enable legacy packaging so bundling won't strip torrent libs
 78. `aa7c32353`  2026-06-02  bump version
 79. `b507e4a16`  2026-06-02  Strip DV from all files
 80. `153769c2d`  2026-06-03  Remove non DV related track change
 81. `dc9e83187`  2026-06-03  Remove redundant "Strip HDR 10+ logic" code - added in previous commit
 82. `7c4fc5b2d`  2026-06-03  fix(player): improve adaptive stream quality selection
 83. `f1b134727`  2026-06-03  Revert additional conversion change
 84. `ac972a823`  2026-06-03  Smoother badges and addon logo
 85. `8528548a9`  2026-06-03  fix(player): resolve WebView subtitle shifting and alignment issues
 86. `722078016`  2026-06-03  fix: correct WebView subtitle centering bug (position anchor TYPE_UNSET to ANCHOR_TYPE_MIDDLE)
 87. `5a01c3c27`  2026-06-04  Update Hungarian translation add missing strings - fix
 88. `a36f95a3c`  2026-06-05  Get rid of CW->Stream flash
 89. `4be39cc20`  2026-06-05  fix: trust Let's Encrypt roots on legacy Android devices
 90. `f3c7793c7`  2026-06-05  Fix certificate filename
 91. `362175add`  2026-06-05  Update missed STRIP_DV reference
 92. `9191f63c2`  2026-06-06  Pause Video when changing to the next episode
 93. `6f94fda9b`  2026-06-06  fix(player): change WebView subtitle layer type to LAYER_TYPE_NONE to prevent MediaTek Gralloc crash
 94. `72c23096f`  2026-06-07  fix(player): revert WebView subtitles and restore Canvas rendering

---

## C. Sync / progress / Trakt — FLAGGED for manual review (18 commits)

Pass the file filter but touch the progress/scrobble pipeline. Listed in
order. **Do not batch — read each diff first.**

### C1 — Needs careful read (logic: scrobble, CW-write, cross-profile, delta-sync)

  1. `136963795`  2026-05-19  Always sync to Nuvio Sync
  2. `e8a3868ea`  2026-05-19  pin profile ID in watch progress sync to prevent cross-profile CW leak
  3. `f77c277f2`  2026-05-20  feat: sync services with additional parameters
  4. `c90f9a131`  2026-05-21  Move library pull to a job, and check if user selected nuvio as library
  5. `997cfacda`  2026-05-24  fix trakt scrobble regression
  6. `1860c8b16`  2026-05-31  Fix trakt scrobbling
  7. `2e7545723`  2026-06-01  ref: stop pushing watch progress every buffer
  8. `4eb015a8b`  2026-06-01  ref: remove periodic refresh
  9. `2ab038bf0`  2026-06-01  feat: watched_items delta syncing
 10. `386da1d93`  2026-06-01  Add TV delta sync for progress
 11. `9dc0d39de`  2026-06-02  fix(player): resolve Trakt resume position on external players (simple fix)
 12. `01b98f058`  2026-06-06  Don't send only a title to trakt. Also, leave non trakt items in local CW

### C2 — Lower concern (interval/config tweaks, still touch sync)

  1. `5ec50a8bc`  2026-05-23  ref: increase periodic pull interval
  2. `19667f7a9`  2026-05-25  Sync Debrid settings on Android TV
  3. `e24a04ea7`  2026-05-30  fix: update sync periodic interval to 30 minutes
  4. `9a711191d`  2026-05-31  ref: adjust onResume sync interval
  5. `d4d6bc2c0`  2026-05-31  ref: increase Nuvio sync periodic interval from 5 minutes to 15 minutes
  6. `08a7d98b0`  2026-06-02  ref: exlcude audio/video advanced settings from sync

---

## Phase 1 totals

| Bucket | Count | Action |
|---|---|---|
| A. i18n / strings | 82 | Batch-pick, then refresh lint baseline |
| B. Clean engine / bug | 94 | Batch-pick in order |
| C. Sync / progress / Trakt | 18 | Review each, then pick |
| **Phase 1 total** | **194** | |

Excluded (later phases): 12 settings/UI · 26 visual · 5 engine-high-risk.
