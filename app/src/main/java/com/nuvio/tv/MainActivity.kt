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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.focus.FocusRequester
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
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.core.sync.ProfileSettingsSyncService
import com.nuvio.tv.core.sync.ProfileSyncService
import com.nuvio.tv.core.sync.StartupSyncService
import com.nuvio.tv.data.remote.supabase.AvatarRepository
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.components.CollectionsDropdown
import com.nuvio.tv.ui.components.FolderPillsDropdown
import com.nuvio.tv.ui.components.NuvioScrollDefaults
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
    val composeHighlighterEnabled: Boolean = false
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
    lateinit var trailerPlayerPool: com.nuvio.tv.core.player.TrailerPlayerPool

    private lateinit var jankStats: JankStats

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
                    LocalRecompositionHighlighterEnabled provides mainUiPrefs.composeHighlighterEnabled,
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
                    if (layoutChosen == null || !mainUiPrefs.experienceModeLoaded || installedAddons == null) {
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
                        layoutChosen -> Screen.Home.route
                        else -> Screen.LayoutSelection.route
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
            traktProgressService.refreshNow()
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
) {
    val showTopNav = currentRoute in rootRoutes
    val contentFocusRequester = remember { FocusRequester() }
    val navBarFr              = remember { FocusRequester() }
    val sideRailFr            = remember { FocusRequester() }

    val channelRailVm: ChannelRailViewModel = hiltViewModel()
    val railPills by channelRailVm.enabledPills.collectAsState()
    val folderPillOptions by channelRailVm.allFolderOptions.collectAsState()
    var showFolderPillsDropdown by remember { mutableStateOf(false) }

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

    // Keep nav bar visual state in sync with the current screen.
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screen.Home.route            -> { selectedCategoryIndex = 0; selectedChannelIndex = null }
            Screen.Movies.route          -> { selectedCategoryIndex = 1; selectedChannelIndex = null }
            Screen.TvShows.route         -> { selectedCategoryIndex = 2; selectedChannelIndex = null }
            Screen.CollectionsHome.route -> { selectedCategoryIndex = 3; selectedChannelIndex = null }
            Screen.Discover.route        -> { selectedCategoryIndex = -1; selectedChannelIndex = null }
            // On non-content screens (Search, Settings, Account) highlight nothing.
            else                  -> selectedCategoryIndex = -1
        }
    }

    BackHandler(enabled = currentRoute in rootRoutes, onBack = onExitApp)

    // D-pad Left at leftmost content item moves focus into the SideRail.
    val openSideRail: () -> Unit = remember(sideRailFr) {
        { runCatching { sideRailFr.requestFocus() } }
    }

    // The SideRail surfaces its expansion state so the TopBar can nudge its
    // left padding away from the overlay when the user opens the rail —
    // otherwise the rail's panel obscures the leftmost category pill.
    var sideRailExpanded by remember { mutableStateOf(false) }
    val topBarLeftPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (sideRailExpanded) 180.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "topBarLeftPadding",
    )

    val activeSideRailItem = when (currentRoute) {
        Screen.Home.route -> com.nuvio.tv.ui.components.SideRailItem.Home
        Screen.Search.route -> com.nuvio.tv.ui.components.SideRailItem.Search
        Screen.Library.route -> com.nuvio.tv.ui.components.SideRailItem.MyStuff
        Screen.Settings.route -> com.nuvio.tv.ui.components.SideRailItem.Settings
        else -> null
    }

    CompositionLocalProvider(
        LocalContentFocusRequester provides contentFocusRequester,
        LocalNavBarFocusRequester  provides navBarFr,
        LocalSideRailController   provides openSideRail,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Full-screen content — hero extends to the very top behind the nav bar.
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
            val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (topBarVisible) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 600,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
                label = "topBarImmersionAlpha",
            )
            if (showTopNav) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .padding(top = 8.dp, start = topBarLeftPadding)
                        .alpha(topBarAlpha)
                ) {
                TopNavigationBar(
                    channels = effectiveChannels,
                    selectedCategoryIndex = selectedCategoryIndex,
                    selectedChannelIndex = selectedChannelIndex,
                    firstTabFocusRequester = navBarFr,
                    collectionContextLabel = selectedCollection?.title,
                    onCategoryLongPress = { index ->
                        if (index == 3) showCollectionsDropdown = true
                    },
                    onCategorySelected = { index ->
                        selectedCategoryIndex = index
                        selectedChannelIndex = null
                        // Reset the rail back to network channels whenever any
                        // category pill is clicked.
                        collectionRailVm.clear()
                        val route = when (index) {
                            1    -> Screen.Movies.route
                            2    -> Screen.TvShows.route
                            3    -> Screen.CollectionsHome.route
                            else -> Screen.Home.route
                        }
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
                    onNetworksClick = { showFolderPillsDropdown = true },
                )
                } // immersion alpha wrapper
            }

            // Permanent transparent icon rail, vertically centered on the left edge.
            if (showTopNav) {
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
                    onProfileClick = {
                        navController.navigate(Screen.ManageProfiles.route)
                    },
                    profileName = profileName,
                    profileColorHex = profileColorHex,
                    profileAvatarUrl = profileAvatarUrl,
                    firstItemFocusRequester = sideRailFr,
                    activeItem = activeSideRailItem,
                    onExpandedChange = { sideRailExpanded = it },
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
    // stale `optimisticRoute` value from a previous tap, which made the
    // first tap on Movies / TV silently early-return ("nothing rendered until
    // the second tap"). `launchSingleTop = true` below already prevents
    // duplicate stack entries when we ARE already on the target, so the only
    // case we need to special-case is "tap Home while on Home → scroll to top".
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
    navController.navigate(targetRoute) {
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

object LocaleCache {
    const val UNSET = "__UNSET__"
    @Volatile
    var localeTag: String = UNSET
}
