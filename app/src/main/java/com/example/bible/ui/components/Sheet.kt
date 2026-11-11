package com.example.bible.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.bible.ui.theme.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A bottom sheet that follows the finger exactly. If its content scrolls, dragging down
 * first scrolls the content back to the top and then pulls the sheet down, in one gesture.
 * Releasing past a third of its height (or flicking down) slides it away; otherwise it springs back.
 *
 * Shown while [visible]; animates out and then calls [onDismiss] when the user closes it.
 * Place it at the root so it covers the whole screen.
 */
@Composable
fun PopSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var heightPx by remember { mutableIntStateOf(0) }
    // How far the sheet is pushed down from fully open, in px. Updated directly while dragging.
    var offset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var anim by remember { mutableStateOf<Job?>(null) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    fun animateTo(target: Float, velocity: Float = 0f, then: () -> Unit = {}) {
        anim?.cancel()
        anim = scope.launch {
            val spec = if (target == 0f) spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) else tween(200)
            animate(offset, target, velocity, spec) { v, _ -> offset = v }
            then()
        }
    }

    fun close() = animateTo(heightPx.toFloat()) {
        shown = false
        currentOnDismiss()
    }

    fun drag(delta: Float) {
        anim?.cancel()
        offset = (offset + delta).coerceIn(0f, heightPx.toFloat())
    }

    fun settle(velocity: Float) {
        if (velocity > 1500f || offset > heightPx / 3f) close() else animateTo(0f, velocity)
    }

    LaunchedEffect(visible) {
        if (visible && !shown) { shown = true; opening = true; heightPx = 0 }
        if (!visible && shown && heightPx > 0) close() // closed from code, e.g. after picking
    }
    if (!shown) return

    BackHandler { close() }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Sheet pulled down part-way and the finger moves back up: raise the sheet before the list scrolls.
                if (available.y < 0 && offset > 0f) {
                    val used = maxOf(available.y, -offset)
                    drag(used)
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // List already at the top and the finger keeps moving down: pull the sheet down.
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    drag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset > 0f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }

    val progress = if (heightPx == 0) 0f else (1f - offset / heightPx).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        // Dimmed background: fades with the drag, tap to close.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { close() }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 24.dp)
                .fillMaxWidth()
                .onSizeChanged {
                    heightPx = it.height
                    if (opening) {
                        opening = false
                        offset = it.height.toFloat()
                        animateTo(0f)
                    }
                }
                .graphicsLayer { translationY = if (heightPx == 0) 10_000f else offset }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Palette.Background)
                // Swallow taps so they don't reach the scrim.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .nestedScroll(connection)
        ) {
            // Grab handle: dragging here always moves the sheet.
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        rememberDraggableState { d -> drag(d) },
                        Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Line)
                )
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp),
                content = content,
            )
        }
    }
}
