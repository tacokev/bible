package com.example.bible.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.bible.data.Block
import com.example.bible.data.Chapter
import com.example.bible.data.Progress
import com.example.bible.data.Translation
import com.example.bible.data.dayOfWeek
import com.example.bible.data.today
import com.example.bible.ui.theme.LocalNeutrals
import com.example.bible.ui.theme.Nunito
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop
import com.example.bible.ui.theme.ScriptureFont

/** Small outlined chip showing the translation code. */
@Composable
fun TranslationPill(translation: Translation, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        colors = Palette.Card,
        borderColor = Palette.Line,
        shape = RoundedCornerShape(12.dp),
        depth = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.BookColor, null, Modifier.size(20.dp), tint = Color.Unspecified)
            Spacer(Modifier.width(6.dp))
            Text(translation.code, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        }
    }
}

@Composable
fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, color: Color, iconTint: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp), tint = iconTint)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** Top bar: translation, streak and days completed. */
@Composable
fun StatsBar(progress: Progress, onTranslation: () -> Unit) {
    val streak = progress.streak()
    val lit = progress.readToday()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Three equal slots so the streak sits exactly in the middle of the screen.
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                TranslationPill(progress.translation, onTranslation)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                StatChip(AppIcons.Flame, "$streak", if (lit) Palette.Flame else Palette.Faint, iconTint = if (lit) Color.Unspecified else Palette.Faint.copy(alpha = 0.5f))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                StatChip(AppIcons.BookColor, "${progress.completedDays.size}", Palette.Blue.face)
            }
        }
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)
    }
}

@Composable
fun TranslationSheet(
    visible: Boolean,
    current: Translation,
    translations: List<Translation>,
    onPick: (Translation) -> Unit,
    onImport: () -> Unit,
    onDelete: (Translation) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf<Translation?>(null) }
    PopSheet(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Choose a translation", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text("Built-in texts are free, public domain and work offline.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            Spacer(Modifier.height(16.dp))
            translations.filter { it.builtIn }.forEach { t ->
                TranslationRow(t, t.id == current.id, onClick = { onPick(t) }, onDelete = null)
            }
            val imported = translations.filter { !it.builtIn }
            if (imported.isNotEmpty()) {
                Text("Your translations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
                imported.forEach { t ->
                    TranslationRow(t, t.id == current.id, onClick = { onPick(t) }, onDelete = { deleting = t })
                }
            }
            Spacer(Modifier.height(8.dp))
            PopButton("Import translation", onImport, Modifier.fillMaxWidth(), colors = Palette.Blue)
            Spacer(Modifier.height(8.dp))
            Text(
                "Zefania XML, OSIS XML, JSON or CSV. Zipped or .gz files work too.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    deleting?.let { t ->
        ConfirmDialog(
            title = "Delete ${t.code}?",
            message = "\u201C${t.title}\u201D will be removed from this phone. You can import it again later.",
            confirm = "Delete",
            onConfirm = { onDelete(t); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TranslationRow(t: Translation, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    PopSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        colors = if (selected) Pop(Palette.Selected, Palette.SelectedBorder) else Palette.Card,
        borderColor = if (selected) Palette.SelectedBorder else Palette.Line,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Palette.Blue.face else Palette.Snow),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.code,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = if (t.code.length > 4) 10.sp else 13.sp),
                    color = if (selected) Color.White else Palette.Muted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = if (selected) Palette.Blue.edge else Palette.Ink, maxLines = 2)
                Text(t.blurb, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            if (selected) Icon(Icons.Default.Check, null, tint = Palette.Blue.face)
            if (onDelete != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Delete, "Delete ${t.code}",
                    Modifier
                        .size(26.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDelete),
                    tint = Palette.Faint,
                )
            }
        }
    }
}

/** Pop-style confirmation dialog. */
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PopButton("Cancel", onDismiss, Modifier.fillMaxWidth(), colors = Palette.Blue)
            Spacer(Modifier.height(10.dp))
            PopButton(confirm, onConfirm, Modifier.fillMaxWidth(), colors = Palette.Red)
        }
    }
}

/** Sun–Sat row with flame markers for days read this week. */
@Composable
fun WeekRow(activeDates: Set<Long>, modifier: Modifier = Modifier) {
    val now = today()
    val start = now - dayOfWeek(now)
    val names = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val day = start + i
            val active = day in activeDates
            val isToday = day == now
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    names[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) Palette.Flame else Palette.Faint,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (active) Palette.Flame else Palette.Snow)
                        .then(if (isToday && !active) Modifier.border(2.dp, Palette.Flame.copy(alpha = 0.5f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}

/** Emits a chapter's headings and paragraphs as LazyColumn items. */
fun LazyListScope.chapterItems(chapter: Chapter, textScale: Float, highlightVerse: Int? = null) {
    chapter.blocks.forEachIndexed { i, block ->
        item(key = "b$i") {
            when (block) {
                is Block.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp * textScale),
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                )
                is Block.Subtitle -> Text(
                    block.text,
                    style = TextStyle(fontFamily = ScriptureFont, fontStyle = FontStyle.Italic, fontSize = 15.sp * textScale, color = Palette.Muted),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                is Block.Paragraph -> ParagraphText(block, textScale, highlightVerse)
            }
        }
    }
}

@Composable
private fun ParagraphText(p: Block.Paragraph, textScale: Float, highlightVerse: Int?) {
    val poetry = p.verses.any { it.isPoetry }
    val highlight = Palette.Gold.face.copy(alpha = if (LocalNeutrals.current.isDark) 0.28f else 0.35f)
    val text: AnnotatedString = remember(p, highlightVerse, highlight) {
        buildAnnotatedString {
            p.verses.forEachIndexed { i, v ->
                if (i > 0) append(if (poetry) "\n" else " ")
                withStyle(
                    SpanStyle(
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Black,
                        fontSize = 0.62.em,
                        color = Palette.Blue.face,
                        baselineShift = BaselineShift(0.35f),
                    )
                ) { append("${v.number} ") }
                val start = length
                v.lines.forEachIndexed { li, line ->
                    if (li > 0) append("\n    ")
                    append(line)
                }
                if (v.number == highlightVerse) addStyle(SpanStyle(background = highlight), start, length)
            }
        }
    }
    Text(
        text,
        style = TextStyle(
            fontFamily = ScriptureFont,
            fontSize = 18.sp * textScale,
            lineHeight = 30.sp * textScale,
            color = Palette.Ink,
            textAlign = TextAlign.Start,
        ),
        modifier = Modifier.padding(bottom = 12.dp),
    )
}
