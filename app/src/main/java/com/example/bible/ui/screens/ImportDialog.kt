package com.example.bible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bible.AppViewModel.ImportState
import com.example.bible.data.importer.ParsedBible
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.PopButton
import com.example.bible.ui.components.popIn
import com.example.bible.ui.theme.Palette

@Composable
fun ImportDialog(state: ImportState, onConfirm: (title: String, code: String) -> Unit, onDismiss: () -> Unit) {
    val busy = state is ImportState.Reading || state is ImportState.Saving
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !busy, dismissOnBackPress = !busy),
    ) {
        Column(
            Modifier
                .popIn()
                .fillMaxWidth()
                .background(Palette.Background, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                ImportState.Reading, ImportState.Saving -> {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(color = Palette.Blue.face, strokeWidth = 5.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(if (state == ImportState.Reading) "Reading your file…" else "Saving translation…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                }
                is ImportState.Failed -> {
                    Icon(Icons.Default.Warning, null, Modifier.size(56.dp), tint = Palette.Red.face)
                    Spacer(Modifier.height(10.dp))
                    Text("Couldn't import", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(state.message, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PopButton("OK", onDismiss, Modifier.fillMaxWidth(), colors = Palette.Blue)
                }
                is ImportState.Preview -> Preview(state.bible, onConfirm, onDismiss)
            }
        }
    }
}

@Composable
private fun Preview(bible: ParsedBible, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(bible.title.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(bible.code.orEmpty().uppercase().take(6)) }
    val valid = title.isNotBlank() && code.isNotBlank()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.BookColor, null, Modifier.size(36.dp), tint = Color.Unspecified)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Ready to import", style = MaterialTheme.typography.headlineMedium)
                Text(bible.format, style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("%,d".format(bible.bookCount), "books", Modifier.weight(1f))
            Stat("%,d".format(bible.chapterCount), "chapters", Modifier.weight(1f))
            Stat("%,d".format(bible.verseCount), "verses", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        Field("Name", title, { title = it }, KeyboardCapitalization.Words)
        Spacer(Modifier.height(10.dp))
        Field("Short name", code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, KeyboardCapitalization.Characters)

        val missing = bible.missingBooks
        val warnings = buildList {
            if (missing.isNotEmpty() && missing.size < 66) {
                val names = missing.map { it.name }
                add(
                    when {
                        missing.size == 39 && missing.all { !it.isNewTestament } -> "New Testament only. The Old Testament reading days will show a notice."
                        missing.size == 27 && missing.all { it.isNewTestament } -> "Old Testament only. The New Testament reading days will show a notice."
                        missing.size <= 6 -> "Missing: ${names.joinToString(", ")}."
                        else -> "Missing ${missing.size} books, including ${names.take(4).joinToString(", ")}."
                    }
                )
            }
            if (bible.skippedBooks.isNotEmpty()) {
                val s = bible.skippedBooks.toList()
                add("Skipped books not in the 66-book Bible: ${s.take(5).joinToString(", ")}${if (s.size > 5) " and ${s.size - 5} more" else ""}.")
            }
        }
        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Gold.face.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                warnings.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PopButton("Add translation", { onConfirm(title, code) }, Modifier.fillMaxWidth(), enabled = valid)
        Spacer(Modifier.height(4.dp))
        Text(
            "CANCEL",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Faint,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickableNoRipple(onDismiss)
                .padding(12.dp),
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .border(2.dp, Palette.Line, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.Blue.face)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, caps: KeyboardCapitalization) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Snow)
                .border(2.dp, Palette.Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Blue.face),
                keyboardOptions = KeyboardOptions(capitalization = caps),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
