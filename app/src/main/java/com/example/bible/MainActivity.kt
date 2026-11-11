package com.example.bible

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.bible.data.Reminders
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.bible.ui.components.TranslationSheet
import com.example.bible.ui.screens.CompleteScreen
import com.example.bible.ui.screens.ImportDialog
import com.example.bible.ui.screens.HomeScreen
import com.example.bible.ui.screens.LessonScreen
import com.example.bible.ui.screens.ReaderScreen
import com.example.bible.ui.screens.StreakScreen
import com.example.bible.ui.theme.BibleTheme
import com.example.bible.ui.theme.Palette
import com.example.bible.ui.theme.ThemeMode
import com.example.bible.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private var pendingOpenToday by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Reminders.EXTRA_OPEN_TODAY, false)) pendingOpenToday = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenToday = savedInstanceState == null && intent?.getBooleanExtra(Reminders.EXTRA_OPEN_TODAY, false) == true
        setContent {
            val vm: AppViewModel = viewModel()
            // Tapped the reminder notification: open today's reading.
            LaunchedEffect(pendingOpenToday) {
                if (pendingOpenToday) { vm.openToday(); pendingOpenToday = false }
            }
            val progress by vm.progress.collectAsStateWithLifecycle()
            val dark = isDarkTheme(progress.themeMode)
            // Status/navigation bar icons follow the app theme (light icons on dark, dark on light).
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = SystemBarStyle.auto(transparent, transparent) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            BibleTheme(dark, black = progress.themeMode == ThemeMode.Black) { App(vm) }
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val screen = vm.stack.last()

    BackHandler(enabled = vm.stack.size > 1 || (screen as? Screen.Home)?.tab != Tab.Path) {
        if (!vm.pop()) vm.selectTab(Tab.Path)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 12 }) togetherWith fadeOut(tween(150))
            },
            contentKey = { it::class },
            label = "screen",
        ) { s ->
            when (s) {
                // Reading screens draw edge to edge and manage system bars themselves (ImmersiveScaffold);
                // everything else stays inside the safe area.
                is Screen.Lesson -> LessonScreen(vm, progress, s.dayIndex)
                is Screen.Reader -> ReaderScreen(vm, progress, s.bookId, s.chapter, s.verse)
                else -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    when (s) {
                        is Screen.Home -> HomeScreen(vm, progress, s.tab)
                        is Screen.Complete -> CompleteScreen(vm, s.result)
                        is Screen.StreakCelebration -> StreakScreen(vm, s.result)
                        else -> Unit
                    }
                }
            }
        }

        // Overlays that cover every screen.
        val translations by vm.translations.collectAsStateWithLifecycle()
        // System file picker; any type, since XML/JSON/CSV files often have vague MIME types.
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::startImport)
        }
        TranslationSheet(
            visible = vm.translationSheetOpen,
            current = progress.translation,
            translations = translations,
            onPick = { vm.setTranslation(it); vm.translationSheetOpen = false },
            onImport = { vm.translationSheetOpen = false; importLauncher.launch(arrayOf("*/*")) },
            onDelete = { vm.deleteTranslation(it) },
            onDismiss = { vm.translationSheetOpen = false },
        )
        vm.importState?.let { state ->
            ImportDialog(state, onConfirm = vm::confirmImport, onDismiss = vm::dismissImport)
        }
    }
}
