package com.example.bible.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bible.data.Narrator
import com.example.bible.ui.theme.Palette

/** Bottom-bar button that starts/pauses reading aloud. */
@Composable
fun SpeakButton(playing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PopSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = if (playing) Palette.Blue else Palette.Card,
        borderColor = if (playing) null else Palette.Line,
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(
            if (playing) AppIcons.Pause else AppIcons.Speaker,
            if (playing) "Pause reading aloud" else "Read aloud",
            Modifier.size(28.dp),
            tint = if (playing) Color.White else Palette.Blue.face,
        )
    }
}

/**
 * Stops narration when leaving a reading screen (but not on rotation, and not when the
 * screen locks or the app goes to the background; that's what [Narrator] is for).
 */
@Composable
fun NarrationLifecycle(narrator: Narrator) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(narrator) {
        onDispose { if (activity?.isChangingConfigurations != true) narrator.stop() }
    }
}
