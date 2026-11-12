package com.example.bible.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.AppViewModel
import com.example.bible.data.Chapter
import com.example.bible.data.HighlightColor
import com.example.bible.data.VerseMark
import com.example.bible.ui.theme.Palette

/** What a reading screen lets you do with a verse. */
class VerseActions(
    val marks: Map<Int, VerseMark>,
    val onHighlight: (verse: Int, HighlightColor?) -> Unit,
    val onNote: (verse: Int, String?) -> Unit,
    val onReadFrom: (verse: Int) -> Unit,
)

/** Wires [VerseActions] for one chapter to the app's saved highlights/notes. */
@Composable
fun rememberVerseActions(vm: AppViewModel, bookId: String, chapter: Int, onReadFrom: (Int) -> Unit): VerseActions {
    val all by vm.annotations.marks.collectAsStateWithLifecycle()
    val marks = remember(all, bookId, chapter) { vm.annotations.forChapter(all, bookId, chapter) }
    return VerseActions(
        marks = marks,
        onHighlight = { v, c -> vm.annotations.setHighlight(bookId, chapter, v, c) },
        onNote = { v, n -> vm.annotations.setNote(bookId, chapter, v, n) },
        onReadFrom = onReadFrom,
    )
}

/**
 * Small menu shown next to a long-pressed verse: read aloud from here, highlight colours,
 * note, copy. [anchor] is the verse's area in window coordinates.
 */
@Composable
fun VerseMenu(
    chapter: Chapter,
    verse: Int,
    anchor: Rect,
    translationCode: String,
    actions: VerseActions,
    onEditNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val topSafe = with(LocalDensity.current) { 56.dp.roundToPx() }
    val mark = actions.marks[verse]
    val position = remember(anchor) { NextToVerse(anchor, margin, topSafe) }

    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .popIn()
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Background)
                .border(2.dp, Palette.Line, RoundedCornerShape(18.dp))
                .padding(12.dp)
                .width(300.dp),
        ) {
            Text(
                "${chapter.book.name.uppercase()} ${chapter.number}:$verse",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Faint,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            // Highlight colours; tapping the current one again (or ✕) clears it.
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HighlightColor.entries.forEach { color ->
                    val chosen = mark?.highlight == color
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.background().copy(alpha = 1f))
                            .border(if (chosen) 3.dp else 2.dp, if (chosen) Palette.Ink else Palette.Line, CircleShape)
                            .clickable {
                                actions.onHighlight(verse, if (chosen) null else color)
                                onDismiss()
                            },
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(2.dp, Palette.Line, CircleShape)
                        .clickable(enabled = mark?.highlight != null) {
                            actions.onHighlight(verse, null)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close, "Remove highlight", Modifier.size(20.dp),
                        tint = if (mark?.highlight != null) Palette.Muted else Palette.Line,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuButton(Icons.Default.PlayArrow, "Read from here", Modifier.weight(1.4f)) {
                    actions.onReadFrom(verse)
                    onDismiss()
                }
                MenuButton(Icons.Default.Create, if (mark?.note != null) "Edit note" else "Note", Modifier.weight(1f)) {
                    onEditNote()
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuButton(null, "Copy", Modifier.fillMaxWidth()) {
                copyVerse(context, chapter, verse, translationCode)
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuButton(icon: ImageVector?, label: String, modifier: Modifier, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = Palette.Card,
        borderColor = Palette.Line,
        shape = RoundedCornerShape(12.dp),
        depth = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp), tint = Palette.Blue.face)
                Spacer(Modifier.width(6.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Blue.face, maxLines = 1)
        }
    }
}

private fun copyVerse(context: Context, chapter: Chapter, verse: Int, translationCode: String) {
    val v = chapter.blocks.filterIsInstance<com.example.bible.data.Block.Paragraph>()
        .flatMap { it.verses }.firstOrNull { it.number == verse } ?: return
    val text = "${v.lines.joinToString(" ")}\n— ${chapter.book.name} ${chapter.number}:$verse ($translationCode)"
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("${chapter.book.name} ${chapter.number}:$verse", text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Verse copied", Toast.LENGTH_SHORT).show()
}

/** Places the menu above the verse (or below it if there's no room), within the screen. */
private class NextToVerse(private val anchor: Rect, private val margin: Int, private val topSafe: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = ((anchor.left + anchor.right) / 2 - popupContentSize.width / 2).toInt()
            .coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
        val above = anchor.top.toInt() - popupContentSize.height - margin
        val below = anchor.bottom.toInt() + margin
        val y = when {
            above >= topSafe -> above
            below + popupContentSize.height <= windowSize.height - margin -> below
            else -> (windowSize.height - popupContentSize.height) / 2
        }
        return IntOffset(x, y)
    }
}

/** Write or edit the note on a verse. */
@Composable
fun NoteDialog(chapter: Chapter, verse: Int, initial: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val verseText = remember(chapter, verse) {
        chapter.blocks.filterIsInstance<com.example.bible.data.Block.Paragraph>()
            .flatMap { it.verses }.firstOrNull { it.number == verse }?.lines?.joinToString(" ").orEmpty()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .imePadding()
                .popIn()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Background)
                .padding(20.dp),
        ) {
            Text("${chapter.book.name} ${chapter.number}:$verse", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(verseText, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Snow)
                    .border(2.dp, Palette.Line, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Blue.face),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                if (text.isEmpty()) Text("Write a note…", style = MaterialTheme.typography.bodyLarge, color = Palette.Faint)
            }
            Spacer(Modifier.height(16.dp))
            PopButton("Save", { onSave(text) }, Modifier.fillMaxWidth(), colors = Palette.Blue)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PopButton("Cancel", onDismiss, Modifier.weight(1f), colors = Palette.Card, textColor = Palette.Muted)
                if (initial != null) {
                    PopButton("Delete", { onSave(null) }, Modifier.weight(1f), colors = Palette.Card, textColor = Palette.Red.face)
                }
            }
        }
    }
}
