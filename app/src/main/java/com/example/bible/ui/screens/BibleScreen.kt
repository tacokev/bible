package com.example.bible.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bible.AppViewModel
import com.example.bible.Screen
import com.example.bible.data.Book
import com.example.bible.data.Books
import com.example.bible.data.Chapter
import com.example.bible.data.Progress
import com.example.bible.ui.components.ImmersiveScaffold
import com.example.bible.ui.components.PopSurface
import com.example.bible.ui.components.TranslationPill
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop

@Composable
fun BibleScreen(vm: AppViewModel, progress: Progress, openPicker: () -> Unit) {
    var newTestament by rememberSaveable { mutableStateOf(false) }
    var bookId by rememberSaveable { mutableStateOf<String?>(null) }
    val book = bookId?.let { Books[it] }

    BackHandler(enabled = book != null) { bookId = null }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (book != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    Modifier
                        .size(30.dp)
                        .clickableNoRipple { bookId = null },
                    tint = Palette.Faint,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(book?.name ?: "Bible", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TranslationPill(progress.translation, openPicker)
        }
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)

        if (book == null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(2) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SegmentButton("Old Testament", !newTestament, Modifier.weight(1f)) { newTestament = false }
                        SegmentButton("New Testament", newTestament, Modifier.weight(1f)) { newTestament = true }
                    }
                }
                items(if (newTestament) Books.newTestament else Books.oldTestament, key = { it.id }) { b ->
                    BookTile(b) { bookId = b.id }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items((1..book.chapters).toList()) { n ->
                    PopSurface(
                        onClick = { vm.push(Screen.Reader(book.id, n)) },
                        modifier = Modifier.aspectRatio(1f),
                        colors = Palette.Card,
                        borderColor = Palette.Line,
                    ) {
                        Text("$n", style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = if (selected) Pop(Palette.Selected, Palette.SelectedBorder) else Palette.Card,
        borderColor = if (selected) Palette.SelectedBorder else Palette.Line,
        depth = 3.dp,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Blue.face else Palette.Faint)
    }
}

@Composable
private fun BookTile(book: Book, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = Palette.Card,
        borderColor = Palette.Line,
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(book.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (book.chapters == 1) "1 chapter" else "${book.chapters} chapters",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Faint,
            )
        }
    }
}

@Composable
fun ReaderScreen(vm: AppViewModel, progress: Progress, bookId: String, number: Int, verse: Int? = null) {
    val book = Books[bookId]
    val chapter by produceState<Chapter?>(null, bookId, number, progress.translation) {
        value = null
        value = vm.chapter(bookId, number)
    }
    // Next/previous chapter, crossing into neighbouring books.
    val idx = Books.indexOf(bookId)
    val prev = when {
        number > 1 -> bookId to number - 1
        idx > 0 -> Books.all[idx - 1].let { it.id to it.chapters }
        else -> null
    }
    val next = when {
        number < book.chapters -> bookId to number + 1
        idx < Books.all.lastIndex -> Books.all[idx + 1].id to 1
        else -> null
    }

    ImmersiveScaffold(
        resetKey = bookId to number,
        topBar = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                Modifier
                    .size(30.dp)
                    .clickableNoRipple { vm.pop() },
                tint = Palette.Faint,
            )
            Spacer(Modifier.width(10.dp))
            Spacer(Modifier.weight(1f))
            Text("${book.name.uppercase()} · $number / ${book.chapters}", style = MaterialTheme.typography.labelLarge, color = Palette.Faint)
        }
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)
        },
        bottomBar = {
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavPop(Icons.AutoMirrored.Filled.KeyboardArrowLeft, prev != null, Modifier.weight(1f)) {
                prev?.let { vm.replaceTop(Screen.Reader(it.first, it.second)) }
            }
            NavPop(Icons.AutoMirrored.Filled.KeyboardArrowRight, next != null, Modifier.weight(1f)) {
                next?.let { vm.replaceTop(Screen.Reader(it.first, it.second)) }
            }
        }
        },
    ) { padding ->
        ChapterPage(null, chapter, progress.textScale, progress.translation.code, highlightVerse = verse, padding = padding)
    }
}

@Composable
private fun NavPop(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = if (enabled) Palette.Card else Palette.Locked,
        borderColor = if (enabled) Palette.Line else null,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icon, null, Modifier.size(32.dp), tint = if (enabled) Palette.Blue.face else Palette.Faint)
    }
}
