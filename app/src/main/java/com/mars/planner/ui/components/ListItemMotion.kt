package com.mars.planner.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.mars.planner.ui.theme.MarsMotion
import kotlinx.coroutines.delay

@Composable
fun Modifier.marsListItemAppear(index: Int): Modifier = composed {
    val reduce = LocalReduceAnimations.current
    var visible by remember { mutableStateOf(reduce) }
    LaunchedEffect(index) {
        if (reduce) {
            visible = true
        } else {
            visible = false
            delay((index.coerceAtMost(15) * MarsMotion.ListStaggerMs).toLong())
            visible = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduce) tween(0) else tween(300, easing = FastOutSlowInEasing),
        label = "listItemAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 14f,
        animationSpec = if (reduce) tween(0) else tween(300, easing = FastOutSlowInEasing),
        label = "listItemOffset"
    )
    Modifier.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

@Composable
fun Modifier.marsListItemMotion(index: Int): Modifier = marsListItemAppear(index)
