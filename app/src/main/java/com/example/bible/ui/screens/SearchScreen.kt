package com.example.bible.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.AppViewModel
import com.example.bible.Screen
import com.example.bible.data.PassageRef
import com.example.bible.data.Progress
import com.example.bible.data.SearchHit
import com.example.bible.data.SearchScope
import com.example.bible.data.SearchText
import com.example.bible.ui.components.AppIcons
import com.example.bible.ui.components.PopSurface
import com.example.bible.ui.components.TranslationPill
import com.example.bible.ui.theme.Nunito
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.Pop
import com.example.bible.ui.theme.ScriptureFont
import kotlinx.coroutines.delay

private val suggestions = listOf("love", "John 3:16", "shepherd", "Psalm 23", "faith hope", "Romans 8", "peace", "light of the world")

@Composable
fun SearchScreen(vm: AppViewModel, progress: Progress, openPicker: () -> Unit) {
    val query = vm.searchQuery
    val scope = vm.searchScope
    val results = vm.searchResults
    var loading by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val reference = remember(query) { SearchText.parseReference(query) }

    // Restarts on every change, which both debounces typing and cancels stale searches.
    LaunchedEffect(query, scope, progress.translation) {
        if (query.isBlank()) { vm.searchResults = null; loading = false; return@LaunchedEffect }
        loading = true
        delay(250)
        vm.searchResults = vm.search(query, scope)
        loading = false
    }

    fun open(bookId: String, chapter: Int, verse: Int?) {
        keyboard?.hide()
        focus.clearFocus()
        vm.push(Screen.Reader(bookId, chapter, verse))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TranslationPill(progress.translation, openPicker)
        }
        HorizontalDivider(color = Palette.Line, thickness = 2.dp)

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SearchField(
                value = query,
                onValueChange = { vm.searchQuery = it },
                onSearch = { keyboard?.hide() },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchScope.entries.forEach { s ->
                    ScopeChip(
                        text = if (s == SearchScope.All) "All" else if (s == SearchScope.Old) "Old" else "New",
                        selected = s == scope,
                        modifier = Modifier.weight(1f),
                    ) { vm.searchScope = s }
                }
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(results?.query, scope) { listState.scrollToItem(0) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            if (reference != null) {
                item(key = "ref") { ReferenceCard(reference) { open(reference.bookId, reference.chapter, reference.verse) } }
            }
            when {
                query.isBlank() -> item(key = "empty") { Suggestions { vm.searchQuery = it } }
                loading && results?.query != query && reference == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Green.face, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                    }
                }
                results != null && results.total == 0 && reference == null -> item(key = "none") { NoResults(query) }
                results != null && results.total > 0 -> {
                    item(key = "count") {
                        Text(
                            buildString {
                                append(if (results.total == 1) "1 verse" else "%,d verses".format(results.total))
                                if (results.total > results.hits.size) append(" · showing first ${results.hits.size}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Faint,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                    val terms = SearchText.terms(results.query)
                    items(results.hits, key = { "${it.bookId}.${it.chapter}.${it.verse}" }) { hit ->
                        HitRow(hit, terms, progress.textScale) { open(hit.bookId, hit.chapter, hit.verse) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Snow)
            .border(2.dp, Palette.Line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, null, Modifier.size(24.dp), tint = Color.Unspecified)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Words or a reference like John 3:16", style = MaterialTheme.typography.bodyLarge, color = Palette.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Blue.face),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close, "Clear",
                Modifier
                    .size(24.dp)
                    .clickableNoRipple { onValueChange("") },
                tint = Palette.Faint,
            )
        }
    }
}

@Composable
private fun ScopeChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = if (selected) Pop(Palette.Selected, Palette.SelectedBorder) else Palette.Card,
        borderColor = if (selected) Palette.SelectedBorder else Palette.Line,
        shape = RoundedCornerShape(12.dp),
        depth = 3.dp,
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Blue.face else Palette.Faint)
    }
}

@Composable
private fun ReferenceCard(ref: PassageRef, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        colors = Palette.Green,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GO TO", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Text(ref.label, style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(32.dp), tint = Color.White)
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, terms: List<String>, textScale: Float, onClick: () -> Unit) {
    val highlight = Palette.Blue.face
    val text = remember(hit, terms, highlight) {
        buildAnnotatedString {
            append(hit.text)
            SearchText.matchRanges(hit.text, terms).forEach {
                addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold), it.first, it.last + 1)
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(hit.reference, style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = TextStyle(fontFamily = ScriptureFont, fontSize = 17.sp * textScale, lineHeight = 26.sp * textScale, color = Palette.Ink),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider(color = Palette.Line, thickness = 1.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text("Try searching for", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestions.forEach { s ->
                PopSurface(
                    onClick = { onPick(s) },
                    colors = Palette.Card,
                    borderColor = Palette.Line,
                    shape = RoundedCornerShape(14.dp),
                    depth = 3.dp,
                ) {
                    Text(s, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Nunito), color = Palette.Blue.face, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Search, null, Modifier.size(64.dp), tint = Color.Unspecified)
        Spacer(Modifier.height(12.dp))
        Text("No verses found", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nothing matched “$query”. Try fewer words or another translation.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
    }
}
