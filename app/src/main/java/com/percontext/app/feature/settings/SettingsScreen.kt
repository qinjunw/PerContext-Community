package com.percontext.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.percontext.app.BuildConfig
import com.percontext.app.data.provider.SenseVoiceModelContract
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.ThemeMode
import com.percontext.app.domain.llm.LlmProviderPreset
import com.percontext.app.domain.llm.LlmProviderPresets
import com.percontext.app.ui.theme.CanvasColor
import com.percontext.app.ui.theme.HairlineColor
import com.percontext.app.ui.theme.InkColor
import com.percontext.app.ui.theme.MutedColor
import com.percontext.app.ui.theme.SurfaceColor

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    appearanceViewModel: AppearanceViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
    }
    LaunchedEffect(appearanceViewModel) {
        appearanceViewModel.messages.collect(snackbarHostState::showSnackbar)
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSelectProvider = viewModel::selectProvider,
        onApiKeyChange = viewModel::updateApiKey,
        onToggleApiKeyVisibility = viewModel::toggleApiKeyVisibility,
        onClearApiKey = viewModel::clearApiKey,
        onSave = viewModel::save,
        onBaseUrlChange = viewModel::updateBaseUrl,
        onModelChange = viewModel::updateModel,
        appearance = appearance ?: AppearanceSettings(),
        onSelectTheme = appearanceViewModel::selectTheme,
        onSelectMode = appearanceViewModel::selectMode,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onClearApiKey: () -> Unit,
    onSave: () -> Unit,
    onBaseUrlChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit = {},
    appearance: AppearanceSettings = AppearanceSettings(),
    onSelectTheme: (AppTheme) -> Unit = {},
    onSelectMode: (ThemeMode) -> Unit = {},
) {
    var legalNoticesOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { SettingsHeader(onBack) }
            item {
                AppearanceSettingsCard(
                    appearance = appearance,
                    onSelectTheme = onSelectTheme,
                    onSelectMode = onSelectMode,
                )
            }
            item {
                ProviderSettingsCard(
                    state = state,
                    onSelectProvider = onSelectProvider,
                    onApiKeyChange = onApiKeyChange,
                    onToggleApiKeyVisibility = onToggleApiKeyVisibility,
                    onClearApiKey = onClearApiKey,
                    onSave = onSave,
                    onBaseUrlChange = onBaseUrlChange,
                    onModelChange = onModelChange,
                )
            }
            item { AboutCard(onOpenLegalNotices = { legalNoticesOpen = true }) }
        }
    }
    if (legalNoticesOpen) {
        SenseVoiceLegalNoticesDialog(onDismiss = { legalNoticesOpen = false })
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = "设置",
            modifier = Modifier.padding(start = 4.dp),
            color = InkColor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProviderSettingsCard(
    state: SettingsUiState,
    onSelectProvider: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onClearApiKey: () -> Unit,
    onSave: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    val selectedPreset = state.presets.firstOrNull { it.id == state.selectedProviderId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "模型服务",
                color = InkColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))
            ProviderDropdown(
                presets = state.presets,
                selected = selectedPreset,
                onSelect = onSelectProvider,
            )
            Spacer(Modifier.height(14.dp))
            if (state.selectedProviderId == LlmProviderPresets.custom.id) {
                OutlinedTextField(
                    value = state.baseUrlDraft,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    supportingText = { Text("使用 HTTPS；自动补全 /chat/completions。修改地址后需重新输入 API Key。") },
                    singleLine = true,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = state.modelDraft,
                    onValueChange = onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                    label = { Text("模型名称") },
                    placeholder = { Text("填写服务提供方的模型 ID") },
                    singleLine = true,
                )
                Spacer(Modifier.height(14.dp))
            }
            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                placeholder = {
                    Text(if (state.hasSavedApiKey) "已保存" else "请输入 API Key")
                },
                singleLine = true,
                enabled = !state.isSaving,
                visualTransformation = if (state.isApiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = onToggleApiKeyVisibility,
                        enabled = !state.isSaving &&
                            (state.hasSavedApiKey || state.apiKeyDraft.isNotEmpty()),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(if (state.isApiKeyVisible) "隐藏" else "显示")
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onClearApiKey,
                    enabled = state.hasSavedApiKey && !state.isSaving,
                ) {
                    Text("清除本地 Key")
                }
            }
            selectedPreset?.takeUnless { it.id == LlmProviderPresets.custom.id }?.let { preset ->
                ProviderPresetDetails(preset)
            }
            Text(
                "语音转文字由手机内置模型离线完成。仅在你主动生成回顾时，当天转写才会发送到所选模型服务。",
                color = MutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存设置")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    presets: List<LlmProviderPreset>,
    selected: LlmProviderPreset?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.displayName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text("服务商") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    onClick = {
                        onSelect(preset.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderPresetDetails(preset: LlmProviderPreset) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderPresetRow("模型", preset.defaultModel)
        ProviderPresetRow("API 地址", preset.baseUrl)
    }
}

@Composable
private fun ProviderPresetRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = MutedColor, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = InkColor,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AboutCard(onOpenLegalNotices: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "关于 PerContext Community",
                color = InkColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = HairlineColor)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("版本", color = MutedColor)
                Text(BuildConfig.VERSION_NAME, color = InkColor)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = HairlineColor)
            TextButton(
                onClick = onOpenLegalNotices,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("开源许可")
            }
        }
    }
}

@Composable
private fun SenseVoiceLegalNoticesDialog(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val documents = remember(assets) {
        SenseVoiceLegalDocuments(
            notice = assets.open(
                "${SenseVoiceModelContract.ASSET_DIRECTORY}/" +
                    SenseVoiceModelContract.NOTICE_FILE_NAME,
            ).bufferedReader().use { it.readText() },
            license = assets.open(
                "${SenseVoiceModelContract.ASSET_DIRECTORY}/" +
                    SenseVoiceModelContract.MODEL_LICENSE_FILE_NAME,
            ).bufferedReader().use { it.readText() },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SenseVoiceSmall 开源许可") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(documents.notice, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = HairlineColor)
                Text(documents.license, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

private data class SenseVoiceLegalDocuments(
    val notice: String,
    val license: String,
)
