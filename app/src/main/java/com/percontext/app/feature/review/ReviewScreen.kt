package com.percontext.app.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.percontext.app.domain.dailycontext.DailyContext
import com.percontext.app.domain.dailycontext.DailyContextStatus
import com.percontext.app.ui.component.AppHeaderGrid
import com.percontext.app.ui.component.HeaderSettingsButton
import com.percontext.app.ui.theme.CanvasColor
import com.percontext.app.ui.theme.HairlineColor
import com.percontext.app.ui.theme.InkColor
import com.percontext.app.ui.theme.LocalColor
import com.percontext.app.ui.theme.MutedColor
import com.percontext.app.ui.theme.RecordColor
import com.percontext.app.ui.theme.SurfaceColor

@Composable
fun ReviewRoute(
    viewModel: ReviewViewModel,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val regenerationConfirmationDayKey by
        viewModel.regenerationConfirmationDayKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect(snackbarHostState::showSnackbar)
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    ReviewScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectDay = viewModel::selectDay,
        onRequestDailyContext = viewModel::requestDailyContext,
        onOpenSettings = onOpenSettings,
        showRegenerationConfirmation = regenerationConfirmationDayKey == state.selectedDayKey,
        onConfirmRegeneration = viewModel::confirmRegeneration,
        onDismissRegenerationConfirmation = viewModel::dismissRegenerationConfirmation,
    )
}

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    snackbarHostState: SnackbarHostState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (String) -> Unit,
    onRequestDailyContext: () -> Unit,
    onOpenSettings: () -> Unit,
    showRegenerationConfirmation: Boolean = false,
    onConfirmRegeneration: () -> Unit = {},
    onDismissRegenerationConfirmation: () -> Unit = {},
) {
    var recordingsOpen by remember(state.selectedDayKey) { mutableStateOf(false) }
    var contextOpen by remember(state.selectedDayKey) { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 10.dp)
                .testTag("review_page"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReviewHeader(onOpenSettings)
            CalendarCard(
                state = state,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDay = onSelectDay,
            )
            SelectedDayCard(
                dayKey = state.selectedDayKey,
                recordings = state.selectedRecordings,
                transcriptCount = state.selectedTranscriptCount,
                context = state.selectedContext,
                onOpenRecordings = { recordingsOpen = true },
                onOpenContext = { contextOpen = true },
                onRequestDailyContext = onRequestDailyContext,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (recordingsOpen) {
        DayRecordingListDialog(
            dayKey = state.selectedDayKey,
            recordings = state.selectedRecordings,
            zoneId = state.zoneId,
            onDismiss = { recordingsOpen = false },
        )
    }
    state.selectedContext?.content?.let { content ->
        if (contextOpen) {
            DailyContextDialog(
                dayKey = state.selectedDayKey,
                content = content,
                onDismiss = { contextOpen = false },
            )
        }
    }
    if (showRegenerationConfirmation) {
        RegenerationConfirmationDialog(
            onConfirm = onConfirmRegeneration,
            onDismiss = onDismissRegenerationConfirmation,
        )
    }
}

@Composable
private fun ReviewHeader(onOpenSettings: () -> Unit) {
    AppHeaderGrid(
        titleContent = {
            Text(
                text = "回顾",
                color = InkColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        actionContent = {
            HeaderSettingsButton(onClick = onOpenSettings)
        },
    )
}

@Composable
private fun CalendarCard(
    state: ReviewUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPreviousMonth) {
                    Text("‹", fontSize = 28.sp, color = MutedColor)
                }
                Text(
                    text = state.month.title,
                    color = InkColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onNextMonth) {
                    Text("›", fontSize = 28.sp, color = MutedColor)
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                    Text(
                        text = weekday,
                        modifier = Modifier.weight(1f),
                        color = MutedColor,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            state.days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDay(
                            day = day,
                            selected = day.cell.dayKey == state.selectedDayKey,
                            today = day.cell.dayKey == state.todayDayKey,
                            onSelect = { day.cell.dayKey?.let(onSelectDay) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CalendarLegend(LocalColor, "已有回顾")
                CalendarLegend(RecordColor, "有录音")
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: ReviewCalendarDay,
    selected: Boolean,
    today: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayNumber = day.cell.day
    Box(
        modifier = modifier
            .height(38.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(13.dp))
            .then(
                if (selected) Modifier.background(InkColor)
                else if (today) Modifier.border(1.dp, HairlineColor, RoundedCornerShape(13.dp))
                else Modifier,
            )
            .then(if (dayNumber != null) Modifier.clickable(onClick = onSelect) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (dayNumber != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayNumber.toString(),
                    color = if (selected) Color.White else InkColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(
                            color = when {
                                day.context?.content != null -> LocalColor
                                day.recordingCount > 0 -> RecordColor
                                else -> Color.Transparent
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, color = MutedColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SelectedDayCard(
    dayKey: String,
    recordings: List<ReviewRecordingSummary>,
    transcriptCount: Int,
    context: DailyContext?,
    onOpenRecordings: () -> Unit,
    onOpenContext: () -> Unit,
    onRequestDailyContext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenRecordings)
                    .testTag("selected_day_recordings")
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dayKey.toChineseDate(),
                        color = InkColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${recordings.size} 段录音",
                        color = MutedColor,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = "›",
                    color = MutedColor,
                    fontSize = 28.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = HairlineColor)
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    context?.content != null -> DailyContextPreview(
                        context = context,
                        onOpen = onOpenContext,
                        onRefresh = onRequestDailyContext,
                    )
                    context?.status == DailyContextStatus.QUEUED ||
                        context?.status == DailyContextStatus.PROCESSING -> GeneratingContent(context)
                    transcriptCount == 0 -> EmptyDayContent()
                    else -> GenerateContent(
                        failed = context?.status == DailyContextStatus.FAILED,
                        onGenerate = onRequestDailyContext,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDayContent() {
    Button(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("生成回顾")
    }
}

@Composable
private fun GeneratingContent(context: DailyContext) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(
            text = if (context.status == DailyContextStatus.QUEUED) "等待生成…" else "生成中…",
            color = MutedColor,
        )
    }
}

@Composable
private fun GenerateContent(
    failed: Boolean,
    onGenerate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (failed) {
            Text(
                text = "生成失败",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
        }
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text(if (failed) "重新生成" else "生成回顾")
        }
    }
}

internal fun String.toChineseDate(): String {
    val parts = split('-')
    return if (parts.size == 3) {
        "${parts[0]} 年 ${parts[1].toIntOrNull() ?: parts[1]} 月 " +
            "${parts[2].toIntOrNull() ?: parts[2]} 日"
    } else {
        this
    }
}
