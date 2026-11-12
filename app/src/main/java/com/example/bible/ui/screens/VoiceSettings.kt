package com.example.bible.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.AppViewModel
import com.example.bible.data.Narrator
import com.example.bible.data.VoiceLibrary
import com.example.bible.data.VoiceModel
import com.example.bible.ui.components.PopButton
import com.example.bible.ui.components.PopProgressBar
import com.example.bible.ui.components.PopSurface
import com.example.bible.ui.components.popIn
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop

/**
 * Reading-aloud settings: speed, the phone's own voice, installed neural voices (pick a
 * speaker, delete), and ways to add more (import a file, or a link).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCard(vm: AppViewModel) {
    val narrator = vm.narrator
    val library = narrator.library
    val voices by library.voices.collectAsStateWithLifecycle()
    val task by library.task.collectAsStateWithLifecycle()
    val selected = narrator.voice
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf<VoiceModel?>(null) }
    var askLink by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(narrator::importVoice)
    }

    fun pick(choice: Narrator.VoiceChoice?) {
        narrator.selectVoice(choice)
        narrator.preview()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .border(2.dp, Palette.Line, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text("Reading voice", style = MaterialTheme.typography.titleMedium)
        Text(
            "Everything runs on your phone, no internet needed. Tap a voice to hear it.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, modifier = Modifier.padding(end = 4.dp))
            Narrator.speeds.forEach { sp ->
                VoiceChip(
                    (if (sp % 1f == 0f) "${sp.toInt()}" else "$sp".trimEnd('0')) + "×",
                    narrator.speed == sp,
                    Modifier.weight(1f),
                ) { narrator.changeSpeed(sp); narrator.preview() }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Chapter start music", style = MaterialTheme.typography.bodyLarge)
                Text("Plays as each chapter begins", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            Switch(
                checked = narrator.introEnabled,
                onCheckedChange = narrator::changeIntroEnabled,
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
        Spacer(Modifier.height(14.dp))
        narrator.voiceError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Red.face, modifier = Modifier.padding(bottom = 8.dp))
        }
        VoiceChip("Phone voice", selected == null) { pick(null) }

        voices.forEach { model ->
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Palette.Line, thickness = 2.dp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(model.detail, style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
                }
                Icon(
                    Icons.Default.Delete, "Remove ${model.name}",
                    Modifier
                        .size(26.dp)
                        .clickableNoRipple { confirmDelete = model },
                    tint = Palette.Faint,
                )
            }
            Spacer(Modifier.height(8.dp))
            val activeSid = selected?.takeIf { it.modelId == model.id }?.sid
            when {
                model.speakers.isEmpty() ->
                    VoiceChip("Use this voice", activeSid != null) { pick(Narrator.VoiceChoice(model.id, 0)) }
                model.speakers.size <= 24 -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    model.speakers.forEachIndexed { sid, name ->
                        VoiceChip(name, activeSid == sid) { pick(Narrator.VoiceChoice(model.id, sid)) }
                    }
                }
                else -> {
                    // Hundreds of speakers (e.g. LibriTTS): step through them.
                    val count = model.speakers.size
                    val current = activeSid ?: 0
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoiceChip("−", false) { pick(Narrator.VoiceChoice(model.id, (current - 1 + count) % count)) }
                        VoiceChip(
                            if (activeSid != null) model.speakers[current] else "Use this voice",
                            activeSid != null,
                            Modifier.weight(1f),
                        ) { pick(Narrator.VoiceChoice(model.id, current)) }
                        VoiceChip("+", false) { pick(Narrator.VoiceChoice(model.id, (current + 1) % count)) }
                    }
                    Text(
                        "$count speakers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Faint,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)
        Spacer(Modifier.height(12.dp))
        when (val t = task) {
            is VoiceLibrary.Task.Working -> {
                Text(
                    t.fraction?.let { "${t.label} ${(it * 100).toInt()}%" } ?: t.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Muted,
                )
                Spacer(Modifier.height(8.dp))
                PopProgressBar(t.fraction ?: 1f, Modifier.fillMaxWidth())
            }
            else -> {
                if (t is VoiceLibrary.Task.Failed) {
                    Text(t.message, style = MaterialTheme.typography.bodyMedium, color = Palette.Red.face)
                    Text(
                        "DISMISS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Blue.face,
                        modifier = Modifier
                            .padding(top = 6.dp, bottom = 12.dp)
                            .clickableNoRipple { library.dismissError() },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PopButton("Import file", { picker.launch(arrayOf("*/*")) }, Modifier.weight(1f), colors = Palette.Card, textColor = Palette.Blue.face)
                    PopButton("From link", { askLink = true }, Modifier.weight(1f), colors = Palette.Card, textColor = Palette.Blue.face)
                }
            }
        }
        Text(
            "Add any sherpa-onnx voice package (.tar.bz2): Piper, Kokoro or Kitten voices. Browse voices to hear samples, then copy a download link into From link.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Faint,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "BROWSE VOICES",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Blue.face,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickableNoRipple { uriHandler.openUri(VoiceLibrary.CATALOG_URL) },
        )
    }

    confirmDelete?.let { model ->
        SimpleDialog(
            title = "Remove ${model.name}?",
            body = "You can add it again anytime.",
            confirm = "Remove",
            confirmColors = Palette.Red,
            onConfirm = { narrator.deleteVoice(model.id); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }

    if (askLink) LinkDialog(
        onDownload = { narrator.downloadVoice(it); askLink = false },
        onDismiss = { askLink = false },
    )
}

@Composable
private fun LinkDialog(onDownload: (String) -> Unit, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .popIn()
                .background(Palette.Background, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text("Download a voice", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Paste a link to a sherpa-onnx voice package, e.g. a .tar.bz2 from the tts-models page.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Snow)
                    .border(2.dp, Palette.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Blue.face),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (link.isEmpty()) Text("https://…", style = MaterialTheme.typography.bodyLarge, color = Palette.Faint)
            }
            Spacer(Modifier.height(20.dp))
            PopButton("Download", { if (link.isNotBlank()) onDownload(link) }, Modifier.fillMaxWidth(), colors = Palette.Blue)
            Spacer(Modifier.height(10.dp))
            PopButton("Cancel", onDismiss, Modifier.fillMaxWidth(), colors = Palette.Card, textColor = Palette.Muted)
        }
    }
}

@Composable
private fun SimpleDialog(title: String, body: String, confirm: String, confirmColors: Pop, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .popIn()
                .background(Palette.Background, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PopButton("Keep it", onDismiss, Modifier.fillMaxWidth(), colors = Palette.Blue)
            Spacer(Modifier.height(10.dp))
            PopButton(confirm, onConfirm, Modifier.fillMaxWidth(), colors = confirmColors)
        }
    }
}

@Composable
private fun VoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = if (selected) Pop(Palette.Selected, Palette.SelectedBorder) else Palette.Card,
        borderColor = if (selected) Palette.SelectedBorder else Palette.Line,
        shape = RoundedCornerShape(12.dp),
        depth = 3.dp,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Blue.face else Palette.Faint,
            modifier = Modifier.padding(horizontal = 10.dp),
            maxLines = 1,
        )
    }
}
