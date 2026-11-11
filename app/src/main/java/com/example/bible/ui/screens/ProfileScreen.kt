package com.example.bible.ui.screens

import androidx.compose.foundation.background
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.bible.data.Reminders
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.bible.AppViewModel
import com.example.bible.data.Progress
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.PopButton
import com.example.bible.ui.components.PopProgressBar
import com.example.bible.ui.components.PopSurface
import com.example.bible.ui.components.WeekRow
import com.example.bible.ui.components.popIn
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop
import com.example.bible.ui.theme.ThemeMode
import com.example.bible.ui.theme.ScriptureFont

@Composable
fun ProfileScreen(vm: AppViewModel, progress: Progress, openPicker: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val total = vm.plan.days.size
    val done = progress.completedDays.size
    val streak = progress.streak()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        // Streak hero
        Box(
            Modifier
                .fillMaxWidth()
                .background(Palette.Orange.edge, RoundedCornerShape(20.dp))
                .padding(bottom = 5.dp)
                .background(Palette.Orange.face, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$streak", style = MaterialTheme.typography.displayLarge, color = Color.White)
                    Text("day streak", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (progress.readToday()) "You've read today. Nice!" else "Read today to keep your streak!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                Icon(
                    AppIcons.Flame, null,
                    Modifier
                        .size(84.dp)
                        .popIn()
                        .graphicsLayer { alpha = if (progress.readToday()) 1f else 0.45f },
                    tint = Color.Unspecified,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, Palette.Line, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) { WeekRow(progress.activeDates) }

        SectionTitle("Statistics")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox(AppIcons.Flame, "$streak", "Day streak", Modifier.weight(1f))
            StatBox(null, "${progress.longestStreak}", "Longest streak", Modifier.weight(1f), star = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val chaptersRead = progress.completedDays.sumOf { vm.plan.days.getOrNull(it)?.chapters?.size ?: 0 }
            StatBox(AppIcons.BookColor, "$chaptersRead", "Chapters read", Modifier.weight(1f))
            StatBox(null, "$done", "Days read", Modifier.weight(1f), check = true)
        }

        SectionTitle("One year plan")
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, Palette.Line, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Day ${minOf(progress.currentDay(total) + 1, total)} of $total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${done * 100 / total}%", style = MaterialTheme.typography.titleMedium, color = Palette.Green.face)
            }
            Spacer(Modifier.height(10.dp))
            PopProgressBar(done / total.toFloat(), Modifier.fillMaxWidth())
        }

        SectionTitle("Settings")
        ReminderCard(vm, progress)
        Spacer(Modifier.height(12.dp))
        PopSurface(
            onClick = openPicker,
            modifier = Modifier.fillMaxWidth(),
            colors = Palette.Card,
            borderColor = Palette.Line,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Translation", style = MaterialTheme.typography.titleMedium)
                    Text(progress.translation.title, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Faint)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, Palette.Line, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = progress.themeMode == mode
                    PopSurface(
                        onClick = { vm.setThemeMode(mode) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = if (selected) Pop(Palette.Selected, Palette.SelectedBorder) else Palette.Card,
                        borderColor = if (selected) Palette.SelectedBorder else Palette.Line,
                        shape = RoundedCornerShape(12.dp),
                        depth = 3.dp,
                    ) {
                        Text(mode.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Blue.face else Palette.Faint)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .border(2.dp, Palette.Line, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Text size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SizeButton("A−") { vm.setTextScale(progress.textScale - 0.1f) }
                Spacer(Modifier.width(10.dp))
                SizeButton("A+") { vm.setTextScale(progress.textScale + 0.1f) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "In the beginning was the Word…",
                style = TextStyle(fontFamily = ScriptureFont, fontSize = 18.sp * progress.textScale, color = Palette.Ink),
            )
        }
        Spacer(Modifier.height(24.dp))
        PopButton("Reset progress", { confirmReset = true }, Modifier.fillMaxWidth(), colors = Palette.Card, textColor = Palette.Red.face)
        Spacer(Modifier.height(24.dp))
        Text(
            "Scripture texts are in the public domain: BSB, WEB, KJV, ASV and YLT.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }

    if (confirmReset) {
        Dialog(onDismissRequest = { confirmReset = false }) {
            Column(
                Modifier
                    .popIn()
                    .background(Palette.Background, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Reset all progress?", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your streak and completed days will be cleared. This can't be undone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                PopButton("Keep my progress", { confirmReset = false }, Modifier.fillMaxWidth(), colors = Palette.Blue)
                Spacer(Modifier.height(10.dp))
                PopButton("Reset", { vm.resetProgress(); confirmReset = false }, Modifier.fillMaxWidth(), colors = Palette.Red)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
}

@Composable
private fun StatBox(icon: ImageVector?, value: String, label: String, modifier: Modifier, star: Boolean = false, check: Boolean = false) {
    Row(
        modifier
            .border(2.dp, Palette.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (star) Icon(Icons.Default.Star, null, Modifier.size(26.dp), tint = Palette.Gold.face)
        else if (check) Icon(Icons.Default.CheckCircle, null, Modifier.size(26.dp), tint = Palette.Green.face)
        else if (icon != null) Icon(icon, null, Modifier.size(26.dp), tint = Color.Unspecified)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
        }
    }
}

@Composable
private fun SizeButton(text: String, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = Palette.Card,
        borderColor = Palette.Line,
        shape = RoundedCornerShape(12.dp),
        depth = 3.dp,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Blue.face)
    }
}

@Composable
private fun ReminderCard(vm: AppViewModel, progress: Progress) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) vm.setReminder(true, progress.reminderMinute)
    }

    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            denied = false
            vm.setReminder(true, progress.reminderMinute)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .border(2.dp, Palette.Line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily reminder", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (progress.reminderEnabled) "Only if you haven't read yet" else "A nudge to keep your streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted,
                )
            }
            Switch(
                checked = progress.reminderEnabled,
                onCheckedChange = { on -> if (on) enable() else vm.setReminder(false, progress.reminderMinute) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Palette.Green.face,
                    checkedBorderColor = Palette.Green.face,
                    uncheckedThumbColor = Palette.Faint,
                    uncheckedTrackColor = Palette.Snow,
                    uncheckedBorderColor = Palette.Line,
                ),
            )
        }
        if (denied && !progress.reminderEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Notifications are turned off for this app. Allow them in your phone's Settings to get reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Red.face,
            )
        }
        if (progress.reminderEnabled) {
            Spacer(Modifier.height(12.dp))
            PopSurface(
                onClick = { picking = true },
                modifier = Modifier.fillMaxWidth(),
                colors = Palette.Card,
                borderColor = Palette.Line,
                shape = RoundedCornerShape(12.dp),
                depth = 3.dp,
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me at", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, modifier = Modifier.weight(1f))
                    Text(Reminders.formatTime(progress.reminderMinute), style = MaterialTheme.typography.titleMedium, color = Palette.Blue.face)
                }
            }
            Text(
                "SEND A TEST",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Blue.face,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickableNoRipple { vm.sendTestReminder() },
            )
        }
    }

    if (picking) {
        ReminderTimeDialog(
            initialMinute = progress.reminderMinute,
            onPick = { vm.setReminder(true, it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initialMinute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .popIn()
                .background(Palette.Background, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Reminder time", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Palette.Snow,
                    selectorColor = Palette.Green.face,
                    containerColor = Palette.Background,
                    clockDialSelectedContentColor = Color.White,
                    clockDialUnselectedContentColor = Palette.Ink,
                    periodSelectorBorderColor = Palette.Line,
                    periodSelectorSelectedContainerColor = Palette.Selected,
                    periodSelectorSelectedContentColor = Palette.Blue.face,
                    periodSelectorUnselectedContentColor = Palette.Muted,
                    timeSelectorSelectedContainerColor = Palette.Selected,
                    timeSelectorSelectedContentColor = Palette.Blue.face,
                    timeSelectorUnselectedContainerColor = Palette.Snow,
                    timeSelectorUnselectedContentColor = Palette.Ink,
                ),
            )
            Spacer(Modifier.height(8.dp))
            PopButton("Save", { onPick(state.hour * 60 + state.minute) }, Modifier.fillMaxWidth())
            Text(
                "CANCEL",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Faint,
                modifier = Modifier
                    .clickableNoRipple(onDismiss)
                    .padding(12.dp),
            )
        }
    }
}
