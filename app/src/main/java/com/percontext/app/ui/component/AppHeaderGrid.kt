package com.percontext.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.percontext.app.ui.theme.InkColor

@Composable
internal fun AppHeaderGrid(
    titleContent: @Composable ColumnScope.() -> Unit,
    actionContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("app_header_grid"),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.Top,
            content = titleContent,
        )
        Row(
            modifier = Modifier
                .width(156.dp)
                .fillMaxHeight()
                .testTag("app_header_actions"),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = actionContent,
        )
    }
}

@Composable
internal fun HeaderSettingsButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag("app_header_settings"),
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "打开设置",
            tint = InkColor.copy(alpha = 0.78f),
        )
    }
}
