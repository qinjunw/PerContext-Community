package com.percontext.app.feature.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.percontext.app.R
import com.percontext.app.core.media.PlaybackState
import com.percontext.app.core.util.formatDuration
import com.percontext.app.core.util.formatFileSize
import com.percontext.app.core.util.formatRecordDate
import com.percontext.app.domain.model.RecordStatus
import com.percontext.app.domain.model.TranscriptStatus
import com.percontext.app.domain.model.VoiceRecord
import com.percontext.app.ui.theme.HairlineColor
import com.percontext.app.ui.theme.ActionSurfaceColor
import com.percontext.app.ui.theme.AcrylicEdgeColor
import com.percontext.app.ui.theme.InkColor
import com.percontext.app.ui.theme.LocalColor
import com.percontext.app.ui.theme.MutedColor
import com.percontext.app.ui.theme.SurfaceColor

@Composable
internal fun RecordingCard(
    record: VoiceRecord,
    playback: PlaybackState,
    onTogglePlayback: () -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onRequestTranscription: () -> Unit,
    onRequestDelete: () -> Unit,
    onOpenTranscript: () -> Unit,
) {
    val selected = playback.recordId == record.id
    val duration = if (selected && playback.durationMillis > 0L) {
        playback.durationMillis
    } else {
        record.durationMillis
    }
    val progress = if (selected && duration > 0L) {
        (playback.positionMillis.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val transcriptAvailable = record.transcriptStatus == TranscriptStatus.SUCCEEDED &&
        !record.transcriptText.isNullOrBlank()
    val transcriptionRunning = record.transcriptStatus == TranscriptStatus.QUEUED ||
        record.transcriptStatus == TranscriptStatus.PROCESSING
    val transcriptionRequestable = record.transcriptStatus == TranscriptStatus.NOT_REQUESTED ||
        record.transcriptStatus == TranscriptStatus.FAILED
    val recordingLabel = if (record.status == RecordStatus.CORRUPTED) {
        "文件可能损坏"
    } else {
        "原始录音"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording_card_${record.id}")
            .then(
                if (transcriptAvailable) {
                    Modifier.clickable(
                        onClickLabel = "查看转写内容",
                        onClick = onOpenTranscript,
                    )
                } else {
                    Modifier
                },
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, AcrylicEdgeColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier
                        .size(48.dp)
                        .background(ActionSurfaceColor, CircleShape),
                ) {
                    Icon(
                        painter = if (selected && playback.isPlaying) {
                            painterResource(R.drawable.ic_pause)
                        } else {
                            rememberVectorPainter(Icons.Default.PlayArrow)
                        },
                        contentDescription = if (selected && playback.isPlaying) "暂停" else "播放",
                        tint = InkColor,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatRecordDate(record.createdAtMillis),
                        color = InkColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "$recordingLabel · ${formatDuration(record.durationMillis)} · " +
                            formatFileSize(record.fileSizeBytes),
                        color = if (record.status == RecordStatus.CORRUPTED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MutedColor
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(6.dp))
                IconButton(
                    onClick = onRequestTranscription,
                    enabled = transcriptionRequestable,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (transcriptionRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .semantics { contentDescription = "转写中" },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_transcribe),
                            contentDescription = if (record.transcriptStatus == TranscriptStatus.SUCCEEDED) {
                                "转写已完成"
                            } else {
                                "转写录音"
                            },
                            tint = if (record.transcriptStatus == TranscriptStatus.SUCCEEDED) {
                                MutedColor
                            } else {
                                LocalColor
                            },
                        )
                    }
                }
                Spacer(Modifier.size(4.dp))
                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除录音",
                        tint = MutedColor,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = progress,
                    onValueChange = onSeekPlayback,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(playback.positionMillis),
                        color = MutedColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = formatDuration(duration),
                        color = MutedColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                playback.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            TranscriptionStatusMessage(record)
        }
    }
}

@Composable
private fun TranscriptionStatusMessage(
    record: VoiceRecord,
) {
    when (record.transcriptStatus) {
        TranscriptStatus.NOT_REQUESTED -> Unit

        TranscriptStatus.QUEUED,
        TranscriptStatus.PROCESSING,
        -> Unit

        TranscriptStatus.SUCCEEDED -> Unit

        TranscriptStatus.FAILED -> {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "转写失败，录音已保留",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun TranscriptDialog(
    record: VoiceRecord,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("transcript_backdrop")
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
                    .heightIn(min = 220.dp, max = 620.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .testTag("transcript_dialog"),
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
                                text = "转写内容",
                                color = InkColor,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatRecordDate(record.createdAtMillis),
                                color = MutedColor,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭转写内容",
                                tint = MutedColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = HairlineColor)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = record.transcriptText.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .testTag("transcript_content")
                            .padding(end = 8.dp),
                        color = InkColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyTimeline() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(HairlineColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = MutedColor,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "还没有记录",
            color = InkColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "第一段录音会安全保存在这里",
            color = MutedColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun DeleteRecordingDialog(
    record: VoiceRecord,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这段原始录音？") },
        text = {
            Text(
                "将删除 ${formatRecordDate(record.createdAtMillis)} 的音频文件和本地记录。" +
                    "此操作无法撤销。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
