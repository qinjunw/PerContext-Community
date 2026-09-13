package com.percontext.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.percontext.app.domain.appearance.AppTheme
import com.percontext.app.domain.appearance.AppearanceSettings
import com.percontext.app.domain.appearance.ThemeMode

@Composable
fun AppearanceSettingsCard(
    appearance: AppearanceSettings,
    onSelectTheme: (AppTheme) -> Unit,
    onSelectMode: (ThemeMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "外观",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            AppearanceDropdown(
                label = "主题",
                options = AppTheme.entries,
                selectedOption = appearance.theme,
                optionLabel = { theme ->
                    when (theme) {
                        AppTheme.SKY -> "晴空奶白"
                        AppTheme.SEA_SALT -> "奶油海盐"
                        AppTheme.LAVENDER -> "雾紫蓝"
                    }
                },
                onSelect = onSelectTheme,
            )
            AppearanceDropdown(
                label = "显示模式",
                options = ThemeMode.entries,
                selectedOption = appearance.mode,
                optionLabel = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "日间"
                        ThemeMode.DARK -> "夜间"
                    }
                },
                onSelect = onSelectMode,
            )
            Text(
                text = "选择后立即生效并保存。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> AppearanceDropdown(
    label: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = optionLabel(selectedOption),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = { RadioButton(selected = isSelected, onClick = null) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
