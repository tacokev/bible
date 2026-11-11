package com.example.bible.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.example.bible.ui.theme.Palette

/**
 * Reading layout whose top and bottom bars slide away while scrolling down and come back
 * when scrolling up, reaching the end, or tapping the text. The content is laid out
 * underneath the bars and gets [PaddingValues] so nothing starts hidden behind them.
 *
 * [resetKey] brings the bars back whenever it changes (e.g. a new chapter).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImmersiveScaffold(
    resetKey: Any?,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(resetKey) { visible = true }

    // Hide/show the phone's status and navigation bars together with ours.
    val view = LocalView.current
    val controller = remember(view) {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    LaunchedEffect(visible, controller) {
        if (visible) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(controller) {
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val connection = remember {
        object : NestedScrollConnection {
            private var drift = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Couldn't scroll any further down: we're at the end, so show the bars (and CONTINUE).
                if (available.y < -1f) { visible = true; drift = 0f; return Offset.Zero }
                if (consumed.y == 0f) return Offset.Zero
                // Require a little consistent movement so tiny jitters don't flicker the bars.
                drift = if ((drift < 0) == (consumed.y < 0)) drift + consumed.y else consumed.y
                if (drift < -24f) visible = false
                if (drift > 24f) visible = true
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y < 0f) visible = true // flung into the end of the chapter
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(connection)
            .pointerInput(Unit) { detectTapGestures { visible = !visible } }
    ) {
        val padding = with(density) { PaddingValues(top = topPx.toDp(), bottom = bottomPx.toDp()) }
        content(padding)

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            // Inset sizes "ignoring visibility" stay constant when the system bars hide,
            // so the text underneath never jumps.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Background)
                    .onSizeChanged { topPx = it.height }
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top)))
            ) { topBar() }
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Background)
                    .onSizeChanged { bottomPx = it.height }
                    .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            ) { bottomBar() }
        }
    }
}
