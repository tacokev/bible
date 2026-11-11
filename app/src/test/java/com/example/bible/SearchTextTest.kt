package com.example.bible

import com.example.bible.data.PassageRef
import com.example.bible.data.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {
    private fun ref(q: String) = SearchText.parseReference(q)

    @Test fun fullReference() = assertEquals(PassageRef("JHN", 3, 16), ref("John 3:16"))
    @Test fun chapterOnly() = assertEquals(PassageRef("PSA", 23, null), ref("Psalm 23"))
    @Test fun numberedBook() = assertEquals(PassageRef("1CO", 13, 4), ref("1 cor 13:4"))
    @Test fun numberedNoSpace() = assertEquals(PassageRef("1CO", 13, null), ref("1cor 13"))
    @Test fun abbreviation() = assertEquals(PassageRef("GEN", 1, null), ref("gen 1"))
    @Test fun alias() = assertEquals(PassageRef("JHN", 1, 1), ref("jn 1.1"))
    @Test fun songOfSongs() = assertEquals(PassageRef("SNG", 2, null), ref("song of songs 2"))
    @Test fun chapterOutOfRange() = assertNull(ref("Jude 2"))
    @Test fun plainWords() = assertNull(ref("love one another"))
    @Test fun ambiguousSingleLetter() = assertNull(ref("j 3"))

    @Test fun termsNormalize() = assertEquals(listOf("lord's", "light"), SearchText.terms("  Lord’s,  LIGHT. "))

    @Test fun rangesCaseInsensitive() =
        assertEquals(listOf(4..8, 17..20), SearchText.matchRanges("The Light of the Lord", listOf("light", "lord")))

    @Test fun shortWordsMustBeWholeWords() =
        assertEquals(listOf(14..16), SearchText.matchRanges("delighting in the there", listOf("the")))

    @Test fun longWordsMatchPrefix() =
        assertEquals(listOf(4..11), SearchText.matchRanges("the shepherds", listOf("shepherd")))

    @Test fun noMidWordMatch() =
        assertEquals(emptyList<IntRange>(), SearchText.matchRanges("delighting", listOf("light")))
}
