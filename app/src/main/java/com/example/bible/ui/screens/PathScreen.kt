package com.example.bible.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bible.AppViewModel
import com.example.bible.Screen
import com.example.bible.data.PlanDay
import com.example.bible.data.PlanUnit
import com.example.bible.data.Progress
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.PopButton
import com.example.bible.ui.components.PopProgressBar
import com.example.bible.ui.components.PopSurface
import com.example.bible.ui.components.StatsBar
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private enum class NodeState { Done, Current, Locked }

// Day buttons are a true ellipse (wider than tall), like a pebble lying flat.
private val NodeWidth = 80.dp
private val NodeHeight = 64.dp
private val NodeDepth = 7.dp
private val Ellipse = GenericShape { size, _ -> addOval(Rect(Offset.Zero, size)) }

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Horizontal zigzag offset for a node, in dp, following a gentle sine wave. */
private fun zigzag(i: Int): Float = (sin(i * PI / 4.0) * 72.0).toFloat()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PathScreen(vm: AppViewModel, progress: Progress, openPicker: () -> Unit) {
    val plan = vm.plan
    val current = progress.currentDay(plan.days.size)
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // List item index of a plan day: every unit adds one header item before its days.
    fun itemIndexOf(day: Int) = day + plan.unitOf(day).index + 1
    val currentItem = itemIndexOf(current.coerceAtMost(plan.days.lastIndex))
    // Leave room above today's node so it sits in the middle of the screen, not under the banner.
    val leadPx = with(density) { 260.dp.toPx() }

    LaunchedEffect(Unit) {
        listState.scrollToItem(currentItem)
        listState.scrollBy(-leadPx)
    }

    val currentVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == currentItem } }
    }
    val currentBelow by remember {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < currentItem }
    }

    Column(Modifier.fillMaxSize()) {
        StatsBar(progress, openPicker)
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                plan.units.forEach { unit ->
                    val colors = Palette.units[unit.index % Palette.units.size]
                    stickyHeader(key = "u${unit.index}") {
                        UnitBanner(unit, colors, progress)
                    }
                    unit.days.forEachIndexed { i, day ->
                        val state = when {
                            day.index in progress.completedDays -> NodeState.Done
                            day.index == current -> NodeState.Current
                            else -> NodeState.Locked
                        }
                        item(key = "d${day.index}") {
                            DayNode(
                                day = day,
                                xOffset = zigzag(day.index),
                                colors = colors,
                                state = state,
                                isUnitEnd = i == unit.days.lastIndex,
                                selected = selected == day.index,
                                streak = progress.streak(),
                                onTap = { selected = if (selected == day.index) null else day.index },
                                onStart = {
                                    selected = null
                                    vm.push(Screen.Lesson(day.index))
                                },
                                topPadding = (if (i == 0) 28.dp else 0.dp) + (if (state == NodeState.Current) 36.dp else 0.dp),
                            )
                        }
                    }
                }
                item(key = "end") { FinishLine(progress.completedDays.size, plan.days.size) }
            }

            JumpButton(
                visible = !currentVisible && current < plan.days.size,
                pointDown = currentBelow,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                onClick = {
                    // One animated scroll straight to the final resting spot (negative offset = room above).
                    scope.launch { listState.animateScrollToItem(currentItem, -leadPx.toInt()) }
                },
            )
        }
    }
}

/** Floating "jump to today's reading" button shown when the current node is off screen. */
@Composable
private fun JumpButton(visible: Boolean, pointDown: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(0.5f)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier,
    ) {
        PopSurface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = Palette.Card,
            borderColor = Palette.Line,
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                if (pointDown) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                "Jump to current day",
                Modifier.size(34.dp),
                tint = Palette.Blue.face,
            )
        }
    }
}

@Composable
private fun UnitBanner(unit: PlanUnit, colors: Pop, progress: Progress) {
    val done = unit.days.count { it.index in progress.completedDays }
    Box(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.edge)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.face)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Text(
                    "UNIT ${unit.index + 1} · ${done}/${unit.days.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(unit.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(unit.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun DayNode(
    day: PlanDay,
    xOffset: Float,
    colors: Pop,
    state: NodeState,
    isUnitEnd: Boolean,
    selected: Boolean,
    streak: Int,
    onTap: () -> Unit,
    onStart: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    val nodeColors = when (state) {
        NodeState.Done -> if (isUnitEnd) Palette.Gold else colors
        NodeState.Current -> colors
        NodeState.Locked -> Palette.Locked
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.offset(x = xOffset.dp), contentAlignment = Alignment.Center) {
            if (state == NodeState.Current) {
                // Progress ring + bobbing START bubble, the eye-catching "you are here".
                val ring = Palette.Line
                // Ring hugs the whole button (face + lip) so the gap is even all the way round.
                Canvas(Modifier.size(NodeWidth + 28.dp, NodeHeight + NodeDepth + 28.dp)) {
                    val stroke = 8.dp.toPx()
                    drawOval(
                        ring,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                }
                StartBubble(colors, Modifier.align(Alignment.TopCenter).offset(y = (-48).dp))
            }
            PopSurface(
                onClick = onTap,
                // Face is a perfect ellipse; the extra height is the 3D lip underneath.
                modifier = Modifier.size(width = NodeWidth, height = NodeHeight + NodeDepth),
                colors = nodeColors,
                shape = Ellipse,
                depth = NodeDepth,
            ) {
                val tint = if (state == NodeState.Locked) Palette.Faint else Color.White
                when {
                    isUnitEnd -> Icon(AppIcons.Trophy, null, Modifier.size(32.dp), tint = tint)
                    state == NodeState.Done -> Icon(Icons.Default.Check, null, Modifier.size(38.dp), tint = tint)
                    state == NodeState.Current -> Icon(Icons.Default.Star, null, Modifier.size(38.dp), tint = tint)
                    else -> Icon(Icons.Default.Lock, null, Modifier.size(28.dp), tint = tint)
                }
            }
        }

        AnimatedVisibility(
            visible = selected,
            enter = expandVertically(spring(0.7f, 600f), expandFrom = Alignment.Top) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100)),
        ) {
            DayPopover(day, state, colors, streak, onStart)
        }
    }
}

@Composable
private fun StartBubble(colors: Pop, modifier: Modifier) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        0f, -8f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Box(
        modifier
            .graphicsLayer { translationY = bob * density }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Palette.Line, RoundedCornerShape(12.dp))
            .background(Palette.Background)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text("START", style = MaterialTheme.typography.labelLarge, color = colors.face)
    }
}

@Composable
private fun DayPopover(day: PlanDay, state: NodeState, colors: Pop, streak: Int, onStart: () -> Unit) {
    val locked = state == NodeState.Locked
    val bg = if (locked) Palette.Snow else colors.face
    val fg = if (locked) Palette.Faint else Color.White
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (locked) Modifier.border(2.dp, Palette.Line, RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp)
    ) {
        Text("Day ${day.dayNumber}", style = MaterialTheme.typography.titleLarge, color = fg)
        Spacer(Modifier.height(6.dp))
        day.readings.forEach {
            Text("• ${it.label}", style = MaterialTheme.typography.bodyLarge, color = fg)
        }
        Spacer(Modifier.height(14.dp))
        when (state) {
            NodeState.Locked -> {
                Text("Finish the days before this one to unlock it!", style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
                Spacer(Modifier.height(12.dp))
                PopButton("Locked", {}, Modifier.fillMaxWidth(), enabled = false)
            }
            NodeState.Done -> PopButton("Read again", onStart, Modifier.fillMaxWidth(), colors = Palette.Card, textColor = colors.face)
            NodeState.Current -> {
                PopButton("Start", onStart, Modifier.fillMaxWidth(), colors = Palette.Card, textColor = colors.face)
            }
        }
    }
}

@Composable
private fun FinishLine(done: Int, total: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(if (done == total) Palette.Gold.face else Palette.Locked.face),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Trophy, null, Modifier.size(48.dp), tint = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Text("The whole Bible in a year", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopProgressBar(done / total.toFloat(), Modifier.weight(1f), color = Palette.Gold.face)
            Spacer(Modifier.width(10.dp))
            Text("$done/$total", style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        }
    }
}
