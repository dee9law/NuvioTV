# Phase 1 Upstream Pick-List — Auto-Safe Batch (CORRECTED v2)

> **Report/planning artifact only — NO cherry-picks performed.**
> Regenerated 2026-06-08. Source range: `0.6.18-beta..0.7.4-beta`
> (our last sync → current upstream latest `0.7.4-beta`), 237 non-merge commits.

## ⚠️ v2 correction notice
The v1 list was built with a classifier that used `grep -q`/`grep -qv`, which
returns the **wrong exit status in this shell** — it mislabeled 31 mixed
code+strings commits as pure i18n (including changes to `MainActivity.kt`, the
CW pipeline, and DV7 native libs). v2 re-derives every bucket with an explicit
no-`-q` capture method and a case-insensitive locale pattern that also accepts
upstream's `string.xml` / `Strings.xml` typo files. **Every Section-A commit is
re-verified to touch ONLY `res/values*/[strings|arrays|plurals].xml` (0 offenders).**

## How to use
- **Order = top-to-bottom (oldest → newest).** Cherry-pick in this order.
- Phase 1 auto-safe = **Section A (i18n) + Section B (clean engine)**.
- **Section C** (sync/progress/Trakt) passes the file filter but touches the
  protected progress/scrobble area — **review each diff before taking**.
- **Excluded from Phase 1** (later phases): 6 engine-HIGH-RISK, 29 VISUAL,
  30 SETTINGS/UI. Summarized at the bottom; not pick-listed here.
- ⚠️ After the i18n batch: re-run `./gradlew updateLintBaseline` + commit
  `lint-baseline.xml` (default-only strings fail `lintVitalFullDebug`).

---

## A. i18n / strings — auto-safe, VERIFIED pure (53 commits)

Every commit touches locale string resources only. Batch-pickable.

  1. `94c26872d`  2026-05-18  update Indonesian locale
  2. `8c4ef1a5d`  2026-05-18  changed "Who's watching?" translation
  3. `142749b60`  2026-05-18  added translations
  4. `c00de61b3`  2026-05-18  translated serval strings to german
  5. `8ccfe0484`  2026-05-18  fix(i18n): translate Debrid integration and FFmpeg downmix settings to French
  6. `fd3189d36`  2026-05-18  fix(i18n): shorten French donate button so it is not truncated
  7. `03fb63732`  2026-05-18  Latin American Spanish (es-419) Translation Update
  8. `7183945b9`  2026-05-19  add 83 missing Indonesian translation
  9. `0f17247ee`  2026-05-19  Fixes for translations errors
 10. `290ea6426`  2026-05-19  Add Italian translations for various strings
 11. `5d0e8b2c8`  2026-05-19  Update strings.xml
 12. `e61da0ea8`  2026-05-19  Fix broken > in pt-BR translation
 13. `463718c69`  2026-05-20  update Indonesian localization
 14. `af1143333`  2026-05-20  fix(i18n): translate 3 missing French strings (binge group reuse, debrid warning)
 15. `9a2bbf96a`  2026-05-20  Normalize Arabic resource filename for Android
 16. `b40eb2434`  2026-05-20  Update strings.xml
 17. `487c92a37`  2026-05-20  i18n(el): add 416 missing Greek string translations
 18. `1fe536e05`  2026-05-20  fix(i18n): replace awkward French 'par récence' with natural phrasing
 19. `4a9f0acbb`  2026-05-20  fix(i18n): fix three awkward/incorrect French translations
 20. `f73e68f42`  2026-05-21  Update string resources for consistency in casing
 21. `ddc883576`  2026-05-21  Title: Update Hungarian translation add missing strings
 22. `de583c765`  2026-05-22  airs in translation
 23. `8c619a96e`  2026-05-23  update Indonesian localization to follow recently added features
 24. `f8c2ec240`  2026-05-23  update Indonesian translation in accordance to the v0.6.20 update
 25. `48b589e9e`  2026-05-23  fix(i18n): polish Greek terminology
 26. `fce899c94`  2026-05-23  fix(i18n): complete Greek locale parity
 27. `3a0f2813c`  2026-05-23  i18n: synchronize Turkish translations and add missing keys
 28. `fa806d9a7`  2026-05-24  Update Polish translations
 29. `5214cdf4e`  2026-05-24  fix(i18n): remove orphaned Indonesian string that breaks lint
 30. `53b96fc78`  2026-05-25  feat(i18n): add 34 missing Indonesian translation
 31. `00c9d1216`  2026-05-25  Add a blank line to xml.
 32. `28e5f6a79`  2026-05-25  Update strings.xml
 33. `7d1a3516b`  2026-05-25  Update Spanish (LatAm) localization strings
 34. `58ebfee3f`  2026-05-25  Add Italian Debrid translations
 35. `e68547f86`  2026-05-26  Update strings.xml
 36. `14eddbc0a`  2026-05-27  Add Polish Translation
 37. `f5e0175fc`  2026-05-29  Update audio&video translation
 38. `9404a8bca`  2026-05-29  Missing Polish translations
 39. `a02402e43`  2026-05-31  Updated Russian translation
 40. `75c9cd78a`  2026-05-31  Update strings.xml
 41. `f902f2cde`  2026-06-01  fix(ru): remove extra translations not in default locale
 42. `1ddcd631b`  2026-06-01  Missing Polish Translations
 43. `cf211af15`  2026-06-02  Rest of the missing Polish translations
 44. `d94c60f4b`  2026-06-02  Update Spanish (LatAm) localization strings
 45. `0e51e1106`  2026-06-02  Address Copilot review feedback
 46. `f64332d7b`  2026-06-03  Added missing Strings +1100 strings
 47. `533eb3c6c`  2026-06-03  Update HDR10 base layer description to remove reference to stripping DV and more accurately describe the functionality (it does not strip DV, it only attempts to ignore DV metadata)
 48. `4592238de`  2026-06-04  Proofreading
 49. `c94cbd8f3`  2026-06-04  Update Hungarian translation add missing strings
 50. `68de4684a`  2026-06-05  Add missing Polish Translations
 51. `f3175dee7`  2026-06-05  Fix issues in translations
 52. `8054972d6`  2026-06-05  feat(i18n): add missing Turkish translations
 53. `33531a61d`  2026-06-06  Update Polish Translation

---

## B. Clean engine / bug fixes — auto-safe (100 commits)

Player, subtitles, debrid, collections, framerate, packaging, dialogs.
No overlap with heavily-modified files; no sync/progress logic.

> ⚠️ **WebView-subtitle round-trip:** `2afe26970` adds WebView HW subtitle
> rendering, then `c40d6437b`+`72c23096f` revert to Canvas. Take net final
> state, not the round-trip, if picking individually.

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
 18. `e8892fadb`  2026-05-23  feat: remove deprecated UI components
 19. `f058032eb`  2026-05-23  ref: adjust formatting
 20. `2282de2c6`  2026-05-23  update CONTRIBUTING.md - Halt PR Notice
 21. `4ca5fe17c`  2026-05-23  ref(cloud): card and emty state layout changes
 22. `93cf972a2`  2026-05-23  ref(cloud): adding observer for lirabry changes
 23. `67600f7a8`  2026-05-23  fix: persist binge group on initial playback start
 24. `97bbbfefa`  2026-05-23  fix: defer addon subtitle restore until addons are loaded
 25. `f808a3974`  2026-05-23  bump version
 26. `9eee33614`  2026-05-23  fix(player): resolve 24fps judder and duplicate AFR preflight execution
 27. `8cb90d123`  2026-05-23  fix: allow binge group match in MANUAL mode
 28. `9efccedb7`  2026-05-23  fix: avoid duplicate keys in episode ratings list
 29. `257ad6d04`  2026-05-24  fix(ui): resolve unclear toggle selection and improve D-pad focus on addons screen
 30. `da9d8d015`  2026-05-24  Fix subtitle addons - filename was not urlencoded
 31. `70d4fb76b`  2026-05-24  Ignore externalUrl sources for auto selection and wait for CACHED to be resolved before throwing an timeout
 32. `075517c11`  2026-05-24  Fix first dialog click after CEC long press
 33. `4418344f0`  2026-05-24  fix(ui): resolve RTL text alignment and vertical overflow in P2pConsentDialog
 34. `c4a668018`  2026-05-24  fix(player): enable auto frame rate matching for m3u8 streams
 35. `07c54542b`  2026-05-24  fix(anime-skip): update AniSkip query parameter to types[]
 36. `40834a3bb`  2026-05-24  fix(updater): use correct shape for focused-disabled border on download button
 37. `457c3ca79`  2026-05-24  fix(stream): use AtomicInteger for concurrent completedJobs count
 38. `07508aa6f`  2026-05-24  Added Simplified Chinese Locale support.
 39. `5e25788bc`  2026-05-25  i18n(fr): extract 7 remaining hardcoded strings + refactor safeApiCall with Context
 40. `755dbf63f`  2026-05-25  update baseline prof and bump version
 41. `473db62cb`  2026-05-25  Fix Android TV Debrid stream presentation
 42. `94597d8de`  2026-05-27  Beter overlay for External Player
 43. `412908d4d`  2026-05-27  Handle Reuse Last link as well with overlay
 44. `09fce0cac`  2026-05-27  Fix Addon Subtitles for Torbox Instant
 45. `6e9bf21f3`  2026-05-27  Add SPL as Latino Espanol
 46. `5017a1596`  2026-05-28  fix(stream): background fetch cancellation and resume on StreamScreen
 47. `277e876f0`  2026-05-28  fix(tv): use event-driven state machine to suppress select key leakage in NuvioDialog without delays
 48. `dd3be7376`  2026-05-28  Remember if selected by user subtitle track was forced
 49. `524442084`  2026-05-28  Enable signal-only DV5 codec rewrite to DV5 toggle for MKV on certain devices
 50. `9d1147c27`  2026-05-28  fix: DV5 green/pink color fix for MKV playback
 51. `89034bb35`  2026-05-29  Don't let non-force subtitles be selected
 52. `7d8094365`  2026-05-29  ref: memoize streambadge
 53. `1a1b87ea8`  2026-05-29  ref: gate remaining strings to ShowLoadingStatus
 54. `2f353f312`  2026-05-29  fix: clear debrid resolving overlay when internal player launches
 55. `b5a521186`  2026-05-29  ref(cloud): support for original stream format
 56. `eb8e8390b`  2026-05-30  fix: removal of maxlines in streamcard
 57. `f8190bd9b`  2026-05-30  fix: prevent card clipping on bigger stream cards
 58. `a45b80cca`  2026-05-30  ref: move badges regex calculation off main thread
 59. `1cd9131e0`  2026-05-30  Bump version
 60. `38cb8b8a9`  2026-05-30  cleanup
 61. `290feb736`  2026-05-30  Strip DV from hybrid HDR10+ files after conversion
 62. `c477fda24`  2026-05-31  Fix subtitles for non imdb ids after DV7 PR
 63. `a757e9266`  2026-05-31  Fix libass after DV7 merge
 64. `3ab690f7b`  2026-05-31  Remove max lines from StreamComponents
 65. `9d78bed1c`  2026-05-31  bump version
 66. `35752cfc4`  2026-05-31  Add missing skip types (from aniskip and anime-skip)
 67. `c8e7b5133`  2026-05-31  Show ConfirmNextEpisode overlay when binge group for next episode is turned on
 68. `dd8730ab7`  2026-05-31  Fix local plugins for Kitsu anime streams
 69. `601dba3f1`  2026-05-31  Updates to strip DV from hybrid HDR10+ files
 70. `7f6217b8c`  2026-05-31  Added fix
 71. `c824f2e36`  2026-06-01  feat: adding user agent
 72. `f0e78bdf3`  2026-05-31  fix(collections): Extend vote_count.desc option to person/director builders
 73. `723cb28b6`  2026-05-31  fix(collections): extend Most Voted sorting to list, collection, and discover
 74. `a3aac6c06`  2026-06-01  fix(player): restore background codec-crash recovery deferral
 75. `2afe26970`  2026-06-01  perf(player): use webview for hardware-accelerated subtitle rendering
 76. `579a7945f`  2026-06-01  Fix stale next up displayed on start of the next episode
 77. `49182691a`  2026-06-02  Improvements for external players
 78. `c40d6437b`  2026-06-02  Revert "perf(player): use webview for hardware-accelerated subtitle rendering"
 79. `5bf370199`  2026-06-02  Store language in Stream Cache
 80. `4a72f3ab8`  2026-06-02  Save also year
 81. `974048ef4`  2026-06-02  fix: enable legacy packaging so bundling won't strip torrent libs
 82. `aa7c32353`  2026-06-02  bump version
 83. `b507e4a16`  2026-06-02  Strip DV from all files
 84. `dbbda64ad`  2026-06-02  i18n(fr): extract ExternalPlayback notification + Debrid/StreamBadge web pages + Fusion badges (batch8 extended)
 85. `153769c2d`  2026-06-03  Remove non DV related track change
 86. `dc9e83187`  2026-06-03  Remove redundant "Strip HDR 10+ logic" code - added in previous commit
 87. `7c4fc5b2d`  2026-06-03  fix(player): improve adaptive stream quality selection
 88. `f1b134727`  2026-06-03  Revert additional conversion change
 89. `ac972a823`  2026-06-03  Smoother badges and addon logo
 90. `919f2019b`  2026-06-02  Complete Greek localization parity
 91. `8528548a9`  2026-06-03  fix(player): resolve WebView subtitle shifting and alignment issues
 92. `722078016`  2026-06-03  fix: correct WebView subtitle centering bug (position anchor TYPE_UNSET to ANCHOR_TYPE_MIDDLE)
 93. `5a01c3c27`  2026-06-04  Update Hungarian translation add missing strings - fix
 94. `a36f95a3c`  2026-06-05  Get rid of CW->Stream flash
 95. `4be39cc20`  2026-06-05  fix: trust Let's Encrypt roots on legacy Android devices
 96. `f3c7793c7`  2026-06-05  Fix certificate filename
 97. `362175add`  2026-06-05  Update missed STRIP_DV reference
 98. `9191f63c2`  2026-06-06  Pause Video when changing to the next episode
 99. `6f94fda9b`  2026-06-06  fix(player): change WebView subtitle layer type to LAYER_TYPE_NONE to prevent MediaTek Gralloc crash
100. `72c23096f`  2026-06-07  fix(player): revert WebView subtitles and restore Canvas rendering

---

## C. Sync / progress / Trakt — FLAGGED for manual review (19 commits)

Pass the file filter but touch the progress/scrobble pipeline. Read each
diff first — **do not batch**.

### C1 — Needs careful read (scrobble / CW-write / cross-profile / delta-sync)

  1. `136963795`  2026-05-19  Always sync to Nuvio Sync
  2. `e8a3868ea`  2026-05-19  pin profile ID in watch progress sync to prevent cross-profile CW leak
  3. `f77c277f2`  2026-05-20  feat: sync services with additional parameters
  4. `c90f9a131`  2026-05-21  Move library pull to a job, and check if user selected nuvio as library
  5. `997cfacda`  2026-05-24  fix trakt scrobble regression
  6. `1860c8b16`  2026-05-31  Fix trakt scrobbling
  7. `2e7545723`  2026-06-01  ref: stop pushing watch progress every buffer
  8. `2ab038bf0`  2026-06-01  feat: watched_items delta syncing
  9. `1e2cac3ef`  2026-06-01  feat: watched items sync mutex protection
 10. `386da1d93`  2026-06-01  Add TV delta sync for progress
 11. `08a7d98b0`  2026-06-02  ref: exlcude audio/video advanced settings from sync
 12. `9dc0d39de`  2026-06-02  fix(player): resolve Trakt resume position on external players (simple fix)
 13. `01b98f058`  2026-06-06  Don't send only a title to trakt. Also, leave non trakt items in local CW

### C2 — Lower concern (interval/config sync tweaks)

  1. `5ec50a8bc`  2026-05-23  ref: increase periodic pull interval
  2. `19667f7a9`  2026-05-25  Sync Debrid settings on Android TV
  3. `e24a04ea7`  2026-05-30  fix: update sync periodic interval to 30 minutes
  4. `9a711191d`  2026-05-31  ref: adjust onResume sync interval
  5. `d4d6bc2c0`  2026-05-31  ref: increase Nuvio sync periodic interval from 5 minutes to 15 minutes
  6. `4eb015a8b`  2026-06-01  ref: remove periodic refresh

---

## Phase 1 totals

| Section | Count | Action |
|---|---|---|
| A. i18n / strings (verified) | 53 | Batch-pick, then refresh lint baseline |
| B. Clean engine / bug | 100 | Batch-pick in order |
| C. Sync / progress / Trakt | 19 | Review each, then pick |
| **Phase 1 in scope** | **172** | |

## Excluded from Phase 1 (later phases — not pick-listed here)

| Bucket | Count |
|---|---|
| ENGINE high-risk (touch our heavy files) | 6 |
| VISUAL (Modern/Classic/Spotlight/Feel/TopBar/SideRail) | 29 |
| SETTINGS / UI (placement decisions) | 30 |
| **Excluded total** | **65** |

Grand total: 237 / 237 ✓
