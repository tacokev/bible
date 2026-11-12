package com.example.bible.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small, hand-drawn multi-colour icons used for the stats bar and celebrations. */
object AppIcons {
    private fun icon(name: String, vararg paths: Pair<String, Color>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { (d, c) -> addPath(pathData = addPathNodes(d), fill = SolidColor(c)) }
        }.build()

    // Mirror-symmetric about x = 12: a pointed tip with two small side flicks.
    private const val FLAME_OUTER =
        "M12 1.5C13.6 4.6 16.4 6.6 17.2 9.4C17.9 8.6 18.3 7.6 18.4 6.6C19.9 8.6 20.5 11 20.5 13.6" +
            "C20.5 18.6 16.7 22.5 12 22.5C7.3 22.5 3.5 18.6 3.5 13.6C3.5 11 4.1 8.6 5.6 6.6" +
            "C5.7 7.6 6.1 8.6 6.8 9.4C7.6 6.6 10.4 4.6 12 1.5Z"
    private const val FLAME_INNER =
        "M12 11.5C13.3 13.4 15.6 15.2 15.6 17.8C15.6 19.9 14 21.5 12 21.5C10 21.5 8.4 19.9 8.4 17.8C8.4 15.2 10.7 13.4 12 11.5Z"

    val Flame = icon("flame", FLAME_OUTER to Color(0xFFFF9600), FLAME_INNER to Color(0xFFFFC800))

    val Book = icon(
        "book",
        "M2.5 5.2C5.3 3.9 8.6 4 11.2 5.6V20.4C8.6 18.9 5.3 18.8 2.5 20Z" to Color.White,
        "M12.8 5.6C15.4 4 18.7 3.9 21.5 5.2V20C18.7 18.8 15.4 18.9 12.8 20.4Z" to Color.White,
    )

    val BookColor = icon(
        "book_color",
        "M2.5 5.2C5.3 3.9 8.6 4 11.2 5.6V20.4C8.6 18.9 5.3 18.8 2.5 20Z" to Color(0xFF1CB0F6),
        "M12.8 5.6C15.4 4 18.7 3.9 21.5 5.2V20C18.7 18.8 15.4 18.9 12.8 20.4Z" to Color(0xFF1899D6),
    )

    val Path = icon(
        "path",
        "M12 2.5A3.2 3.2 0 1 1 12 8.9A3.2 3.2 0 1 1 12 2.5Z" to Color(0xFF58CC02),
        "M6 9.5A3.2 3.2 0 1 1 6 15.9A3.2 3.2 0 1 1 6 9.5Z" to Color(0xFFFFC800),
        "M13 15.5A3.2 3.2 0 1 1 13 21.9A3.2 3.2 0 1 1 13 15.5Z" to Color(0xFF1CB0F6),
    )

    val Search = icon(
        "search",
        "M10.5 2.5A8 8 0 1 1 10.5 18.5A8 8 0 1 1 10.5 2.5Z" to Color(0xFFFFC800),
        "M10.5 5.5A5 5 0 1 1 10.5 15.5A5 5 0 1 1 10.5 5.5Z" to Color(0xFFFFF4CC),
        "M15.6 17.7L17.7 15.6L22 19.9C22.6 20.5 22.6 21.4 22 22C21.4 22.6 20.5 22.6 19.9 22Z" to Color(0xFFE5A400),
    )

    val Person = icon(
        "person",
        "M12 2.5A4.5 4.5 0 1 1 12 11.5A4.5 4.5 0 1 1 12 2.5Z" to Color(0xFFCE82FF),
        "M3.5 21.5C3.5 16.5 7.3 13.5 12 13.5C16.7 13.5 20.5 16.5 20.5 21.5Z" to Color(0xFFA568CC),
    )

    val Trophy = icon(
        "trophy",
        "M6 3H18V9.5C18 12.8 15.3 15.5 12 15.5C8.7 15.5 6 12.8 6 9.5Z" to Color.White,
        "M10.5 15H13.5V18.5H10.5Z" to Color.White,
        "M7.5 18.5H16.5V21.5H7.5Z" to Color.White,
        "M6 5H2.5V7.5C2.5 9.5 4 11 6 11Z" to Color.White,
        "M18 5H21.5V7.5C21.5 9.5 20 11 18 11Z" to Color.White,
    )

    val Speaker = icon(
        "speaker",
        "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" to Color.White,
    )

    val Pause = icon(
        "pause",
        "M7.5 5h2a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1-1.5 1.5h-2A1.5 1.5 0 0 1 6 17.5v-11A1.5 1.5 0 0 1 7.5 5zM14.5 5h2A1.5 1.5 0 0 1 18 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-2a1.5 1.5 0 0 1-1.5-1.5v-11A1.5 1.5 0 0 1 14.5 5z" to Color.White,
    )
}
