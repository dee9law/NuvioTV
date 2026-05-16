@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.ui.navigation.TvBackToFirstThenTopNav
import com.nuvio.tv.ui.navigation.dpadLeftToSideRail
import com.nuvio.tv.ui.navigation.dpadUpToTopNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.ui.screens.plugin.PluginScreenContent
import com.nuvio.tv.ui.theme.NuvioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

internal enum class SettingsCategory {
    EXPERIENCE,
    ACCOUNT,
    PROFILES,
    APPEARANCE,
    LAYOUT,
    PLUGINS,
    INTEGRATION,
    PLAYBACK,
    ADVANCED,
    TRAKT,
    ABOUT,
    DEBUG
}

private enum class IntegrationSettingsSection {
    Hub,
    Tmdb,
    MdbList,
    AnimeSkip
}

internal enum class SettingsSectionDestination {
    Inline,
    External
}

internal data class SettingsSectionSpec(
    val category: SettingsCategory,
    val title: String,
    val icon: ImageVector? = null,
    @param:RawRes val rawIconRes: Int? = null,
    val subtitle: String,
    val destination: SettingsSectionDestination
)

private const val SETTINGS_DETAIL_FOCUS_DELAY_MS = 120L
private const val SETTINGS_DETAIL_ANIM_IN_DURATION_MS = 200
private const val SETTINGS_DETAIL_ANIM_OUT_DURATION_MS = 180

internal enum class SettingsGroup {
    APPEARANCE_GROUP,
    EXTENSIONS_GROUP,
    ACCOUNTS_SYNC_GROUP,
    PLAYBACK_GROUP,
    ADVANCED_GROUP
}

internal data class SettingsGroupSpec(
    val group: SettingsGroup,
    val title: String,
    val icon: ImageVector,
    val pills: List<SettingsPillSpec>
)

internal data class SettingsPillSpec(
    val id: String,
    val title: String,
    val category: SettingsCategory? = null
)

private sealed interface ExperienceModeLoadState {
    data object Loading : ExperienceModeLoadState
    data class Loaded(val mode: ExperienceMode?) : ExperienceModeLoadState
}

@Composable
private fun rememberSettingsSectionSpecs() = listOf(
    SettingsSectionSpec(
        category = SettingsCategory.EXPERIENCE,
        title = stringResource(R.string.settings_experience),
        icon = Icons.Default.Tune,
        subtitle = stringResource(R.string.settings_experience_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.ACCOUNT,
        title = stringResource(R.string.settings_account),
        icon = Icons.Default.Person,
        subtitle = stringResource(R.string.settings_account_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.PROFILES,
        title = stringResource(R.string.settings_profiles),
        icon = Icons.Default.People,
        subtitle = stringResource(R.string.settings_profiles_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.APPEARANCE,
        title = stringResource(R.string.appearance_title),
        icon = Icons.Default.Palette,
        subtitle = stringResource(R.string.appearance_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.LAYOUT,
        title = stringResource(R.string.settings_layout),
        icon = Icons.Default.GridView,
        subtitle = stringResource(R.string.settings_layout_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.PLUGINS,
        title = stringResource(R.string.settings_plugins),
        icon = Icons.Default.Build,
        subtitle = stringResource(R.string.settings_plugins_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.INTEGRATION,
        title = stringResource(R.string.settings_integration),
        icon = Icons.Default.Link,
        subtitle = "",
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.PLAYBACK,
        title = stringResource(R.string.settings_playback),
        icon = Icons.Rounded.PlayArrow,
        subtitle = stringResource(R.string.settings_playback_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.TRAKT,
        title = "Trakt",
        rawIconRes = R.raw.trakt_tv_glyph,
        subtitle = stringResource(R.string.settings_trakt_subtitle),
        destination = SettingsSectionDestination.External
    ),
    SettingsSectionSpec(
        category = SettingsCategory.ABOUT,
        title = stringResource(R.string.about_title),
        icon = Icons.Default.Info,
        subtitle = stringResource(R.string.settings_about_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.ADVANCED,
        title = stringResource(R.string.settings_advanced),
        icon = Icons.Default.Build,
        subtitle = stringResource(R.string.settings_advanced_subtitle),
        destination = SettingsSectionDestination.Inline
    ),
    SettingsSectionSpec(
        category = SettingsCategory.DEBUG,
        title = stringResource(R.string.settings_debug),
        icon = Icons.Default.BugReport,
        subtitle = stringResource(R.string.settings_debug_subtitle),
        destination = SettingsSectionDestination.Inline
    )
)

@Composable
private fun rememberSettingsGroupSpecs(
    isPrimaryProfileActive: Boolean,
    isEssentialMode: Boolean,
    pluginsEnabled: Boolean
): List<SettingsGroupSpec> {
    val appearanceTitle = stringResource(R.string.appearance_title)
    val layoutTitle = stringResource(R.string.settings_layout)
    val pluginsTitle = stringResource(R.string.settings_plugins)
    val accountTitle = stringResource(R.string.settings_account)
    val profilesTitle = stringResource(R.string.settings_profiles)
    val integrationTitle = stringResource(R.string.settings_integration)
    val playbackTitle = stringResource(R.string.settings_playback)
    val advancedTitle = stringResource(R.string.settings_advanced)
    val aboutTitle = stringResource(R.string.about_title)

    return remember(
        isPrimaryProfileActive,
        isEssentialMode,
        pluginsEnabled,
        appearanceTitle,
        layoutTitle,
        pluginsTitle,
        accountTitle,
        profilesTitle,
        integrationTitle,
        playbackTitle,
        advancedTitle,
        aboutTitle
    ) {
        listOf(
            SettingsGroupSpec(
                group = SettingsGroup.APPEARANCE_GROUP,
                title = "Appearance",
                icon = Icons.Default.Palette,
                pills = listOf(
                    SettingsPillSpec("appearance", appearanceTitle, SettingsCategory.APPEARANCE),
                    SettingsPillSpec("layout", "Old Layout", SettingsCategory.LAYOUT),
                    SettingsPillSpec("layout_rows", "Layout & Rows", SettingsCategory.LAYOUT),
                    SettingsPillSpec("display", "Display", null)
                )
            ),
            SettingsGroupSpec(
                group = SettingsGroup.EXTENSIONS_GROUP,
                title = "Extensions",
                icon = Icons.Default.Build,
                pills = buildList {
                    add(SettingsPillSpec("addons", "Addons", null))
                    if (pluginsEnabled && !isEssentialMode) {
                        add(SettingsPillSpec("plugins", pluginsTitle, SettingsCategory.PLUGINS))
                    }
                    add(SettingsPillSpec("collections", "Collections", null))
                }
            ),
            SettingsGroupSpec(
                group = SettingsGroup.ACCOUNTS_SYNC_GROUP,
                title = "Accounts & Sync",
                icon = Icons.Default.Person,
                pills = buildList {
                    if (isPrimaryProfileActive) {
                        add(SettingsPillSpec("account", accountTitle, SettingsCategory.ACCOUNT))
                        add(SettingsPillSpec("profiles", profilesTitle, SettingsCategory.PROFILES))
                    }
                    add(SettingsPillSpec("trakt", "Trakt", null))
                    add(SettingsPillSpec("integrations", integrationTitle, SettingsCategory.INTEGRATION))
                }
            ),
            SettingsGroupSpec(
                group = SettingsGroup.PLAYBACK_GROUP,
                title = "Playback",
                icon = Icons.Rounded.PlayArrow,
                pills = listOf(
                    SettingsPillSpec("playback", playbackTitle, SettingsCategory.PLAYBACK),
                    SettingsPillSpec("link_resolving", "Link Resolving", null),
                    SettingsPillSpec("link_filtering", "Link Filtering", null)
                )
            ),
            SettingsGroupSpec(
                group = SettingsGroup.ADVANCED_GROUP,
                title = "Advanced",
                icon = Icons.Default.Settings,
                pills = listOf(
                    SettingsPillSpec("advanced", advancedTitle, SettingsCategory.ADVANCED),
                    SettingsPillSpec("about", aboutTitle, SettingsCategory.ABOUT)
                )
            )
        )
    }
}

@Composable
fun SettingsScreen(
    showBuiltInHeader: Boolean = true,
    onNavigateToTrakt: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {},
    onNavigateToAuthQrSignIn: () -> Unit = {},
    onNavigateToManageProfiles: () -> Unit = {},
    onNavigateToSupportersContributors: () -> Unit = {},
    profileViewModel: ProfileSettingsViewModel = hiltViewModel(),
    experienceModeViewModel: ExperienceModeSettingsViewModel = hiltViewModel()
) {
    val isPrimaryProfileActive by profileViewModel.isPrimaryProfileActive.collectAsStateWithLifecycle()
    val experienceModeState by remember(experienceModeViewModel) {
        experienceModeViewModel.mode.map<ExperienceMode?, ExperienceModeLoadState> {
            ExperienceModeLoadState.Loaded(it)
        }
    }.collectAsStateWithLifecycle(initialValue = ExperienceModeLoadState.Loading)
    val loadedExperienceMode = (experienceModeState as? ExperienceModeLoadState.Loaded)?.mode
    val experienceModeLoaded = experienceModeState is ExperienceModeLoadState.Loaded

    if (!experienceModeLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuvioColors.Background)
        )
        return
    }

    val isEssentialMode = loadedExperienceMode == ExperienceMode.ESSENTIAL
    val pluginsEnabled = AppFeaturePolicy.pluginsEnabled

    val groupSpecs = rememberSettingsGroupSpecs(
        isPrimaryProfileActive = isPrimaryProfileActive,
        isEssentialMode = isEssentialMode,
        pluginsEnabled = pluginsEnabled
    )

    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var selectedGroup by remember(groupSpecs) {
        mutableStateOf(groupSpecs.firstOrNull()?.group ?: SettingsGroup.APPEARANCE_GROUP)
    }
    val selectedPillIdsByGroup = remember(groupSpecs) {
        androidx.compose.runtime.mutableStateMapOf<SettingsGroup, String>().apply {
            groupSpecs.forEach { spec ->
                spec.pills.firstOrNull()?.let { put(spec.group, it.id) }
            }
        }
    }
    val groupFocusRequesters = remember(groupSpecs) {
        groupSpecs.associate { it.group to FocusRequester() }
    }
    val pillFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val contentFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val railContainerFocusRequester = remember { FocusRequester() }
    val integrationHubFocusRequester = remember { FocusRequester() }
    val integrationTmdbFocusRequester = remember { FocusRequester() }
    val integrationMdbListFocusRequester = remember { FocusRequester() }
    val integrationAnimeSkipFocusRequester = remember { FocusRequester() }
    var integrationSection by remember { mutableStateOf(IntegrationSettingsSection.Hub) }
    var pendingContentFocus by remember { mutableStateOf<Pair<SettingsGroup, String>?>(null) }
    var pendingContentFocusRequestId by remember { mutableLongStateOf(0L) }
    var allowDetailAutofocus by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(groupSpecs) {
        if (groupSpecs.none { it.group == selectedGroup }) {
            selectedGroup = groupSpecs.firstOrNull()?.group ?: SettingsGroup.APPEARANCE_GROUP
        }
    }

    LaunchedEffect(Unit) {
        runCatching { railContainerFocusRequester.requestFocus() }
    }

    LaunchedEffect(pendingContentFocusRequestId) {
        val pending = pendingContentFocus ?: return@LaunchedEffect
        delay(SETTINGS_DETAIL_FOCUS_DELAY_MS)
        val key = "${pending.first.name}|${pending.second}"
        val requester = contentFocusRequesters[key]
        val requested = if (requester != null) {
            runCatching { requester.requestFocus() }.isSuccess
        } else {
            false
        }
        if (!requested) {
            focusManager.moveFocus(FocusDirection.Right)
        }
        pendingContentFocus = null
    }

    val contentEntryFocusRequester = LocalContentFocusRequester.current
    var railHadFocus by remember { mutableStateOf(false) }
    var contentPanelHasFocus by remember { mutableStateOf(false) }

    TvBackToFirstThenTopNav(
        contentHasFocus = { contentPanelHasFocus || railHadFocus },
        isAtFirstItem = { !contentPanelHasFocus },
        requestFirstItemFocus = {
            allowDetailAutofocus = false
            val requested = groupFocusRequesters[selectedGroup]?.let { requester ->
                runCatching { requester.requestFocus() }.isSuccess
            } ?: false
            if (!requested) {
                runCatching { railContainerFocusRequester.requestFocus() }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 32.dp,
                end = 32.dp,
                top = if (showBuiltInHeader) 24.dp else 68.dp,
                bottom = 24.dp
            )
            .dpadUpToTopNav()
            .dpadLeftToSideRail()
    ) {
        SettingsWorkspaceSurface(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val railListState = rememberLazyListState()

                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        state = railListState,
                        modifier = Modifier
                            .focusRequester(railContainerFocusRequester)
                            .focusRequester(contentEntryFocusRequester)
                            .fillMaxSize()
                            .onFocusChanged { state ->
                                val justGainedFocus = !railHadFocus && state.hasFocus
                                railHadFocus = state.hasFocus
                                if (justGainedFocus) {
                                    val requester = groupFocusRequesters[selectedGroup]
                                    val requested = requester?.let {
                                        runCatching { it.requestFocus() }.isSuccess
                                    } ?: false
                                    if (!requested) {
                                        focusManager.moveFocus(FocusDirection.Down)
                                    }
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                val toDetailKey = if (isRtl) Key.DirectionLeft else Key.DirectionRight
                                if (event.type == KeyEventType.KeyDown && event.key == toDetailKey) {
                                    allowDetailAutofocus = true
                                    false
                                } else {
                                    false
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                    ) {
                        items(items = groupSpecs, key = { it.group }) { spec ->
                            SettingsRailButton(
                                title = spec.title,
                                icon = spec.icon,
                                rawIconRes = null,
                                isSelected = selectedGroup == spec.group,
                                focusRequester = groupFocusRequesters[spec.group],
                                onClick = {
                                    allowDetailAutofocus = true
                                    selectedGroup = spec.group
                                    val firstPill = spec.pills.firstOrNull()?.id
                                    if (firstPill != null) {
                                        if (selectedPillIdsByGroup[spec.group] == null) {
                                            selectedPillIdsByGroup[spec.group] = firstPill
                                        }
                                        pendingContentFocus = spec.group to (selectedPillIdsByGroup[spec.group] ?: firstPill)
                                        pendingContentFocusRequestId += 1L
                                    }
                                }
                            )
                        }
                    }
                    SettingsVerticalScrollIndicators(state = railListState)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onKeyEvent { event ->
                            val toRailKey = if (isRtl) Key.DirectionRight else Key.DirectionLeft
                            if (event.type == KeyEventType.KeyDown && event.key == toRailKey) {
                                val movedLeft = focusManager.moveFocus(if (isRtl) FocusDirection.Right else FocusDirection.Left)
                                if (!movedLeft) {
                                    allowDetailAutofocus = false
                                    val requested = groupFocusRequesters[selectedGroup]?.let { requester ->
                                        runCatching { requester.requestFocus() }.isSuccess
                                    } ?: false
                                    if (!requested) {
                                        runCatching { railContainerFocusRequester.requestFocus() }
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                        .onFocusChanged { state ->
                            contentPanelHasFocus = state.hasFocus
                            if (state.hasFocus && !allowDetailAutofocus) {
                                groupFocusRequesters[selectedGroup]?.let { requester ->
                                    runCatching { requester.requestFocus() }
                                }
                            }
                        }
                ) {
                    val activeGroupSpec = groupSpecs.firstOrNull { it.group == selectedGroup }
                    if (activeGroupSpec != null) {
                        val currentPillId = selectedPillIdsByGroup[selectedGroup]
                            ?: activeGroupSpec.pills.firstOrNull()?.id
                        val currentPill = activeGroupSpec.pills.firstOrNull { it.id == currentPillId }

                        if (currentPill != null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SettingsPillRow(
                                    pills = activeGroupSpec.pills,
                                    selectedPillId = currentPill.id,
                                    pillFocusRequester = { pillId ->
                                        pillFocusRequesters.getOrPut(
                                            "${selectedGroup.name}|$pillId"
                                        ) { FocusRequester() }
                                    },
                                    onPillClick = { pill ->
                                        selectedPillIdsByGroup[selectedGroup] = pill.id
                                    }
                                )

                                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                    val contentKey = "${selectedGroup.name}|${currentPill.id}"
                                    val pillContentFr = contentFocusRequesters
                                        .getOrPut(contentKey) { FocusRequester() }
                                    val initialFr =
                                        if (allowDetailAutofocus) pillContentFr else null

                                    RenderPillContent(
                                        pill = currentPill,
                                        initialFocusRequester = initialFr,
                                        isEssentialMode = isEssentialMode,
                                        pluginsEnabled = pluginsEnabled,
                                        experienceModeViewModel = experienceModeViewModel,
                                        integrationSection = integrationSection,
                                        onSelectIntegrationSection = { integrationSection = it },
                                        integrationHubFocusRequester = integrationHubFocusRequester,
                                        integrationTmdbFocusRequester = integrationTmdbFocusRequester,
                                        integrationMdbListFocusRequester = integrationMdbListFocusRequester,
                                        integrationAnimeSkipFocusRequester = integrationAnimeSkipFocusRequester,
                                        autoFocusEnabled = allowDetailAutofocus,
                                        onNavigateToTrakt = onNavigateToTrakt,
                                        onNavigateToAddons = onNavigateToAddons,
                                        onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn,
                                        onNavigateToManageProfiles = onNavigateToManageProfiles,
                                        onNavigateToSupportersContributors = onNavigateToSupportersContributors
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginsSettingsContent() {
    val pluginViewModel: com.nuvio.tv.ui.screens.plugin.PluginViewModel = hiltViewModel()
    val pluginUiState by pluginViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_plugins),
            subtitle = stringResource(R.string.settings_plugins_section_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopStart
            ) {
                PluginScreenContent(
                    uiState = pluginUiState,
                    viewModel = pluginViewModel,
                    showHeader = false
                )
            }
        }
    }
}

@Composable
private fun EssentialAdvancedSettingsContent(
    experienceModeViewModel: ExperienceModeSettingsViewModel,
    initialFocusRequester: FocusRequester?
) {
    var showConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_advanced),
            subtitle = stringResource(R.string.experience_mode_switch_to_advanced_header_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.experience_mode_switch_to_advanced),
                subtitle = stringResource(R.string.experience_mode_switch_to_advanced_subtitle),
                value = stringResource(R.string.experience_mode_essential),
                onClick = { showConfirmation = true },
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }

    if (showConfirmation) {
        ExperienceModeConfirmationDialog(
            targetMode = ExperienceMode.ADVANCED,
            onConfirm = { experienceModeViewModel.setMode(ExperienceMode.ADVANCED) },
            onDismiss = { showConfirmation = false }
        )
    }
}

@Composable
internal fun AccountSettingsInline(
    onNavigateToAuthQrSignIn: () -> Unit
) {
    val accountViewModel: com.nuvio.tv.ui.screens.account.AccountViewModel = hiltViewModel()
    val accountUiState by accountViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.settings_account),
            subtitle = stringResource(R.string.settings_account_section_subtitle)
        )
        SettingsGroupCard(modifier = Modifier.fillMaxSize()) {
            com.nuvio.tv.ui.screens.account.AccountSettingsContent(
                uiState = accountUiState,
                viewModel = accountViewModel,
                onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn
            )
        }
    }
}

@Composable
private fun IntegrationSettingsContent(
    selectedSection: IntegrationSettingsSection,
    onSelectSection: (IntegrationSettingsSection) -> Unit,
    initialFocusRequester: FocusRequester?,
    hubFocusRequester: FocusRequester,
    tmdbFocusRequester: FocusRequester,
    mdbListFocusRequester: FocusRequester,
    animeSkipFocusRequester: FocusRequester,
    autoFocusEnabled: Boolean
) {
    BackHandler(enabled = selectedSection != IntegrationSettingsSection.Hub) {
        onSelectSection(IntegrationSettingsSection.Hub)
    }
    val hubEntryFocusRequester = initialFocusRequester ?: hubFocusRequester

    LaunchedEffect(selectedSection, autoFocusEnabled) {
        if (!autoFocusEnabled) return@LaunchedEffect
        val requester = when (selectedSection) {
            IntegrationSettingsSection.Hub -> hubEntryFocusRequester
            IntegrationSettingsSection.Tmdb -> tmdbFocusRequester
            IntegrationSettingsSection.MdbList -> mdbListFocusRequester
            IntegrationSettingsSection.AnimeSkip -> animeSkipFocusRequester
        }
        runCatching { requester.requestFocus() }
    }

    when (selectedSection) {
        IntegrationSettingsSection.Hub -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsDetailHeader(
                    title = stringResource(R.string.settings_integrations_section),
                    subtitle = stringResource(R.string.settings_integrations_section_subtitle)
                )

                SettingsGroupCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val integrationHubState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = integrationHubState,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(key = "integration_hub_tmdb") {
                                SettingsActionRow(
                                    title = "TMDB",
                                    subtitle = stringResource(R.string.settings_tmdb_subtitle),
                                    onClick = { onSelectSection(IntegrationSettingsSection.Tmdb) },
                                    modifier = Modifier.focusRequester(hubEntryFocusRequester)
                                )
                            }
                            item(key = "integration_hub_mdblist") {
                                SettingsActionRow(
                                    title = "MDBList",
                                    subtitle = stringResource(R.string.settings_mdblist_subtitle),
                                    onClick = { onSelectSection(IntegrationSettingsSection.MdbList) }
                                )
                            }
                            item(key = "integration_hub_animeskip") {
                                SettingsActionRow(
                                    title = "Anime-Skip",
                                    subtitle = stringResource(R.string.settings_animeskip_subtitle),
                                    onClick = { onSelectSection(IntegrationSettingsSection.AnimeSkip) }
                                )
                            }
                        }
                        SettingsVerticalScrollIndicators(state = integrationHubState)
                    }
                }
            }
        }

        IntegrationSettingsSection.Tmdb -> {
            TmdbSettingsContent(
                initialFocusRequester = tmdbFocusRequester
            )
        }

        IntegrationSettingsSection.MdbList -> {
            MDBListSettingsContent(
                initialFocusRequester = mdbListFocusRequester
            )
        }

        IntegrationSettingsSection.AnimeSkip -> {
            AnimeSkipSettingsContent(
                initialFocusRequester = animeSkipFocusRequester
            )
        }
    }
}

// ── Pill sub-navigation ──────────────────────────────────────────────────────

@Composable
private fun SettingsPillRow(
    pills: List<SettingsPillSpec>,
    selectedPillId: String,
    pillFocusRequester: (String) -> FocusRequester,
    onPillClick: (SettingsPillSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pills.forEach { pill ->
            SettingsPillTab(
                text = pill.title,
                isSelected = pill.id == selectedPillId,
                focusRequester = pillFocusRequester(pill.id),
                onClick = { onPillClick(pill) }
            )
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPillTab(
    text: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val containerColor = when {
        isSelected -> androidx.compose.ui.graphics.Color.White
        isFocused -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val textColor = if (isSelected) {
        androidx.compose.ui.graphics.Color(0xFF0A1628)
    } else {
        androidx.compose.ui.graphics.Color.White
    }
    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
        shape = androidx.tv.material3.CardDefaults.shape(pillShape),
        colors = androidx.tv.material3.CardDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = containerColor
        ),
        border = androidx.tv.material3.CardDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isSelected) {
                        androidx.compose.ui.graphics.Color.Transparent
                    } else {
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f)
                    }
                ),
                shape = pillShape
            )
        ),
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1f)
    ) {
        androidx.tv.material3.Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isSelected) {
                androidx.compose.ui.text.font.FontWeight.SemiBold
            } else {
                androidx.compose.ui.text.font.FontWeight.Medium
            }
        )
    }
}

// ── Pill content dispatcher ──────────────────────────────────────────────────

@Composable
private fun RenderPillContent(
    pill: SettingsPillSpec,
    initialFocusRequester: FocusRequester?,
    isEssentialMode: Boolean,
    pluginsEnabled: Boolean,
    experienceModeViewModel: ExperienceModeSettingsViewModel,
    integrationSection: IntegrationSettingsSection,
    onSelectIntegrationSection: (IntegrationSettingsSection) -> Unit,
    integrationHubFocusRequester: FocusRequester,
    integrationTmdbFocusRequester: FocusRequester,
    integrationMdbListFocusRequester: FocusRequester,
    integrationAnimeSkipFocusRequester: FocusRequester,
    autoFocusEnabled: Boolean,
    onNavigateToTrakt: () -> Unit,
    onNavigateToAddons: () -> Unit,
    onNavigateToAuthQrSignIn: () -> Unit,
    onNavigateToManageProfiles: () -> Unit,
    onNavigateToSupportersContributors: () -> Unit
) {
    when (pill.id) {
        "appearance" -> ThemeSettingsContent(initialFocusRequester = initialFocusRequester)
        "layout" -> LayoutSettingsContent(
            initialFocusRequester = initialFocusRequester,
            essentialMode = isEssentialMode
        )
        "layout_rows" -> NewLayoutSettingsContent(
            initialFocusRequester = initialFocusRequester
        )
        "display" -> SettingsComingSoonContent(
            title = "Display",
            subtitle = "Display options (resolution, refresh rate, scaling) will live here."
        )
        "addons" -> SettingsLaunchContent(
            title = "Addons",
            subtitle = "Manage installed addons and discover new ones.",
            buttonLabel = "Open Addon Manager",
            initialFocusRequester = initialFocusRequester,
            onLaunch = onNavigateToAddons
        )
        "plugins" -> if (pluginsEnabled && !isEssentialMode) {
            PluginsSettingsContent()
        } else {
            SettingsComingSoonContent(
                title = "Plugins",
                subtitle = "Plugins are disabled in this build / mode."
            )
        }
        "collections" -> SettingsComingSoonContent(
            title = "Collections",
            subtitle = "Manage your saved collections here."
        )
        "account" -> AccountSettingsInline(onNavigateToAuthQrSignIn = onNavigateToAuthQrSignIn)
        "profiles" -> ProfileSettingsContent(onManageProfiles = onNavigateToManageProfiles)
        "trakt" -> SettingsLaunchContent(
            title = "Trakt",
            subtitle = "Sign in and manage your Trakt sync settings.",
            buttonLabel = "Open Trakt Settings",
            initialFocusRequester = initialFocusRequester,
            onLaunch = onNavigateToTrakt
        )
        "integrations" -> IntegrationSettingsContent(
            selectedSection = integrationSection,
            onSelectSection = onSelectIntegrationSection,
            initialFocusRequester = initialFocusRequester,
            hubFocusRequester = integrationHubFocusRequester,
            tmdbFocusRequester = integrationTmdbFocusRequester,
            mdbListFocusRequester = integrationMdbListFocusRequester,
            animeSkipFocusRequester = integrationAnimeSkipFocusRequester,
            autoFocusEnabled = autoFocusEnabled
        )
        "playback" -> if (isEssentialMode) {
            EssentialPlaybackSettingsContent(initialFocusRequester = initialFocusRequester)
        } else {
            PlaybackSettingsContent(initialFocusRequester = initialFocusRequester)
        }
        "link_resolving" -> SettingsComingSoonContent(
            title = "Link Resolving",
            subtitle = "Configure how stream links are resolved."
        )
        "link_filtering" -> SettingsComingSoonContent(
            title = "Link Filtering",
            subtitle = "Filter streams by quality, language, size, and source."
        )
        "advanced" -> if (isEssentialMode) {
            EssentialAdvancedSettingsContent(
                experienceModeViewModel = experienceModeViewModel,
                initialFocusRequester = initialFocusRequester
            )
        } else {
            AdvancedSettingsContent(
                initialFocusRequester = initialFocusRequester,
                experienceModeViewModel = experienceModeViewModel
            )
        }
        "about" -> AboutSettingsContent(
            onNavigateToSupportersContributors = onNavigateToSupportersContributors,
            initialFocusRequester = initialFocusRequester
        )
    }
}

// ── Placeholder + launch panels ──────────────────────────────────────────────

@Composable
private fun SettingsComingSoonContent(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(title = title, subtitle = subtitle)
        SettingsGroupCard(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.tv.material3.Text(
                    text = "Coming soon",
                    style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                    color = NuvioColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsLaunchContent(
    title: String,
    subtitle: String,
    buttonLabel: String,
    initialFocusRequester: FocusRequester?,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsDetailHeader(title = title, subtitle = subtitle)
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = buttonLabel,
                subtitle = "Tap to open the full screen.",
                onClick = onLaunch,
                modifier = if (initialFocusRequester != null) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}
