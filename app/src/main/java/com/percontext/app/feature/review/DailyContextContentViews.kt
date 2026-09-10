package com.percontext.app.feature.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextContent
import com.percontext.app.domain.dailycontext.DailyContextStatus
import com.percontext.app.ui.theme.HairlineColor
import com.percontext.app.ui.theme.InkColor
import com.percontext.app.ui.theme.LocalColor
import com.percontext.app.ui.theme.MutedColor
import com.percontext.app.ui.theme.SurfaceColor

@Composable
internal fun DailyContextPreview(
    context: DailyContext,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
) {
    val content = requireNotNull(context.content)
    val refreshing = context.status == DailyContextStatus.QUEUED ||
        context.status == DailyContextStatus.PROCESSING
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            refreshing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在刷新…", color = MutedColor, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
            context.status == DailyContextStatus.FAILED -> {
                Text(
                    text = "刷新失败，显示上次回顾",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
            else -> Unit
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .testTag("review_context_preview"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = content.title,
                    modifier = Modifier.weight(1f),
                    color = InkColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("›", color = MutedColor, fontSize = 24.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = content.summary,
                color = InkColor,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            DailyContextSectionPreview(content)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    refreshing -> "刷新中…"
                    context.status == DailyContextStatus.FAILED -> "重试刷新"
                    else -> "刷新回顾"
                },
            )
        }
    }
}

@Composable
private fun DailyContextSectionPreview(content: DailyContextContent) {
    val section = dailyContextSections(content).firstOrNull() ?: return
    Spacer(Modifier.height(14.dp))
    Text(
        text = section.first,
        color = LocalColor,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(5.dp))
    section.second.take(2).forEach { item ->
        Text(
            text = "· $item",
            color = InkColor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DailyContextDialog(
    dayKey: String,
    content: DailyContextContent,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("daily_context_backdrop")
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(min = 260.dp, max = 640.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .testTag("daily_context_dialog"),
                shape = RoundedCornerShape(26.dp),
                color = SurfaceColor,
                tonalElevation = 6.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 22.dp,
                        top = 18.dp,
                        end = 14.dp,
                        bottom = 22.dp,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当天回顾",
                                color = InkColor,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = dayKey.toChineseDate(),
                                color = MutedColor,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭当天回顾",
                                tint = MutedColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = HairlineColor)
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .testTag("daily_context_dialog_content")
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = content.title,
                            color = InkColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = content.summary,
                            color = InkColor,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        DailyContextSections(content)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyContextSections(content: DailyContextContent) {
    dailyContextSections(content).forEach { (title, items) ->
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            color = LocalColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("·", color = MutedColor)
                Text(item, color = InkColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun dailyContextSections(content: DailyContextContent) = listOf(
    "主题" to content.topics,
    "想法" to content.ideas,
    "问题" to content.questions,
    "决定" to content.decisions,
    "待办" to content.todos,
).filter { it.second.isNotEmpty() }

@Composable
internal fun RegenerationConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新生成回顾？") },
        text = {
            Text("当天录音的可用转写与现有回顾不一致。重新生成会覆盖现有回顾，请确认已经做好备份。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认并重新生成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
