package com.example.bible

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.bible.ui.theme.BibleTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ImmersiveTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: AppViewModel

    @Before fun setUp() {
        vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { BibleTheme { App(vm) } }
    }

    private fun waitForText() {
        compose.waitUntil(5000) { compose.onAllNodes(androidx.compose.ui.test.hasText("CONTINUE")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        Thread.sleep(500); compose.waitForIdle()
    }

    private fun swipe(up: Boolean) {
        compose.onRoot().performTouchInput {
            if (up) swipeUp(startY = centerY + 200f, endY = centerY - 200f, durationMillis = 400)
            else swipeDown(startY = centerY - 200f, endY = centerY + 200f, durationMillis = 400)
        }
        compose.waitForIdle()
    }

    @Test fun barsHideOnScrollDownAndReturnOnScrollUp() {
        vm.push(Screen.Lesson(0)) // Genesis 1 is long enough to scroll
        waitForText()
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()

        swipe(up = true) // finger up = reading further down
        compose.onNodeWithText("CONTINUE").assertIsNotDisplayed()

        swipe(up = false)
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
    }

    @Test fun tapTogglesBars() {
        vm.push(Screen.Lesson(0))
        waitForText()
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithText("CONTINUE").assertIsNotDisplayed()
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
    }

    @Test fun barsReturnAtEndOfChapter() {
        vm.push(Screen.Lesson(0))
        waitForText()
        repeat(25) { swipe(up = true) } // keep reading past the end
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
    }
}
