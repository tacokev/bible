package com.example.bible

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.bible.data.CompletionResult
import com.example.bible.data.today
import com.example.bible.ui.theme.BibleTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders key screens to PNGs (build/screenshots) for visual review. Not a regression test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: AppViewModel

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val t = today()
        app.getSharedPreferences("progress", Context.MODE_PRIVATE).edit()
            .putStringSet("completed", (0 until 12).map { "$it" }.toSet())
            .putStringSet("active", (1..5).map { "${t - it}" }.toSet())
            .putInt("longest", 9)
            .commit()
        vm = AppViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val p = vm.progress.collectAsState().value
            BibleTheme(com.example.bible.ui.theme.isDarkTheme(p.themeMode), p.themeMode == com.example.bible.ui.theme.ThemeMode.Black) { App(vm) }
        }
    }

    private fun shot(name: String, ms: Long = 2500) {
        compose.mainClock.advanceTimeBy(ms)
        Thread.sleep(300) // let chapter loading finish off the main thread
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun path() = shot("1_path")

    @Test fun lesson() { vm.push(Screen.Lesson(12)); shot("3_lesson") }

    @Test fun complete() {
        val r = CompletionResult(12, 2, 5, 6, true, 13, (0..5).map { today() - it }.toSet())
        vm.push(Screen.Complete(r)); shot("4_complete", 600)
    }

    @Test fun streak() {
        val r = CompletionResult(12, 2, 5, 6, true, 13, (0..5).map { today() - it }.toSet())
        vm.push(Screen.StreakCelebration(r)); shot("5_streak")
    }

    @Test fun bible() { vm.selectTab(Tab.Bible); shot("6_bible") }

    @Test fun reader() { vm.push(Screen.Reader("PSA", 23)); shot("7_reader") }

    @Test fun profile() { vm.selectTab(Tab.Profile); shot("8_profile") }

    @Test @Config(qualifiers = "+night") fun darkPath() = shot("d1_path")
    @Test @Config(qualifiers = "+night") fun darkLesson() { vm.push(Screen.Lesson(12)); shot("d3_lesson") }
    @Test @Config(qualifiers = "+night") fun darkComplete() {
        val r = CompletionResult(12, 2, 5, 6, true, 13, (0..5).map { today() - it }.toSet())
        vm.push(Screen.Complete(r)); shot("d4_complete", 600)
    }
    @Test @Config(qualifiers = "+night") fun darkStreak() {
        val r = CompletionResult(12, 2, 5, 6, true, 13, (0..5).map { today() - it }.toSet())
        vm.push(Screen.StreakCelebration(r)); shot("d5_streak")
    }
    @Test @Config(qualifiers = "+night") fun darkBible() { vm.selectTab(Tab.Bible); shot("d6_bible") }
    @Test @Config(qualifiers = "+night") fun darkProfile() { vm.selectTab(Tab.Profile); shot("d8_profile") }

    @Test fun search() {
        vm.selectTab(Tab.Search); vm.searchQuery = "shepherd"; shot("9_search")
    }
    @Test fun searchEmpty() { vm.selectTab(Tab.Search); shot("9_search_empty") }
    @Test fun searchRef() { vm.selectTab(Tab.Search); vm.searchQuery = "john 3:16"; shot("9_search_ref") }
    @Test @Config(qualifiers = "+night") fun darkSearch() {
        vm.selectTab(Tab.Search); vm.searchQuery = "light of the world"; shot("d9_search")
    }
    @Test fun readerHighlight() {
        vm.push(Screen.Reader("JHN", 3, 16)); shot("9_reader_verse_warm"); Thread.sleep(1500); shot("9_reader_verse")
    }

    private fun darby() = com.example.bible.data.importer.Importer.parse(
        java.io.File(System.getenv("IMPORT_SAMPLES") ?: "/nonexistent", "darby.zip").also { org.junit.Assume.assumeTrue(it.exists()) }.readBytes(),
        "darby.zip",
    )

    private fun screen(name: String) {
        compose.mainClock.advanceTimeBy(2500); Thread.sleep(300); compose.mainClock.advanceTimeBy(2500)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/screenshots/$name.png")
    }

    @Test fun importPreview() {
        vm.importState = AppViewModel.ImportState.Preview(darby(), "darby.zip"); screen("10_import_preview")
    }
    @Test @Config(qualifiers = "+night") fun importPreviewKjva() {
        val f = java.io.File(System.getenv("IMPORT_SAMPLES") ?: "/nonexistent", "KJVA.csv"); org.junit.Assume.assumeTrue(f.exists())
        vm.importState = AppViewModel.ImportState.Preview(com.example.bible.data.importer.Importer.parse(f.readBytes(), "KJVA.csv"), "KJVA.csv")
        screen("d10_import_preview_warn")
    }
    @Test fun importEndToEnd() {
        val t = vm.library.add(darby(), "Darby Bible", "DARBY")
        vm.setTranslation(t)
        vm.push(Screen.Reader("JHN", 3, 16))
        shot("11_imported_reader")
        Thread.sleep(1500)
        shot("11_imported_reader")
    }
    @Test fun sheetWithImported() {
        val t = vm.library.add(darby(), "Darby Bible", "DARBY")
        vm.setTranslation(t)
        vm.selectTab(Tab.Bible)
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(androidx.compose.ui.test.hasText("DARBY")).performClick()
        screen("12_sheet")
    }

    private fun black() {
        vm.setThemeMode(com.example.bible.ui.theme.ThemeMode.Black)
    }
    @Test fun blackPath() { black(); shot("b1_path") }
    @Test fun blackLesson() { black(); vm.push(Screen.Lesson(12)); shot("b3_lesson") }
    @Test fun blackProfile() { black(); vm.selectTab(Tab.Profile); shot("b8_profile") }
    @Test fun blackSearch() { black(); vm.selectTab(Tab.Search); vm.searchQuery = "shepherd"; shot("b9_search") }
}
