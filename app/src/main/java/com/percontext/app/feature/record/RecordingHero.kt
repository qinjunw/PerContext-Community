package com.percontext.app.feature.record
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.percontext.app.R
import com.percontext.app.core.util.formatDuration
import com.percontext.app.domain.recording.RecordingSession
import com.percontext.app.ui.theme.AcrylicBackColor
import com.percontext.app.ui.theme.AcrylicEdgeColor
import com.percontext.app.ui.theme.InkColor
import com.percontext.app.ui.theme.SurfaceColor
import com.percontext.app.ui.theme.PerContextTheme
import kotlinx.coroutines.delay

@Composable
internal fun RecordingHero(
    session: RecordingSession,
    inputLevel: Float,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val colors = PerContextTheme.colors
    var nowMillis by remember(session) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session) {
        if (session is RecordingSession.Recording) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(250L)
            }
        }
    }

    val elapsedMillis = if (session is RecordingSession.Recording) {
        nowMillis - session.startedAtMillis
    } else {
        0L
    }
    val buttonEnabled = session is RecordingSession.Idle ||
        session is RecordingSession.Failed ||
        session is RecordingSession.Recording
    val isRecording = session is RecordingSession.Recording

    val panelShape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(304.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = 6.dp, y = 8.dp)
                .clip(panelShape)
                .background(AcrylicBackColor)
                .border(1.dp, AcrylicEdgeColor, panelShape),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(294.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = panelShape,
                    ambientColor = AcrylicBackColor,
                    spotColor = AcrylicBackColor,
                ),
            shape = panelShape,
            colors = CardDefaults.cardColors(containerColor = colors.hero),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, AcrylicEdgeColor),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPath(
                        path = Path().apply {
                            moveTo(0f, size.height * 0.86f)
                            cubicTo(
                                size.width * 0.32f, size.height * 0.82f,
                                size.width * 0.64f, size.height * 0.60f,
                                size.width, size.height * 0.60f,
                            )
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        },
                        color = colors.heroWave,
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(0f, size.height * 0.82f)
                            cubicTo(
                                size.width * 0.34f, size.height * 0.80f,
                                size.width * 0.72f, size.height * 0.98f,
                                size.width, size.height,
                            )
                            lineTo(0f, size.height)
                            close()
                        },
                        color = colors.heroWaveAlt,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = formatDuration(elapsedMillis),
                        color = colors.heroInk,
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = session.statusLabel(),
                        color = if (session is RecordingSession.Failed) {
                            if (colors.hero == colors.surface) {
                                MaterialTheme.colorScheme.error
                            } else {
                                colors.heroInk
                            }
                        } else {
                            colors.heroMuted
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.size(124.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isRecording) {
                            RecordingRipple(inputLevel)
                        }
                        Button(
                            onClick = if (isRecording) onStopRecording else onStartRecording,
                            modifier = Modifier
                                .size(124.dp)
                                .shadow(
                                    elevation = 18.dp,
                                    shape = CircleShape,
                                    ambientColor = if (isRecording) {
                                        InkColor.copy(alpha = 0.24f)
                                    } else {
                                        colors.heroAction.copy(alpha = 0.24f)
                                    },
                                    spotColor = if (isRecording) {
                                        InkColor.copy(alpha = 0.24f)
                                    } else {
                                        colors.heroAction.copy(alpha = 0.24f)
                                    },
                                ),
                            enabled = buttonEnabled,
                            shape = CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) InkColor else colors.heroAction,
                                contentColor = if (isRecording) SurfaceColor else colors.onHeroAction,
                                disabledContainerColor = colors.disabledSurface,
                                disabledContentColor = colors.muted,
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                            ),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(
                                        if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic,
                                    ),
                                    contentDescription = if (isRecording) "停止录音" else "开始录音",
                                    modifier = Modifier.size(34.dp),
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = if (isRecording) "停止" else "记录",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRipple(inputLevel: Float) {
    val rippleColor = PerContextTheme.colors.heroAction
    val visibleLevel by animateFloatAsState(
        targetValue = inputLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "recordingInputLevel",
    )
    val transition = rememberInfiniteTransition(label = "recordingRipple")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
        ),
        label = "recordingRipplePhase",
    )

    Canvas(
        modifier = Modifier
            .requiredSize(220.dp)
            .testTag("recording_ripple"),
    ) {
        val buttonRadius = 62.dp.toPx()
        val travel = (6.dp + 38.dp * visibleLevel).toPx()
        val peakAlpha = 0.08f + 0.34f * visibleLevel
        val strokeWidth = (1.5.dp + 1.5.dp * visibleLevel).toPx()
        repeat(3) { index ->
            val wavePhase = (phase + index / 3f) % 1f
            drawCircle(
                color = rippleColor.copy(
                    alpha = (1f - wavePhase) * peakAlpha,
                ),
                radius = buttonRadius + travel * wavePhase,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private fun RecordingSession.statusLabel(): String = when (this) {
    RecordingSession.Idle -> "准备记录"
    RecordingSession.Starting -> "准备记录"
    is RecordingSession.Recording -> "正在录音"
    RecordingSession.Stopping -> "准备记录"
    is RecordingSession.Failed -> reason
}
