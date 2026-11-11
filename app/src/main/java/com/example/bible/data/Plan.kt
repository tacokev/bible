package com.example.bible.data

import android.content.Context

/** A contiguous run of chapters within one book, e.g. Genesis 1–3. */
data class Reading(val bookId: String, val from: Int, val to: Int) {
    val book get() = Books[bookId]
    val chapters get() = (from..to).map { bookId to it }
    val label get() = if (from == to) "${book.name} $from" else "${book.name} $from–$to"
}

data class PlanDay(val index: Int, val readings: List<Reading>) {
    val dayNumber get() = index + 1
    val chapters get() = readings.flatMap { it.chapters }
    val summary get() = readings.joinToString(" · ") { it.label }
}

data class PlanUnit(val index: Int, val days: List<PlanDay>, val title: String, val subtitle: String)

/**
 * One-year plan: every day has an Old Testament portion, plus New Testament and
 * Psalms/Proverbs portions spread across the year. Balanced by word count so each day
 * takes roughly the same time (~12–15 minutes). Generated offline into assets/plan.txt.
 */
class ReadingPlan(val days: List<PlanDay>) {
    val units: List<PlanUnit> = buildUnits()

    private fun buildUnits(): List<PlanUnit> {
        val count = 12
        return (0 until count).map { u ->
            val start = u * days.size / count
            val end = (u + 1) * days.size / count
            val unitDays = days.subList(start, end)
            val books = unitDays.flatMap { d -> d.readings.map { it.bookId } }.distinct()
            val ot = books.filter { !Books[it].isNewTestament && it != "PSA" && it != "PRO" }
            val nt = books.filter { Books[it].isNewTestament }
            val title = when {
                ot.size == 1 -> Books[ot.first()].name
                ot.isNotEmpty() -> "${Books[ot.first()].name} to ${Books[ot.last()].name}"
                else -> "Reading"
            }
            val subtitle = buildString {
                append("Days ${start + 1}–$end")
                if (nt.isNotEmpty()) append(" · ${Books[nt.first()].name}" + if (nt.size > 1) " to ${Books[nt.last()].name}" else "")
            }
            PlanUnit(u, unitDays, title, subtitle)
        }
    }

    fun unitOf(dayIndex: Int) = units.first { u -> u.days.any { it.index == dayIndex } }

    companion object {
        fun load(context: Context): ReadingPlan {
            val lines = context.assets.open("plan.txt").bufferedReader().readLines().filter { it.isNotBlank() }
            return ReadingPlan(lines.mapIndexed { i, line -> PlanDay(i, line.split(';').map(::parseReading)) })
        }

        private fun parseReading(s: String): Reading {
            val (book, range) = s.trim().split(' ')
            val parts = range.split('-').map { it.toInt() }
            return Reading(book, parts.first(), parts.last())
        }
    }
}
