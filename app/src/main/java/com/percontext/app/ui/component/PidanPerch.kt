package com.percontext.app.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.percontext.app.R
import com.percontext.app.ui.theme.PerContextTheme

@Composable
fun PidanPerch(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val colors = PerContextTheme.colors
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val breathing = if (animate && resumed) {
        val transition = rememberInfiniteTransition(label = "pidanBreathing")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 4_000
                    0f at 0 using FastOutSlowInEasing
                    1f at 1_600 using FastOutSlowInEasing
                    0f at 3_400
                    0f at 4_000
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "pidanBreathingPhase",
        )
    } else {
        rememberUpdatedState(0f)
    }
    val tailSway = if (animate && resumed) {
        val transition = rememberInfiniteTransition(label = "pidanTail")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 8_000
                    0f at 0
                    0f at 5_000 using FastOutSlowInEasing
                    3f at 5_400 using FastOutSlowInEasing
                    -1.5f at 5_850 using FastOutSlowInEasing
                    0f at 6_400
                    0f at 8_000
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "pidanTailAngle",
        )
    } else {
        rememberUpdatedState(0f)
    }
    val nightFilter = remember(colors.isDark) {
        if (colors.isDark) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.86f, 0.86f, 0.86f, 1f) })
        } else {
            null
        }
    }
    Box(
        modifier = modifier.fillMaxWidth().height(40.dp).testTag("pidan_perch"),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp)
                .width(132.dp)
                .height(6.dp)
                .background(colors.shelf, RoundedCornerShape(3.dp)),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 17.dp, bottom = 2.dp)
                .wrapContentSize(align = Alignment.BottomEnd, unbounded = true)
                .size(width = 122.dp, height = 56.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.pidan_sleeping_body),
                contentDescription = null,
                colorFilter = nightFilter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                    scaleY = 1f + 0.09f * breathing.value
                    scaleX = 1f + 0.014f * breathing.value
                },
            )
            Image(
                painter = painterResource(R.drawable.pidan_sleeping_tail),
                contentDescription = null,
                colorFilter = nightFilter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.85f, 0.8f)
                    rotationZ = tailSway.value
                },
            )
            Image(
                painter = painterResource(R.drawable.pidan_sleeping_head),
                contentDescription = null,
                colorFilter = nightFilter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0.25f, 0.86f)
                    translationY = 0.75.dp.toPx() * breathing.value
                    rotationZ = 0.8f * breathing.value
                },
            )
            Image(
                painter = painterResource(R.drawable.pidan_sleeping_paws),
                contentDescription = null,
                colorFilter = nightFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
