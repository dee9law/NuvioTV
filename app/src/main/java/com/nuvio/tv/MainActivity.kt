package com.nuvio.tv

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import android.util.Log
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import coil3.request.crossfade
import coil3.request.transformations
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.core.runtime.PluginRuntimeHooks
import com.nuvio.tv.data.local.AppOnboardingDataStore
import com.nuvio.tv.data.local.ExperienceModeDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.ThemeDataStore
import com.nuvio.tv.data.repository.TraktProgressService
import com.nuvio.tv.domain.model.AppFont
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.CategoryPill
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.Feel
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.core.sync.ProfileSettingsSyncService
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.core.sync.StartupSyncService
import com.nuvio.tv.data.remote.supabase.AvatarRepository
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import com.nuvio.tv.ui.components.CategoryPillsViewModel
import com.nuvio.tv.ui.components.CollectionsDropdown
import com.nuvio.tv.ui.components.FolderPillsDropdown
import com.nuvio.tv.ui.components.NuvioScrollDefaults
import com.nuvio.tv.ui.components.ProfileOverlay
import com.nuvio.tv.ui.components.ProfileOverlayDestination
import com.nuvio.tv.ui.components.ProfileOverlayHiddenItem
import com.nuvio.tv.ui.components.SideRail
import com.nuvio.tv.ui.components.TopNavigationBar
import com.nuvio.tv.ui.screens.home.ChannelRailViewModel
import com.nuvio.tv.ui.screens.account.AuthQrSignInScreen
import com.nuvio.tv.ui.screens.addon.EssentialAddonSetupScreen
import com.nuvio.tv.ui.screens.profile.ProfileSelectionScreen
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.LocalFastHorizontalNavigationEnabled
import com.nuvio.tv.ui.util.LocalRecompositionHighlighterEnabled
import com.nuvio.tv.updater.UpdateViewModel
import com.nuvio.tv.updater.ui.UpdatePromptDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf

val LocalContentFocusRequester = compositionLocalOf { FocusRequester.Default }
val LocalNavBarFocusRequester  = compositionLocalOf { FocusRequester.Default }
val LocalSideRailController   = compositionLocalOf<(() -> Unit)?> { null }
/**
 * What Back should do when a home-content layout (Classic / Modern / Grid /
 * Spotlight) reaches the TOP of its internal Back hierarchy — the step that
 * normally escapes upward to the TopBar via [LocalNavBarFocusRequester].
 *
 * On the main screens this is null and the TopBar escape runs as always.
 * Screens that embed the layouts WITHOUT a TopBar (FolderDetail's
 * follow-layout mode) provide their route-pop here — otherwise the terminal
 * `navBarFr.requestFocus()` silently no-ops on a bar that isn't composed and
 * Back is swallowed forever (the "Back trap").
 */
val LocalContentBackFallback = compositionLocalOf<(() -> Unit)?> { null }
/**
 * `true` when the current screen sits inside the Feel.MODERN navigation
 * shell — i.e. there is no SideRail consuming left-edge space. Content
 * composables read this to drop the left buffer that otherwise reserves
 * SideRail clearance, going truly edge-to-edge.
 *
 * Default is `false` so any code path that forgets to provide the value
 * keeps the existing (Legacy-safe) padding. MainActivity provides the
 * real value once the active Feel resolves.
 */
val LocalIsModernFeel = compositionLocalOf { false }

/**
 * Top inset (in dp) that hero text/info Columns should apply so their
 * content doesn't slip behind the TopBar overlay. Non-zero only when
 * the glassmorphism Modern Top Bar is enabled AND a top nav is showing
 * — the hero's *image* still runs to y=0 in that mode, but text needs
 * to start below the bar. Default 0.dp keeps every other layout
 * untouched.
 */
val LocalTopBarOverlayHeight: androidx.compose.runtime.ProvidableCompositionLocal<androidx.compose.ui.unit.Dp> =
    compositionLocalOf { 0.dp }

/**
 * Whether focused cards (and channel pill logos) should render the
 * soft coloured-shadow halo. Orthogonal to [LocalCardFocusStyle] —
 * Poster Glow works with either Accent or Bloom border styles.
 */
val LocalPosterGlowEnabled = compositionLocalOf { true }

/**
 * Card focus style the user has selected — `ACCENT` is the safe
 * static-border default. Content cards and channel pills with logos
 * read this and switch between Accent / Poster Glow / Border Bloom
 * focus treatments accordingly.
 */
val LocalCardFocusStyle = compositionLocalOf {
    com.nuvio.tv.domain.model.CardFocusStyle.ACCENT
}

private data class MainUiPrefs(
    val theme: AppTheme = AppTheme.WHITE,
    val font: AppFont = AppFont.INTER,
    val amoledMode: Boolean = false,
    val amoledSurfacesMode: Boolean = false,
    val hasChosenLayout: Boolean? = null,
    val experienceMode: ExperienceMode? = null,
    val experienceModeLoaded: Boolean = false,
    val addonSetupSkipped: Boolean = false,
    val smoothBringIntoViewEnabled: Boolean = true,
    val fastHorizontalNavigationEnabled: Boolean = false,
    val composeHighlighterEnabled: Boolean = false,
    val navigationFeel: Feel = Feel.MODERN,
    val topBarEnabled: Boolean = true,
    val modernTopBarEnabled: Boolean = false,
    val posterGlowEnabled: Boolean = true,
    val cardFocusStyle: com.nuvio.tv.domain.model.CardFocusStyle =
        com.nuvio.tv.domain.model.CardFocusStyle.ACCENT,
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeDataStore: ThemeDataStore

    @Inject
    lateinit var layoutPreferenceDataStore: LayoutPreferenceDataStore

    @Inject
    lateinit var experienceModeDataStore: ExperienceModeDataStore

    @Inject
    lateinit var addonRepository: AddonRepository

    @Inject
    lateinit var traktProgressService: TraktProgressService

    @Inject
    lateinit var startupSyncService: StartupSyncService

    @Inject
    lateinit var profileSettingsSyncService: ProfileSettingsSyncService

    @Inject
    lateinit var profileSyncService: ProfileSyncService

    @Inject
    lateinit var profileManager: ProfileManager

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var appOnboardingDataStore: AppOnboardingDataStore

    @Inject
    lateinit var avatarRepository: AvatarRepository

    @Inject
    lateinit var categoryPillOrderDataStore: com.nuvio.tv.data.local.CategoryPillOrderDataStore

    @Inject
    lateinit var traktAuthDataStore: com.nuvio.tv.data.local.TraktAuthDataStore

    @Inject
    lateinit var trailerPlayerPool: com.nuvio.tv.core.player.TrailerPlayerPool

    private lateinit var jankStats: JankStats

    /** True until the first onResume after onCreate completes. */
    private var isFirstResumeAfterCreate = false

    @OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun attachBaseContext(newBase: Context) {
        val tag = LocaleCache.localeTag.takeIf { it != LocaleCache.UNSET }

        if (!tag.isNullOrEmpty()) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            // Cache not ready yet (very early cold start) — use system locale
            // The IO coroutine in Application.onCreate will finish before any activity
            // is usually created, but if not, we just use system locale until next launch
            super.attachBaseContext(newBase)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        isFirstResumeAfterCreate = true
        window?.setBackgroundDrawable(null)

        PluginRuntimeHooks.onActivityCreate(this)

        window?.decorView?.post {
            val snapshot = com.nuvio.tv.core.player.DisplayCapabilities.detect(this)
            com.nuvio.tv.core.player.DisplayCapabilities.logSummary(snapshot)
        }

        // Extract extras set by the Continue Watching launcher channel preview programs.
        val launchContentId = intent?.getStringExtra("contentId")
        val launchContentType = intent?.getStringExtra("contentType")

        setContent {
            var hasSelectedProfileThisSession by rememberSaveable { mutableStateOf(false) }
            var onboardingCompletedThisSession by remember { mutableStateOf(false) }
            var onboardingProfileSyncInProgress by remember { mutableStateOf(false) }
            val hasSeenAuthQrFlow = remember(appOnboardingDataStore) {
                appOnboardingDataStore.hasSeenAuthQrOnFirstLaunch.map<Boolean, Boolean?> { it }
            }
            val hasSeenAuthQrOnFirstLaunch by hasSeenAuthQrFlow.collectAsState(initial = null)
            val authState by authManager.authState.collectAsState()

            LaunchedEffect(hasSeenAuthQrOnFirstLaunch, authState) {
                if (hasSeenAuthQrOnFirstLaunch == false && authState is AuthState.FullAccount) {
                    appOnboardingDataStore.setHasSeenAuthQrOnFirstLaunch(true)
                    onboardingCompletedThisSession = true
                }
            }

            val activeProfileId by profileManager.activeProfileId.collectAsState()
            val profiles by profileManager.profiles.collectAsState()
            val hasEverSelectedProfile by profileManager.hasEverSelectedProfile.collectAsState()
            val rememberLastProfileEnabled by profileManager.rememberLastProfileEnabled.collectAsState()
            val activeProfile = remember(activeProfileId, profiles) {
                profiles.firstOrNull { it.id == activeProfileId }
            }
            var profilePinStates by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }

            LaunchedEffect(authState, profiles) {
                if (authState is AuthState.FullAccount) {
                    profileSyncService.pullProfileLockStates()
                        .onSuccess { profilePinStates = it }
                        .onFailure { profilePinStates = emptyMap() }
                } else {
                    profilePinStates = emptyMap()
                }
            }

            val activeProfileHasPin = remember(activeProfileId, profilePinStates) {
                profilePinStates[activeProfileId] == true
            }

            LaunchedEffect(hasEverSelectedProfile, activeProfileHasPin, rememberLastProfileEnabled) {
                if (rememberLastProfileEnabled && hasEverSelectedProfile && !activeProfileHasPin && !hasSelectedProfileThisSession) {
                    hasSelectedProfileThisSession = true
                    if (authManager.authState.value is AuthState.FullAccount) {
                        startupSyncService.requestSyncNow()
                    }
                }
            }

            var avatarCatalog by remember { mutableStateOf(emptyList<com.nuvio.tv.data.remote.supabase.AvatarCatalogItem>()) }

            LaunchedEffect(Unit) {
                avatarCatalog = runCatching { avatarRepository.getAvatarCatalog() }
                    .getOrDefault(emptyList())
            }

            val activeProfileAvatarImageUrl = remember(activeProfile, avatarCatalog) {
                activeProfile?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: activeProfile?.avatarId?.let { avatarRepository.getAvatarImageUrl(it, avatarCatalog) }
            }

            val mainUiPrefsFlow = remember(themeDataStore, layoutPreferenceDataStore, experienceModeDataStore) {
                combine(
                    themeDataStore.selectedTheme,
                    themeDataStore.selectedFont,
                    layoutPreferenceDataStore.hasChosenLayout,
                ) { theme, font, hasChosenLayout ->
                    MainUiPrefs(
                        theme = theme,
                        font = font,
                        hasChosenLayout = hasChosenLayout,
                    )
                }.combine(experienceModeDataStore.mode) { prefs, experienceMode ->
                    prefs.copy(experienceMode = experienceMode, experienceModeLoaded = true)
                }.combine(experienceModeDataStore.addonSetupSkipped) { prefs, addonSetupSkipped ->
                    prefs.copy(addonSetupSkipped = addonSetupSkipped)
                }.combine(themeDataStore.amoledMode) { prefs, amoledMode ->
                    prefs.copy(amoledMode = amoledMode)
                }.combine(themeDataStore.amoledSurfacesMode) { prefs, amoledSurfacesMode ->
                    prefs.copy(amoledSurfacesMode = amoledSurfacesMode)
                }.combine(layoutPreferenceDataStore.smoothBringIntoViewEnabled) { prefs, smoothBringIntoViewEnabled ->
                    prefs.copy(smoothBringIntoViewEnabled = smoothBringIntoViewEnabled)
                }.combine(layoutPreferenceDataStore.fastHorizontalNavigationEnabled) { prefs, fastHorizontalNavigationEnabled ->
                    prefs.copy(fastHorizontalNavigationEnabled = fastHorizontalNavigationEnabled)
                }.combine(layoutPreferenceDataStore.composeHighlighterEnabled) { prefs, composeHighlighterEnabled ->
                    prefs.copy(composeHighlighterEnabled = composeHighlighterEnabled)
                }.combine(layoutPreferenceDataStore.navigationFeel) { prefs, feel ->
                    prefs.copy(navigationFeel = feel)
                }.combine(layoutPreferenceDataStore.topBarEnabled) { prefs, topBar ->
                    prefs.copy(topBarEnabled = topBar)
                }.combine(layoutPreferenceDataStore.modernTopBarEnabled) { prefs, modernTopBar ->
                    prefs.copy(modernTopBarEnabled = modernTopBar)
                }.combine(layoutPreferenceDataStore.posterGlowEnabled) { prefs, posterGlow ->
                    prefs.copy(posterGlowEnabled = posterGlow)
                }.combine(layoutPreferenceDataStore.cardFocusStyle) { prefs, style ->
                    prefs.copy(cardFocusStyle = style)
                }
            }
            val mainUiPrefs by mainUiPrefsFlow.collectAsState(initial = MainUiPrefs(hasChosenLayout = null))
            val installedAddons by remember(addonRepository) {
                addonRepository.getInstalledAddons()
            }.collectAsState(initial = null)

            NuvioTheme(
                appTheme = mainUiPrefs.theme,
                appFont = mainUiPrefs.font,
                amoledMode = mainUiPrefs.amoledMode,
                amoledSurfacesMode = mainUiPrefs.amoledSurfacesMode
            ) {
                val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
                val bringIntoViewSpec = if (mainUiPrefs.smoothBringIntoViewEnabled) {
                    NuvioScrollDefaults.smoothScrollSpec
                } else {
                    defaultBringIntoViewSpec
                }
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides bringIntoViewSpec,
                    LocalFastHorizontalNavigationEnabled provides mainUiPrefs.fastHorizontalNavigationEnabled,
                    LocalRecompositionHighlighterEnabled provides (BuildConfig.IS_DEBUG_BUILD && mainUiPrefs.composeHighlighterEnabled),
                    LocalPosterGlowEnabled provides mainUiPrefs.posterGlowEnabled,
                    LocalCardFocusStyle provides mainUiPrefs.cardFocusStyle,
                    com.nuvio.tv.core.player.LocalTrailerPlayerPool provides trailerPlayerPool
                ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(
                        containerColor = NuvioColors.Background
                    )
                ) {
                    if (hasSeenAuthQrOnFirstLaunch == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NuvioColors.Background)
                        )
                        return@Surface
                    }

                    if (
                        hasSeenAuthQrOnFirstLaunch == false &&
                        authState !is AuthState.FullAccount &&
                        !onboardingCompletedThisSession
                    ) {
                        AuthQrSignInScreen(
                            onBackPress = {},
                            onContinue = {
                                lifecycleScope.launch {
                                    val shouldRunRemoteOnboardingSync =
                                        authManager.authState.value is AuthState.FullAccount

                                    if (shouldRunRemoteOnboardingSync) {
                                        if (onboardingProfileSyncInProgress) return@launch
                                        onboardingProfileSyncInProgress = true
                                        val maxAttempts = 3
                                        var synced = false
                                        for (attempt in 0 until maxAttempts) {
                                            val result = profileSyncService.pullFromRemote()
                                            if (result.isSuccess) {
                                                synced = true
                                                break
                                            }
                                            if (attempt < maxAttempts - 1) {
                                                delay(1_000)
                                            }
                                        }
                                        if (!synced) {
                                            android.util.Log.w(
                                                "MainActivity",
                                                "Onboarding profile sync failed after retries; continuing"
                                            )
                                        }
                                    }
                                    appOnboardingDataStore.setHasSeenAuthQrOnFirstLaunch(true)
                                    onboardingCompletedThisSession = true
                                    onboardingProfileSyncInProgress = false
                                }
                                if (authManager.authState.value is AuthState.FullAccount) {
                                    startupSyncService.requestSyncNow()
                                }
                            }
                        )
                        return@Surface
                    }

                    val shouldShowProfileSelection =
                        !hasSelectedProfileThisSession && (profiles.size > 1 || activeProfileHasPin)

                    if (shouldShowProfileSelection) {
                        ProfileSelectionScreen(
                            onProfileSelected = {
                                hasSelectedProfileThisSession = true
                                if (authManager.authState.value is AuthState.FullAccount) {
                                    startupSyncService.requestSyncNow()
                                }
                            }
                        )
                        return@Surface
                    }

                    val layoutChosen = mainUiPrefs.hasChosenLayout
                    // For You launch behavior: when Trakt is authenticated, For
                    // You is the default landing tab; otherwise Home. Resolved
                    // before the NavHost composes (gated below) so the start
                    // destination is correct on first frame.
                    val traktAuthed by traktAuthDataStore.isAuthenticated
                        .collectAsState(initial = null)
                    if (layoutChosen == null || !mainUiPrefs.experienceModeLoaded ||
                        installedAddons == null || traktAuthed == null
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NuvioColors.Background)
                        )
                        return@Surface
                    }
                    val effectiveExperienceMode = mainUiPrefs.experienceMode
                        ?: if (layoutChosen) ExperienceMode.ADVANCED else null
                    val needsExperienceSelection = effectiveExperienceMode == null
                    val needsEssentialAddonSetup =
                        effectiveExperienceMode == ExperienceMode.ESSENTIAL &&
                            installedAddons.orEmpty().isEmpty() &&
                            !mainUiPrefs.addonSetupSkipped

                    if (needsEssentialAddonSetup) {
                        EssentialAddonSetupScreen(
                            onSkip = {
                                lifecycleScope.launch {
                                    experienceModeDataStore.setAddonSetupSkipped(true)
                                }
                            }
                        )
                        return@Surface
                    }
                    // Onboarding flow: skip Essential/Advanced choice entirely.
                    // First launch → LayoutSelection (writes global default) →
                    // Home. The Essential/Advanced toggle still exists in
                    // Settings → Experience for users who want to flip it
                    // later; we just no longer prompt at startup.
                    val startDestination = when {
                        layoutChosen && traktAuthed == true -> Screen.ForYou.route
                        layoutChosen -> Screen.Home.route
                        else -> Screen.LayoutSelection.route
                    }
                    // One-shot: promote the For You pill to first position for
                    // Trakt-authenticated upgraders (fresh installs already have
                    // it first). Idempotent + flag-guarded inside the store.
                    LaunchedEffect(traktAuthed) {
                        if (traktAuthed == true) {
                            categoryPillOrderDataStore.promoteForYouToFrontOnce()
                        }
                    }
                    val navController = rememberNavController()
                    var optimisticRoute by remember { mutableStateOf<String?>(null) }
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val actualRoute = navBackStackEntry?.destination?.route
                    val currentRoute = optimisticRoute ?: actualRoute

                    LaunchedEffect(actualRoute) {
                        optimisticRoute = null
                    }

                    // Navigate to content when launched from the Continue Watching channel row.
                    LaunchedEffect(navController) {
                        if (launchContentId != null && launchContentType != null && layoutChosen) {
                            navController.navigate(
                                Screen.Detail.createRoute(
                                    itemId = launchContentId,
                                    itemType = launchContentType
                                )
                            )
                        }
                    }

                    val view = LocalView.current
                    LaunchedEffect(currentRoute) {
                        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
                        if (currentRoute != null) {
                            holder.state?.putState("Screen", currentRoute)
                        }
                    }

                    val rootRoutes = remember {
                        setOf(
                            Screen.ForYou.route,
                            Screen.Home.route,
                            Screen.Search.route,
                            Screen.Discover.route,
                            Screen.Library.route,
                            Screen.Movies.route,
                            Screen.TvShows.route,
                            Screen.CollectionsHome.route,
                            Screen.Account.route
                        )
                    }

                    val showDiscoverInRail by layoutPreferenceDataStore.searchDiscoverEnabled
                        .collectAsState(initial = true)
                    val sideRailSearchVisible by layoutPreferenceDataStore.sideRailSearchVisible
                        .collectAsState(initial = true)
                    val sideRailMyStuffVisible by layoutPreferenceDataStore.sideRailMyStuffVisible
                        .collectAsState(initial = true)
                    val sideRailPillChannelsVisible by layoutPreferenceDataStore.sideRailPillChannelsVisible
                        .collectAsState(initial = true)
                    val sideRailSettingsVisible by layoutPreferenceDataStore.sideRailSettingsVisible
                        .collectAsState(initial = true)
                    TopNavBarScaffold(
                        navController = navController,
                        startDestination = startDestination,
                        currentRoute = currentRoute,
                        rootRoutes = rootRoutes,
                        onNavigate = { optimisticRoute = it },
                        onExitApp = {
                            finishAffinity()
                            finishAndRemoveTask()
                        },
                        profileName = activeProfile?.name,
                        profileColorHex = activeProfile?.avatarColorHex,
                        profileAvatarUrl = activeProfileAvatarImageUrl,
                        showDiscoverInRail = showDiscoverInRail,
                        sideRailSearchVisible = sideRailSearchVisible,
                        sideRailMyStuffVisible = sideRailMyStuffVisible,
                        sideRailPillChannelsVisible = sideRailPillChannelsVisible,
                        sideRailSettingsVisible = sideRailSettingsVisible,
                        navigationFeel = mainUiPrefs.navigationFeel,
                        topBarEnabled = mainUiPrefs.topBarEnabled,
                        modernTopBarEnabled = mainUiPrefs.modernTopBarEnabled,
                    )

                    if (AppFeaturePolicy.inAppUpdatesEnabled && !BuildConfig.IS_DEBUG_BUILD) {
                        val updateViewModel: UpdateViewModel = hiltViewModel(this@MainActivity)
                        val updateState by updateViewModel.uiState.collectAsState()
                        UpdatePromptDialog(
                            state = updateState,
                            onDismiss = { updateViewModel.dismissDialog() },
                            onDownload = { updateViewModel.downloadUpdate() },
                            onInstall = { updateViewModel.installUpdateOrRequestPermission() },
                            onIgnore = { updateViewModel.ignoreThisVersion() },
                            onOpenUnknownSources = { updateViewModel.openUnknownSourcesSettings() }
                        )
                    }
                }
            }
            }
        }

        jankStats = JankStats.createAndTrack(window) { frameData ->
            if (frameData.isJank) {
                Log.w(
                    "JankStats",
                    "JANK: ${frameData.frameDurationUiNanos / 1_000_000}ms | states: ${frameData.states}"
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::jankStats.isInitialized) jankStats.isTrackingEnabled = true
        startupSyncService.requestSyncNow(includeProfileSettings = false)
        lifecycleScope.launch {
            if (isFirstResumeAfterCreate) {
                isFirstResumeAfterCreate = false
                traktProgressService.invalidateAndRefresh()
            } else {
                traktProgressService.refreshNow()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::jankStats.isInitialized) jankStats.isTrackingEnabled = false
    }

    override fun onStart() {
        super.onStart()
        profileSettingsSyncService.requestForegroundPull()
    }

    override fun onDestroy() {
        super.onDestroy()
        PluginRuntimeHooks.onActivityDestroy()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopNavBarScaffold(
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    onNavigate: (String) -> Unit,
    onExitApp: () -> Unit,
    profileName: String?,
    profileColorHex: String?,
    profileAvatarUrl: String?,
    showDiscoverInRail: Boolean,
    sideRailSearchVisible: Boolean,
    sideRailMyStuffVisible: Boolean,
    sideRailPillChannelsVisible: Boolean,
    sideRailSettingsVisible: Boolean,
    navigationFeel: Feel,
    topBarEnabled: Boolean,
    modernTopBarEnabled: Boolean,
) {
    val isModernFeel = navigationFeel == Feel.MODERN
    val layoutRoutes = remember {
        setOf(
            Screen.ForYou.route,
            Screen.Home.route,
            Screen.Movies.route,
            Screen.TvShows.route,
            Screen.CollectionsHome.route,
        )
    }
    val isOnLayoutRoute = currentRoute in layoutRoutes
    val showTopNav = (isModernFeel || topBarEnabled) && isOnLayoutRoute
    val showSideRail = !isModernFeel && isOnLayoutRoute

    // Whenever the user lands on a layout route, force the immersion
    // state back to visible so the TopBar reappears immediately (no
    // alpha-fade lag). Without this, returning from Settings /
    // Discover after scrolling deep on Home left the bar invisible
    // because the immersion flag was still false.
    LaunchedEffect(currentRoute) {
        if (currentRoute in layoutRoutes) {
            com.nuvio.tv.ui.components.TopBarImmersionState.setVisible(true)
        }
    }
    val contentFocusRequester = remember { FocusRequester() }
    val navBarFr              = remember { FocusRequester() }
    val sideRailFr            = remember { FocusRequester() }

    val channelRailVm: ChannelRailViewModel = hiltViewModel()
    val railPills by channelRailVm.enabledPills.collectAsState()
    val folderPillOptions by channelRailVm.allFolderOptions.collectAsState()
    var showFolderPillsDropdown by remember { mutableStateOf(false) }

    // Modern-feel category pill ordering (F9 + F10). Powers the dynamic
    // TopBar pill list and the Profile Overlay's Hidden Items section.
    //
    // `pillOrderFull` is nullable — null while the persisted layout is
    // still being read from disk on cold start. Treat as "not yet
    // loaded" and skip rendering pills until the first real emission
    // lands; the seed-default flash would otherwise overwrite the
    // user's saved layout if any mutation fires in the same window.
    val pillsVm: CategoryPillsViewModel = hiltViewModel()
    val topbarPills by pillsVm.topbarPills.collectAsState()
    val drawerPills by pillsVm.drawerPills.collectAsState()
    val pillOrderFull by pillsVm.order.collectAsState()
    val pillsLoaded by pillsVm.isLoaded.collectAsState()

    val collectionRailVm: com.nuvio.tv.ui.screens.collection.CollectionRailViewModel = hiltViewModel()
    val allCollections by collectionRailVm.collections.collectAsState()
    val selectedCollection by collectionRailVm.selectedCollection.collectAsState()
    val folderChannels by collectionRailVm.folderChannels.collectAsState()
    var showCollectionsDropdown by remember { mutableStateOf(false) }

    // Channel rail is mutually exclusive: when a collection is selected (via
    // long-press on Collections), its folders take over the rail entirely.
    // Default mode shows the user-curated cross-collection folder pills from
    // [ChannelRailViewModel].
    val effectiveChannels = if (selectedCollection != null) folderChannels else railPills

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedChannelIndex  by remember { mutableStateOf<Int?>(null) }

    // ── F10 Edit Mode state ──────────────────────────────────────────────
    // Modern-feel-only. Long-press on a non-Collections category pill
    // flips this on; Back / Done flips it off (and persists via the
    // ViewModel's writes — every swap/demote/promote saves on the spot).
    var editMode by remember { mutableStateOf(false) }
    LaunchedEffect(navigationFeel) {
        // If the user flips back to Legacy mid-edit, drop edit state so
        // it doesn't reappear unexpectedly the next time they re-enter
        // Modern.
        if (!isModernFeel) editMode = false
    }

    // Route ↔ CategoryPill mapping. Used in Modern feel to drive the
    // dynamic pill list selection state and to translate index taps
    // through the live (reorderable) topbarPills list.
    //
    // CHANNELS has no dedicated route — when promoted to the TopBar it
    // governs the channel-pill rail's visibility instead of acting as
    // a navigation pill. We fall it back to Home.route so the mapping
    // stays total, but the rendering path filters CHANNELS out of the
    // pill list so the route is never invoked.
    val pillForRoute: (String?) -> CategoryPill? = remember {
        { route ->
            when (route) {
                Screen.ForYou.route -> CategoryPill.FOR_YOU
                Screen.Home.route -> CategoryPill.HOME
                Screen.Movies.route -> CategoryPill.MOVIES
                Screen.TvShows.route -> CategoryPill.TV_SHOWS
                Screen.CollectionsHome.route -> CategoryPill.COLLECTIONS
                Screen.Search.route -> CategoryPill.SEARCH
                Screen.Discover.route -> CategoryPill.DISCOVER
                Screen.Library.route -> CategoryPill.MY_STUFF
                Screen.Settings.route -> CategoryPill.SETTINGS
                else -> null
            }
        }
    }
    val routeForPill: (CategoryPill) -> String = remember {
        { pill ->
            when (pill) {
                CategoryPill.FOR_YOU -> Screen.ForYou.route
                CategoryPill.HOME -> Screen.Home.route
                CategoryPill.MOVIES -> Screen.Movies.route
                CategoryPill.TV_SHOWS -> Screen.TvShows.route
                CategoryPill.COLLECTIONS -> Screen.CollectionsHome.route
                CategoryPill.SEARCH -> Screen.Search.route
                CategoryPill.DISCOVER -> Screen.Discover.route
                CategoryPill.MY_STUFF -> Screen.Library.route
                CategoryPill.SETTINGS -> Screen.Settings.route
                CategoryPill.CHANNELS -> Screen.Home.route
            }
        }
    }
    val iconForPill: (CategoryPill) -> androidx.compose.ui.graphics.vector.ImageVector = remember {
        { pill ->
            when (pill) {
                CategoryPill.FOR_YOU -> Icons.Default.AutoAwesome
                CategoryPill.HOME -> Icons.Default.Home
                CategoryPill.MOVIES -> Icons.Default.Movie
                CategoryPill.TV_SHOWS -> Icons.Default.Tv
                CategoryPill.COLLECTIONS -> Icons.Default.Folder
                CategoryPill.SEARCH -> Icons.Default.Search
                CategoryPill.DISCOVER -> Icons.Default.Explore
                CategoryPill.MY_STUFF -> Icons.Default.Bookmark
                CategoryPill.SETTINGS -> Icons.Default.Settings
                CategoryPill.CHANNELS -> Icons.Default.Tune
            }
        }
    }

    // Keep nav bar visual state in sync with the current screen. Both
    // feels now derive the selected index from the live topbarPills list
    // (filtered to the category-pill subset that actually renders on the
    // bar — CHANNELS is virtual, SideRail items don't render in Legacy)
    // so user-reordered pills track the active route correctly.
    val legacyAllowedPillsForIndex = remember {
        setOf(
            CategoryPill.FOR_YOU,
            CategoryPill.HOME,
            CategoryPill.MOVIES,
            CategoryPill.TV_SHOWS,
            CategoryPill.COLLECTIONS,
            CategoryPill.CHANNELS,
        )
    }
    LaunchedEffect(currentRoute, topbarPills, isModernFeel) {
        val pill = pillForRoute(currentRoute)
        val rendered = if (isModernFeel) {
            topbarPills.filter { it != CategoryPill.CHANNELS }
        } else {
            topbarPills.filter { it in legacyAllowedPillsForIndex && it != CategoryPill.CHANNELS }
        }
        selectedCategoryIndex = if (pill != null) rendered.indexOf(pill) else -1
        selectedChannelIndex = null
    }

    // Back during edit mode exits edit mode instead of bubbling to the
    // app-exit handler below. Composed inside this scaffold so it's
    // disabled automatically when editMode flips off.
    BackHandler(enabled = editMode) {
        editMode = false
    }

    BackHandler(enabled = currentRoute in rootRoutes, onBack = onExitApp)

    // D-pad Left at leftmost content item moves focus into the SideRail
    // (Legacy) or opens the Profile Overlay (Modern). Same composition
    // local — different destination based on Feel.
    var showProfileOverlay by remember { mutableStateOf(false) }
    val openSideRail: () -> Unit = remember(sideRailFr) {
        { runCatching { sideRailFr.requestFocus() } }
    }
    val openProfileOverlay: () -> Unit = remember { { showProfileOverlay = true } }

    // Per Prime Video reference: the SideRail is a true overlay. When the
    // user expands it, it overlaps the leftmost category pill rather than
    // pushing the TopNavigationBar rightward. No padding adjustment needed.
    val activeSideRailItem = when (currentRoute) {
        Screen.Home.route -> com.nuvio.tv.ui.components.SideRailItem.Home
        Screen.Search.route -> com.nuvio.tv.ui.components.SideRailItem.Search
        Screen.Discover.route -> com.nuvio.tv.ui.components.SideRailItem.Discover
        Screen.Library.route -> com.nuvio.tv.ui.components.SideRailItem.MyStuff
        Screen.Settings.route -> com.nuvio.tv.ui.components.SideRailItem.Settings
        else -> null
    }

    // Dynamic TopBar height — measured via Modifier.onGloballyPositioned
    // on the bar's outer Box (further down in this scaffold) and pushed
    // into TopBarImmersionState.topBarHeightDp. Hero text composables
    // read this through LocalTopBarOverlayHeight so the inset reacts to
    // the actual rendered bar height instead of a hard-coded constant.
    // Fallback 60dp matches the prior static value so the very first
    // frame (before measurement lands) doesn't underlay the hero text
    // behind the bar.
    val measuredTopBarHeight by com.nuvio.tv.ui.components.TopBarImmersionState
        .topBarHeightDp.collectAsState()
    val heroTextTopInset = if (showTopNav) {
        val measured = measuredTopBarHeight
        if (measured > 0.dp) measured + 8.dp else 68.dp
    } else 0.dp

    CompositionLocalProvider(
        LocalContentFocusRequester provides contentFocusRequester,
        LocalNavBarFocusRequester  provides navBarFr,
        // Modern feel: D-pad Left from the first content item hard-stops
        // (no Profile Overlay). The avatar Select still opens the overlay
        // directly. Legacy keeps the SideRail entry. The Profile Overlay is
        // now reachable only via the avatar, not the Left gesture.
        LocalSideRailController   provides if (isModernFeel) null else openSideRail,
        // Modern feel has no SideRail; content composables drop their
        // left buffer when they see this. Legacy keeps the existing
        // padding because the SideRail needs that clearance.
        LocalIsModernFeel         provides isModernFeel,
        // Hero text inset — measured TopBar height + 8dp breathing room
        // so the hero title logo never sits behind the bar even when the
        // bar grows for taller fonts / accessibility scaling.
        LocalTopBarOverlayHeight  provides heroTextTopInset,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Hero backdrop runs full-bleed to y=0 in every mode so the
            // glassmorphism TopBar has actual image content to blur and
            // pick up colors from. The per-screen hero text/info Columns
            // apply their own top inset (via LocalTopBarOverlayHeight) so
            // text doesn't sit behind the TopBar — see ModernHomeContent /
            // ClassicHomeContent / GridHomeContent.
            NuvioNavHost(
                navController = navController,
                startDestination = startDestination,
                hideBuiltInHeaders = showTopNav,
            )

            // Transparent nav bar overlays the hero at the top.
            // Immersion mode: when the user scrolls past the hero into the
            // catalog rows, HomeScreen flips `TopBarImmersionState.visible`
            // to false; we drive a gradual 600ms alpha fade so the bar
            // smoothly disappears (per spec E: "make the fade more apparent
            // and gradual"). Back/Up to the hero brings it back.
            val topBarVisible by com.nuvio.tv.ui.components.TopBarImmersionState.visible
                .collectAsState()
            // Asymmetric animation: showing is instant (snap) so the
            // bar reappears with alpha 1.0 the moment the user lands on
            // a layout route, even after immersion-mode had hidden it.
            // Hiding still uses the gradual 600ms fade so scrolling
            // into rows feels smooth.
            val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (topBarVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = if (topBarVisible) 300 else 400,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
                label = "topBarImmersionAlpha",
            )
            if (showTopNav) {
                // Backdrop selection:
                //  - Modern Top Bar ON  → real frosted-glass: render the
                //    same hero backdrop URL the home screens pushed into
                //    TopBarImmersionState, but through Coil's
                //    BlurTransformation (radius = 25). Coil caches the
                //    blurred bitmap so subsequent loads are instant. A
                //    Color.Black @ 0.3 overlay sits on top for text
                //    readability, and the bottom 12dp fades to
                //    transparent so the bar melts into the hero below.
                //    Works on every API level — no GPU RenderEffect
                //    dependency.
                //  - Modern Top Bar OFF → the original opaque-ish vertical
                //    gradient (0.55 alpha solid → transparent), unchanged
                //    so flipping the toggle off returns to the previous
                //    behaviour verbatim.
                androidx.compose.runtime.LaunchedEffect(modernTopBarEnabled) {
                    android.util.Log.d(
                        "NuvioTopBar",
                        "modernTopBarEnabled=$modernTopBarEnabled sdkInt=" +
                            "${android.os.Build.VERSION.SDK_INT}"
                    )
                }
                val topBarBackdropUrl by com.nuvio.tv.ui.components.TopBarImmersionState
                    .backdropUrl.collectAsState()
                val topBarDensity = LocalDensity.current
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .alpha(topBarAlpha)
                        .fillMaxWidth()
                        // FIX 4: the bar is hidden via alpha (still laid out and
                        // focusable), so when Back/Up from deep rows returns focus
                        // to the TopBar it can land here while invisible. Force it
                        // visible the moment it (or any child pill) gains focus so
                        // the user is never focused on an invisible bar.
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                com.nuvio.tv.ui.components.TopBarImmersionState
                                    .setVisible(true)
                            }
                        }
                        // Measure the actual rendered bar height and push
                        // it into TopBarImmersionState so hero text can
                        // inset itself dynamically (Task 1).
                        .onGloballyPositioned { coords ->
                            val heightPx = coords.size.height
                            if (heightPx > 0) {
                                val dp = with(topBarDensity) { heightPx.toDp() }
                                com.nuvio.tv.ui.components.TopBarImmersionState
                                    .setTopBarHeightDp(dp)
                            }
                        }
                ) {
                    if (modernTopBarEnabled) {
                        if (!topBarBackdropUrl.isNullOrBlank()) {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val blurredRequest = remember(ctx, topBarBackdropUrl) {
                                coil3.request.ImageRequest.Builder(ctx)
                                    .data(topBarBackdropUrl)
                                    .crossfade(false)
                                    .transformations(
                                        com.nuvio.tv.ui.util.BlurTransformation(radius = 25)
                                    )
                                    .build()
                            }
                            coil3.compose.AsyncImage(
                                model = blurredRequest,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        // Readability tint + 12dp bottom gradient fade.
                        // Drawn over the blurred image (or alone, on
                        // non-home screens where backdrop URL is null).
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Black.copy(alpha = 0.3f),
                                            0.80f to Color.Black.copy(alpha = 0.3f),
                                            1.0f to Color.Transparent,
                                        )
                                    )
                                )
                        )
                    } else {
                        // Default (non-glass) TopBar: subtle readability
                        // vignette. ~0.3 alpha at y=0 fading to fully
                        // transparent across ~40dp (≈67% of the 60dp
                        // bar). Just enough for pill text to stay legible
                        // over bright hero images without looking like a
                        // visible block.
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Black.copy(alpha = 0.3f),
                                            0.67f to Color.Transparent,
                                            1f to Color.Transparent,
                                        )
                                    )
                                )
                        )
                    }
                // Both feels honour the user's saved TopBar configuration —
                // pill order, display mode, and visibility all flow from
                // CategoryPillsViewModel. Legacy additionally filters out the
                // overlay-only pills (SEARCH / DISCOVER / MY_STUFF / SETTINGS)
                // since those live on the SideRail in Legacy and are never
                // configurable from the TopBar.
                //
                // CHANNELS is a virtual pill — when in TOPBAR it governs the
                // channel-pill rail's visibility rather than rendering as a
                // category pill, so filter it out of the renderable list.
                val orderSnapshot = pillOrderFull.orEmpty()
                val legacyAllowedPills = setOf(
                    CategoryPill.FOR_YOU,
                    CategoryPill.HOME,
                    CategoryPill.MOVIES,
                    CategoryPill.TV_SHOWS,
                    CategoryPill.COLLECTIONS,
                    CategoryPill.CHANNELS,
                )
                val effectiveTopbarPills = if (isModernFeel) {
                    topbarPills
                } else {
                    // Legacy: filter SideRail items out — they're never on
                    // the Legacy TopBar regardless of user settings.
                    topbarPills.filter { it in legacyAllowedPills }
                }
                val renderablePills = effectiveTopbarPills.filter { it != CategoryPill.CHANNELS }
                val effectiveCategories = renderablePills.map { it.displayLabel }
                val effectiveCategoryIcons = renderablePills.map { pill -> iconForPill(pill) }
                val effectiveCategoryIconsOnly = renderablePills.map { pill ->
                    orderSnapshot.firstOrNull { it.pill == pill }?.displayMode ==
                        com.nuvio.tv.domain.model.CategoryPillDisplayMode.ICON_ONLY
                }
                val effectiveCategoryTextOnly = renderablePills.map { pill ->
                    orderSnapshot.firstOrNull { it.pill == pill }?.displayMode ==
                        com.nuvio.tv.domain.model.CategoryPillDisplayMode.TEXT_ONLY
                }
                // Channel rail is gated by the CHANNELS pill's visibility in
                // BOTH feels now — Legacy respects the user's TopBar settings
                // too (Task C: "Channel pills visibility and display mode
                // should also be respected in Legacy").
                val channelRailVisible = effectiveTopbarPills.contains(CategoryPill.CHANNELS)
                val channelDisplayMode = orderSnapshot
                    .firstOrNull { it.pill == CategoryPill.CHANNELS }
                    ?.displayMode
                    ?: com.nuvio.tv.domain.model.CategoryPillDisplayMode.ICON_AND_TEXT
                TopNavigationBar(
                    categories = effectiveCategories,
                    channels = effectiveChannels,
                    selectedCategoryIndex = selectedCategoryIndex,
                    selectedChannelIndex = selectedChannelIndex,
                    firstTabFocusRequester = navBarFr,
                    collectionContextLabel = selectedCollection?.title,
                    isModernFeel = isModernFeel,
                    profileName = profileName,
                    profileColorHex = profileColorHex,
                    profileAvatarUrl = profileAvatarUrl,
                    onProfileClick = { showProfileOverlay = true },
                    categoryIcons = effectiveCategoryIcons,
                    categoryIconsOnly = effectiveCategoryIconsOnly,
                    categoryTextOnly = effectiveCategoryTextOnly,
                    channelRailVisible = channelRailVisible,
                    channelDisplayMode = channelDisplayMode,
                    editMode = editMode && isModernFeel,
                    onEditSwap = { fromVisible, toVisible ->
                        // The TopBar speaks in visible-pill indices over the
                        // rendered (CHANNELS-filtered) list; the ViewModel
                        // speaks in full-order indices. Translate.
                        val fromPill = renderablePills.getOrNull(fromVisible) ?: return@TopNavigationBar
                        val toPill = renderablePills.getOrNull(toVisible) ?: return@TopNavigationBar
                        val full = pillsVm.order.value ?: return@TopNavigationBar
                        val a = full.indexOfFirst { it.pill == fromPill }
                        val b = full.indexOfFirst { it.pill == toPill }
                        if (a >= 0 && b >= 0) pillsVm.swap(a, b)
                    },
                    onEditDemote = { visibleIndex ->
                        val pill = renderablePills.getOrNull(visibleIndex) ?: return@TopNavigationBar
                        pillsVm.demote(pill)
                    },
                    onExitEditMode = { editMode = false },
                    onCategoryLongPress = { index ->
                        // Both feels: long-press a Collections pill opens the
                        // dropdown; any other pill enters Edit Mode (Modern
                        // only — Legacy still uses the original UX which
                        // limits long-press to Collections).
                        val pill = renderablePills.getOrNull(index)
                        when {
                            pill == CategoryPill.COLLECTIONS -> showCollectionsDropdown = true
                            pill == null -> Unit
                            isModernFeel -> editMode = true
                            else -> Unit
                        }
                    },
                    onCategorySelected = { index ->
                        selectedCategoryIndex = index
                        selectedChannelIndex = null
                        // Reset the rail back to network channels whenever any
                        // category pill is clicked.
                        collectionRailVm.clear()
                        // Both feels: translate the visible index through the
                        // live renderable-pill list, then map to its route.
                        val pill = renderablePills.getOrNull(index) ?: CategoryPill.HOME
                        val route = routeForPill(pill)
                        onNavigate(route)
                        navigateToTopNavRoute(navController, currentRoute, route)
                    },
                    onChannelSelected = { index ->
                        selectedChannelIndex = index
                        val ch = effectiveChannels.getOrNull(index) ?: return@TopNavigationBar
                        val folderPair = com.nuvio.tv.ui.screens.collection.CollectionRailViewModel
                            .decodeFolderTabId(ch.id)
                        if (folderPair != null) {
                            val (collectionId, folderId) = folderPair
                            navController.navigate(
                                Screen.FolderDetail.createRoute(collectionId, folderId)
                            )
                        }
                        // No fallback: every rail pill is a folder pill now.
                        // (TMDB-networks rail removed in favor of the
                        // user-curated cross-collection folder rail.)
                    },
                )
                // No hairline in Modern Top Bar mode — the bottom-8dp
                // gradient fade replaces it as the soft separator. Legacy/
                // off mode never had a hairline.
                } // immersion alpha wrapper
            }

            // Permanent transparent icon rail, vertically centered on the left edge.
            // Modern feel skips the rail entirely — content runs edge-to-edge
            // and the profile/Search/Discover entry points live in the
            // Profile Overlay opened from the avatar (added in F4–F6).
            if (showSideRail) {
                SideRail(
                    onSearchClick = {
                        onNavigate(Screen.Search.route)
                        navigateToTopNavRoute(navController, currentRoute, Screen.Search.route)
                    },
                    onHomeClick = {
                        // Same behavior as the TopBar Home pill — keeps the
                        // rail entry as a redundant quick-jump per spec C.
                        onNavigate(Screen.Home.route)
                        navigateToTopNavRoute(navController, currentRoute, Screen.Home.route)
                    },
                    onDiscoverClick = {
                        onNavigate(Screen.Discover.route)
                        navigateToTopNavRoute(navController, currentRoute, Screen.Discover.route)
                    },
                    onMyStuffClick = {
                        // "My Stuff" reuses the Library route (rename only,
                        // no new screen) per the approved decision B.
                        onNavigate(Screen.Library.route)
                        navigateToTopNavRoute(navController, currentRoute, Screen.Library.route)
                    },
                    onSettingsClick = {
                        onNavigate(Screen.Settings.route)
                        navigateToTopNavRoute(navController, currentRoute, Screen.Settings.route)
                    },
                    onPillChannelsClick = { showFolderPillsDropdown = true },
                    onProfileClick = {
                        navController.navigate(Screen.ManageProfiles.route)
                    },
                    profileName = profileName,
                    profileColorHex = profileColorHex,
                    profileAvatarUrl = profileAvatarUrl,
                    showDiscover = showDiscoverInRail,
                    showSearch = sideRailSearchVisible,
                    showMyStuff = sideRailMyStuffVisible,
                    showPillChannels = sideRailPillChannelsVisible,
                    showSettings = sideRailSettingsVisible,
                    firstItemFocusRequester = sideRailFr,
                    activeItem = activeSideRailItem,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }

            if (showCollectionsDropdown) {
                val density = LocalDensity.current
                val dropdownOffset = with(density) {
                    // Approximate position under the Collections pill (4th in
                    // Zone 1). Nav bar starts at 36dp horizontal padding; pills
                    // are ~85dp wide each with ~4dp spacing.
                    IntOffset(x = 305.dp.roundToPx(), y = 64.dp.roundToPx())
                }
                CollectionsDropdown(
                    collections = allCollections,
                    offset = dropdownOffset,
                    onSelect = { collection ->
                        collectionRailVm.selectCollection(collection.id)
                    },
                    onDismiss = { showCollectionsDropdown = false },
                )
            }

            if (showFolderPillsDropdown) {
                val density = LocalDensity.current
                // Anchored near the right edge of the nav bar (where the +
                // button lives). Right-padding matches the nav bar's
                // horizontal padding (36dp); vertical drop sits just below
                // the 60dp tall bar.
                val dropdownOffset = with(density) {
                    IntOffset(x = -36.dp.roundToPx(), y = 64.dp.roundToPx())
                }
                FolderPillsDropdown(
                    options = folderPillOptions,
                    offset = dropdownOffset,
                    onToggle = { option -> channelRailVm.togglePill(option) },
                    onDismiss = { showFolderPillsDropdown = false },
                )
            }

            // Modern-feel-only Profile Overlay. Drawn last so it sits on top
            // of TopBar + NavHost. Triggered by avatar Select or D-pad Left
            // at content carousel index 0 (via LocalSideRailController).
            if (isModernFeel) {
                ProfileOverlay(
                    visible = showProfileOverlay,
                    profileName = profileName,
                    profileColorHex = profileColorHex,
                    profileAvatarUrl = profileAvatarUrl,
                    onDismiss = {
                        showProfileOverlay = false
                        runCatching { navBarFr.requestFocus() }
                    },
                    onNavigate = { destination ->
                        showProfileOverlay = false
                        when (destination) {
                            ProfileOverlayDestination.PILL_CHANNELS -> {
                                // Pill Channels is a popup, not a screen —
                                // close overlay then open the FolderPills
                                // dropdown over the (now empty) TopBar slot.
                                showFolderPillsDropdown = true
                            }
                            else -> {
                                val route = when (destination) {
                                    ProfileOverlayDestination.SEARCH -> Screen.Search.route
                                    ProfileOverlayDestination.DISCOVER -> Screen.Discover.route
                                    ProfileOverlayDestination.MY_STUFF -> Screen.Library.route
                                    ProfileOverlayDestination.SETTINGS -> Screen.Settings.route
                                    ProfileOverlayDestination.MANAGE_PROFILES -> Screen.ManageProfiles.route
                                    ProfileOverlayDestination.PILL_CHANNELS -> Screen.Home.route
                                }
                                onNavigate(route)
                                navigateToTopNavRoute(navController, currentRoute, route)
                            }
                        }
                    },
                    // Default-DRAWER pills (Search / Discover / My Stuff /
                    // Settings / Channels) render as built-in rows above.
                    // The Hidden Items section is reserved for user-demoted
                    // category pills — surfacing them with a `+` icon and
                    // promoting on tap (the original Edit Mode flow).
                    hiddenItems = drawerPills
                        .filter { it.defaultVisibility == com.nuvio.tv.domain.model.PillVisibility.TOPBAR }
                        .map { pill ->
                            ProfileOverlayHiddenItem(id = pill.storageId, label = pill.displayLabel)
                        },
                    onHiddenItemSelected = { item ->
                        val pill = CategoryPill.fromStorageId(item.id) ?: return@ProfileOverlay
                        pillsVm.promote(pill)
                        val route = routeForPill(pill)
                        onNavigate(route)
                        navigateToTopNavRoute(navController, currentRoute, route)
                    },
                    // Each built-in row is gated on its pill currently being
                    // in DRAWER — if the user promotes (e.g.) Search to the
                    // TopBar, the overlay row hides so it doesn't double up
                    // with the pill.
                    showSearch = drawerPills.contains(CategoryPill.SEARCH),
                    showDiscover = drawerPills.contains(CategoryPill.DISCOVER),
                    showMyStuff = drawerPills.contains(CategoryPill.MY_STUFF),
                    showSettings = drawerPills.contains(CategoryPill.SETTINGS),
                    // "Pill Channels" is the management entry point for the
                    // channel-rail folder list. Always-on in the overlay so
                    // users can configure the rail regardless of whether
                    // the CHANNELS pill itself is currently promoted to the
                    // TopBar — the rail's visibility and its contents are
                    // separately-managed concepts.
                    showPillChannels = true,
                    // D3: Long-press on a built-in overlay row promotes its
                    // pill to the TopBar (mirror of "+ Hidden Item" tap).
                    // Map ProfileOverlayDestination → CategoryPill and run
                    // the existing promote() flow.
                    onPromoteBuiltin = { dest ->
                        val pill = when (dest) {
                            ProfileOverlayDestination.SEARCH -> CategoryPill.SEARCH
                            ProfileOverlayDestination.DISCOVER -> CategoryPill.DISCOVER
                            ProfileOverlayDestination.MY_STUFF -> CategoryPill.MY_STUFF
                            ProfileOverlayDestination.SETTINGS -> CategoryPill.SETTINGS
                            ProfileOverlayDestination.PILL_CHANNELS -> CategoryPill.CHANNELS
                            ProfileOverlayDestination.MANAGE_PROFILES -> null
                        }
                        if (pill != null) {
                            pillsVm.promote(pill)
                            showProfileOverlay = false
                            runCatching { navBarFr.requestFocus() }
                        }
                    },
                    // D3: Long-press on a hidden category pill promotes it
                    // (same as tapping the "+" icon — both surface a single
                    // promote action).
                    onPromoteHiddenItem = { item ->
                        val pill = CategoryPill.fromStorageId(item.id)
                        if (pill != null) {
                            pillsVm.promote(pill)
                            showProfileOverlay = false
                            runCatching { navBarFr.requestFocus() }
                        }
                    },
                )
            }
        }
    }
}

private fun navigateToTopNavRoute(
    navController: NavHostController,
    currentRoute: String?,
    targetRoute: String
) {
    // Always reconcile against the navController's REAL current destination
    // rather than the [currentRoute] parameter — that parameter can carry a
    // stale `optimisticRoute` value from a previous tap.  Only Home gets
    // tap-to-scroll-to-top; other tabs no-op on same-tab repeat tap.
    val realRoute = navController.currentDestination?.route
    if (realRoute == targetRoute) {
        if (targetRoute == Screen.Home.route) {
            val homeEntry = runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrNull()
            val homeViewModel = homeEntry?.let {
                androidx.lifecycle.ViewModelProvider(it)[com.nuvio.tv.ui.screens.home.HomeViewModel::class.java]
            }
            homeViewModel?.requestScrollToTop()
        }
        return
    }
    // The bottom-nav `saveState`/`restoreState` pair was silently no-op'ing
    // the FIRST Home→Movies tap: with `launchSingleTop=true` + `restoreState=
    // true`, the navigator consults the saved-state map for the target and
    // short-circuits before the back stack actually changes when the target
    // has no saved state yet AND we're navigating away from the graph's
    // start destination.  TV Shows worked because its branch was warmed up
    // (or it intermittently lost the race differently).  Strip both
    // saveState/restoreState — `popUpTo(start) + launchSingleTop` is enough
    // to keep the back stack a single layer deep without state-restoration
    // ambiguity.  Per-screen scroll/focus state is rebuilt on tab switch
    // (acceptable vs. the silent-no-op bug).
    navController.navigate(targetRoute) {
        popUpTo(navController.graph.startDestinationId)
        launchSingleTop = true
    }
}

object LocaleCache {
    const val UNSET = "__UNSET__"
    @Volatile
    var localeTag: String = UNSET
}
