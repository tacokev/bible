package com.example.bible.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The core "pop" surface: a bright face sitting on a darker lip. Pressing pushes the
 * face down into the lip, giving the chunky, tactile feel.
 */
@Composable
fun PopSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    colors: Pop = Palette.Green,
    shape: Shape = RoundedCornerShape(16.dp),
    depth: Dp = 4.dp,
    borderColor: Color? = null,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val sink by animateDpAsState(
        if (pressed && enabled && onClick != null) depth else 0.dp,
        spring(stiffness = Spring.StiffnessHigh),
        label = "sink",
    )
    val clickMod = if (onClick != null) Modifier.clickable(interaction, indication = null, enabled = enabled) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    } else Modifier

    Box(modifier.then(clickMod), propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .background(colors.edge, shape)
        )
        Box(
            Modifier
                .offset(y = sink)
                .padding(bottom = depth)
                .clip(shape)
                .background(colors.face, shape)
                .then(if (borderColor != null) Modifier.border(2.dp, borderColor, shape) else Modifier),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** Full-width chunky button with an uppercase label, like CONTINUE / CHECK. */
@Composable
fun PopButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: Pop = Palette.Green,
    textColor: Color = Color.White,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = if (enabled) colors else Palette.Locked
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        colors = c,
        enabled = enabled,
        borderColor = if (c == Palette.Card) Palette.Line else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(22.dp), tint = Color.Unspecified)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) textColor else Palette.Faint,
            )
        }
    }
}

/** Duolingo-style progress bar: rounded track with a glossy highlight stripe. */
@Composable
fun PopProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Palette.Green.face, height: Dp = 16.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 120f), label = "progress")
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Palette.Line)
    ) {
        if (animated > 0.001f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            ) {
                Box(
                    Modifier
                        .padding(start = 8.dp, end = 8.dp, top = height / 4)
                        .fillMaxWidth()
                        .height(height / 4)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/** Springs an element in from small to full size, optionally after a delay. */
fun Modifier.popIn(delayMillis: Int = 0): Modifier = composed {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        alpha.animateTo(1f, tween(120))
    }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

/** Card with a coloured header strip and a big value, used on the lesson-complete screen. */
@Composable
fun StatCard(label: String, value: String, colors: Pop, icon: ImageVector?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.face)
            .padding(2.dp)
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Background)
                    .padding(vertical = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(24.dp), tint = Color.Unspecified)
                    Spacer(Modifier.width(6.dp))
                }
                Text(value, style = MaterialTheme.typography.titleLarge, color = colors.face, fontWeight = FontWeight.Black)
            }
        }
    }
}

private class Particle(
    val x: Float, val vx: Float, val vy: Float, val color: Color,
    val spin: Float, val w: Float, val h: Float, val wobble: Float,
)

/** A one-shot confetti burst drawn on a Canvas. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val colors = listOf(Palette.Green.face, Palette.Blue.face, Palette.Gold.face, Palette.Pink.face, Palette.Purple.face, Palette.Orange.face)
    val particles = remember {
        List(110) {
            Particle(
                x = Random.nextFloat(),
                vx = (Random.nextFloat() - 0.5f) * 0.6f,
                vy = -(0.6f + Random.nextFloat() * 0.9f),
                color = colors.random(),
                spin = (Random.nextFloat() - 0.5f) * 1440f,
                w = 6f + Random.nextFloat() * 8f,
                h = 10f + Random.nextFloat() * 10f,
                wobble = Random.nextFloat() * 2f * PI.toFloat(),
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200)) }
    Canvas(modifier.fillMaxSize()) {
        val time = t.value * 3.2f
        if (t.value >= 1f) return@Canvas
        particles.forEach { p ->
            val px = (p.x + p.vx * time + 0.03f * sin(time * 6f + p.wobble)) * size.width
            val py = size.height * (0.55f + p.vy * time + 0.55f * time * time)
            val fade = (1f - t.value).coerceIn(0f, 1f)
            rotate(p.spin * time, Offset(px, py)) {
                drawRect(p.color.copy(alpha = fade), Offset(px - p.w / 2, py - p.h / 2), Size(p.w * density / 2, p.h * density / 2))
            }
        }
    }
}
