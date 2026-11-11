package com.example.bible.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.AppViewModel
import com.example.bible.Screen
import com.example.bible.data.CompletionResult
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.Confetti
import com.example.bible.ui.components.PopButton
import com.example.bible.ui.components.StatCard
import com.example.bible.ui.components.WeekRow
import com.example.bible.ui.components.popIn
import com.example.bible.ui.theme.Palette
import kotlinx.coroutines.delay

private val cheers = listOf(
    "Well done!", "Faithful reading!", "You did it!", "Wonderful work!", "Keep it up!", "Amazing!",
)

private val verses = listOf(
    "Your word is a lamp to my feet and a light to my path. — Psalm 119:105",
    "Man shall not live on bread alone, but on every word that comes from the mouth of God. — Matthew 4:4",
    "All Scripture is God-breathed and is useful for instruction. — 2 Timothy 3:16",
    "The grass withers and the flowers fall, but the word of our God stands forever. — Isaiah 40:8",
    "Blessed are those who hear the word of God and obey it. — Luke 11:28",
    "I have hidden Your word in my heart that I might not sin against You. — Psalm 119:11",
)

@Composable
fun CompleteScreen(vm: AppViewModel, result: CompletionResult) {
    val day = vm.plan.days[result.dayIndex]
    val next = { if (result.extendedStreakToday) vm.replaceTop(Screen.StreakCelebration(result)) else vm.goHome() }
    BackHandler(onBack = next)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            val wiggle by rememberInfiniteTransition(label = "wiggle").animateFloat(
                -6f, 6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wiggle",
            )
            Box(
                Modifier
                    .popIn()
                    .graphicsLayer { rotationZ = wiggle }
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Palette.Gold.face)
                    .border(8.dp, Palette.Gold.edge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Trophy, null, Modifier.size(76.dp), tint = Color.White)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                remember { cheers.random() },
                style = MaterialTheme.typography.headlineLarge,
                color = Palette.Gold.edge,
                modifier = Modifier.popIn(150),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Day ${day.dayNumber} complete · ${day.summary}",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.popIn(250),
            )
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Chapters", "${result.chapters}", Palette.Blue, AppIcons.BookColor, Modifier.weight(1f).popIn(400))
                StatCard("Streak", "${result.streakAfter}", Palette.Orange, AppIcons.Flame, Modifier.weight(1f).popIn(520))
                StatCard("Plan", "${result.daysCompleted * 100 / vm.plan.days.size}%", Palette.Green, null, Modifier.weight(1f).popIn(640))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                remember { verses.random() },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp).popIn(800),
            )
            Spacer(Modifier.weight(1f))
            PopButton("Continue", next, Modifier.fillMaxWidth())
        }
        Confetti()
    }
}

@Composable
fun StreakScreen(vm: AppViewModel, result: CompletionResult) {
    BackHandler { vm.goHome() }
    var shown by remember { mutableIntStateOf(result.streakBefore) }
    val flameScale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        flameScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        delay(350)
        shown = result.streakAfter
        flameScale.animateTo(1.25f, tween(120))
        flameScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
    }
    val flicker by rememberInfiniteTransition(label = "flicker").animateFloat(
        0.96f, 1.04f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "flicker",
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(
            AppIcons.Flame, null,
            Modifier
                .size(150.dp)
                .graphicsLayer {
                    scaleX = flameScale.value * flicker
                    scaleY = flameScale.value * (2f - flicker)
                },
            tint = Color.Unspecified,
        )
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                (slideInVertically(spring(0.5f)) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "count",
        ) { n ->
            Text(
                "$n",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, fontWeight = FontWeight.Black),
                color = Palette.Flame,
            )
        }
        Text("day streak!", style = MaterialTheme.typography.headlineMedium, color = Palette.Flame)
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier
                .popIn(500)
                .fillMaxWidth()
                .border(2.dp, Palette.Line, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            WeekRow(result.activeDates)
            Spacer(Modifier.height(14.dp))
            Text(
                streakMessage(result.streakAfter),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.weight(1f))
        PopButton("Continue", { vm.goHome() }, Modifier.fillMaxWidth(), colors = Palette.Orange)
    }
}

private fun streakMessage(streak: Int) = when {
    streak <= 1 -> "You started a streak! Read again tomorrow to keep it going."
    streak < 7 -> "Great consistency! Come back tomorrow to make it ${streak + 1}."
    streak < 30 -> "You're building a real habit. Don't break the chain!"
    streak < 100 -> "Incredible devotion. You're in the top tier of readers!"
    else -> "A legendary streak. \"Your word is a lamp to my feet.\""
}
