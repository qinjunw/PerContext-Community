package com.percontext.app.feature.review

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.percontext.app.core.util.formatDuration
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.ui.theme.HairlineColor
import com.percontext.app.ui.theme.InkColor
import com.percontext.app.ui.theme.LocalColor
import com.percontext.app.ui.theme.MutedColor
import com.percontext.app.ui.theme.RecordColor
import com.percontext.app.ui.theme.SurfaceColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun DayRecordingListDialog(
    dayKey: String,
    recordings: List<ReviewRecordingSummary>,
    zoneId: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("day_recordings_backdrop")
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
                    .heightIn(min = 220.dp, max = 580.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .testTag("day_recordings_dialog"),
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
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭当天录音",
                                tint = MutedColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = HairlineColor)
                    if (recordings.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("当天没有录音", color = MutedColor)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("day_recordings_list"),
                        ) {
                            items(recordings, key = ReviewRecordingSummary::recordId) { recording ->
                                DayRecordingRow(recording, zoneId)
                                HorizontalDivider(color = HairlineColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRecordingRow(recording: ReviewRecordingSummary, zoneId: String) {
    val (statusLabel, statusColor) = when (recording.transcriptStatus) {
        TranscriptStatus.SUCCEEDED -> "已转写" to LocalColor
        TranscriptStatus.QUEUED,
        TranscriptStatus.PROCESSING,
        -> "转写中" to RecordColor
        TranscriptStatus.FAILED -> "转写失败" to MaterialTheme.colorScheme.error
        TranscriptStatus.NOT_REQUESTED -> "未转写" to MutedColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_recording_${recording.recordId}")
            .padding(end = 8.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatReviewRecordingTime(recording.recordedAtMillis, zoneId),
                color = InkColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "时长 ${formatDuration(recording.durationMillis)}",
                color = MutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = statusLabel,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(statusColor.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = statusColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun formatReviewRecordingTime(epochMillis: Long, zoneId: String): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone(zoneId)
    }.format(Date(epochMillis))
