package io.github.alaabataineh92.termuxvmfixer.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.alaabataineh92.termuxvmfixer.R
import io.github.alaabataineh92.termuxvmfixer.core.common.openLink
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.ScreenScaffold
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.SectionCard
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.SectionHeader
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.component.StackedValue
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Dimens
import io.github.alaabataineh92.termuxvmfixer.core.designsystem.theme.Spacing
import io.github.alaabataineh92.termuxvmfixer.core.device.ThemePreference
import io.github.alaabataineh92.termuxvmfixer.core.model.Credits
import io.github.alaabataineh92.termuxvmfixer.core.model.TermuxPaths
import io.github.alaabataineh92.termuxvmfixer.core.model.ThemeMode
import io.github.alaabataineh92.termuxvmfixer.data.privilege.adbdirect.AdbIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showingLicenses by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.vmfix_settings_title),
        onBack = onBack,
        backLabel = stringResource(R.string.vmfix_common_back),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { SectionTitle(stringResource(R.string.vmfix_settings_section_appearance)) }

            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.vmfix_settings_appearance_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ThemeModeSelector(
                        selected = state.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.vmfix_settings_section_storage)) }

            item {
                SettingsCard {
                    StackedValue(
                        stringResource(R.string.vmfix_settings_workspace),
                        state.workspacePath,
                    )
                    Text(
                        text = stringResource(
                            R.string.vmfix_settings_workspace_body,
                            state.requiredStorageMb,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    StackedValue(
                        stringResource(R.string.vmfix_settings_free_space),
                        stringResource(
                            R.string.vmfix_settings_free_space_value,
                            state.availableStorageMb,
                        ),
                    )
                    TextButton(onClick = viewModel::clearWorkspace) {
                        Text(stringResource(R.string.vmfix_settings_clear_now))
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.vmfix_settings_section_adb)) }

            item {
                SettingsCard {
                    Text(
                        text = if (state.hasAdbKey) {
                            stringResource(R.string.vmfix_settings_adb_has_key)
                        } else {
                            stringResource(R.string.vmfix_settings_adb_no_key)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.hasAdbKey) {
                        TextButton(onClick = viewModel::resetAdbKey) {
                            Text(stringResource(R.string.vmfix_settings_reset_key))
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.vmfix_settings_section_diagnostics)) }

            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.vmfix_settings_diagnostics_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onOpenLog) {
                        Text(stringResource(R.string.vmfix_settings_open_log))
                    }
                }
            }

            item {
                SettingsCard {
                    StackedValue(
                        stringResource(R.string.vmfix_settings_label_package),
                        TermuxPaths.PACKAGE,
                    )
                    StackedValue(
                        stringResource(R.string.vmfix_settings_label_prefix),
                        TermuxPaths.PREFIX,
                    )
                    StackedValue(
                        stringResource(R.string.vmfix_settings_label_staging),
                        TermuxPaths.WORK_DIR,
                    )
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.vmfix_settings_reporting_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.vmfix_settings_section_about)) }

            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.vmfix_settings_about_body_1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.vmfix_settings_about_body_2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.vmfix_settings_licenses_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showingLicenses = true }) {
                        Text(stringResource(R.string.vmfix_settings_open_licenses))
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.vmfix_settings_section_credits)) }

            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.vmfix_settings_credits_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = Credits.AUTHOR,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    CreditLink(
                        text = Credits.EMAIL,
                        onClick = { openLink(context, "mailto:${Credits.EMAIL}") },
                    )
                    CreditLink(
                        text = Credits.SOURCE_LABEL,
                        onClick = { openLink(context, Credits.SOURCE_URL) },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vmfix_settings_credits_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showingLicenses) {
        LicensesDialog(onDismiss = { showingLicenses = false })
    }
}

@Composable
private fun CreditLink(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = Spacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val licenses = remember(resources) {
        resources.openRawResource(R.raw.licenses).bufferedReader().use { it.readText() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vmfix_licenses_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(
                        text = licenses,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.vmfix_common_close))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {

    SectionHeader(title = text)
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.vmfix_settings_theme_system,
        ThemeMode.LIGHT to R.string.vmfix_settings_theme_light,
        ThemeMode.DARK to R.string.vmfix_settings_theme_dark,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { (mode, label) ->
            val isSelected = selected == mode

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(mode) },
                    )
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)

                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {

    SectionCard(content = content)
}

data class SettingsUiState(
    val workspacePath: String = "",
    val availableStorageMb: Long = 0,
    val requiredStorageMb: Long = 0,
    val hasAdbKey: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workspace: io.github.alaabataineh92.termuxvmfixer.core.device.WorkspaceManager,
    private val themePreference: ThemePreference,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()

        viewModelScope.launch {
            themePreference.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreference.set(mode) }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    workspacePath = context.filesDir.resolve("fix-work").absolutePath,
                    availableStorageMb = workspace.availableBytes() / 1024 / 1024,
                    requiredStorageMb = workspace.requiredBytes / 1024 / 1024,
                    hasAdbKey = AdbIdentity(context).exists(),
                )
            }
        }
    }

    fun clearWorkspace() {
        viewModelScope.launch {
            workspace.clear()
            refresh()
        }
    }

    fun resetAdbKey() {
        viewModelScope.launch {
            runCatching { AdbIdentity(context).reset() }
            refresh()
        }
    }
}
