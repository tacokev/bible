package com.example.bible

import com.example.bible.data.importer.BookNames
import com.example.bible.data.importer.ImportException
import com.example.bible.data.importer.Importer
import com.example.bible.data.importer.ParsedBible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImporterTest {
    private fun parse(text: String, name: String = "test.txt") = Importer.parse(text.toByteArray(), name)

    /** Looks a verse up in the written output format. */
    private fun ParsedBible.verse(book: String, chapter: Int, verse: Int): String? {
        val out = StringWriter().also { writeTo(it) }.toString().lines()
        var b = ""; var c = 0
        for (l in out) {
            val p = l.split('\t')
            when (p[0]) {
                "#B" -> b = p[1]
                "#C" -> c = p[1].toInt()
                "V" -> if (b == book && c == chapter && p[1].toInt() == verse) return p[2]
            }
        }
        return null
    }

    private val tenVerses = (1..10).joinToString("\n") { "Genesis,1,$it,Verse $it text" }

    @Test fun bookNames() {
        assertEquals("1CO", BookNames.resolve("I Corinthians"))
        assertEquals("1CO", BookNames.resolve("1Cor"))
        assertEquals("1CO", BookNames.resolve("1 Cor."))
        assertEquals("SNG", BookNames.resolve("Song of Solomon"))
        assertEquals("PSA", BookNames.resolve("Psalm"))
        assertEquals("PHP", BookNames.resolve("Philippians"))
        assertEquals("PHM", BookNames.resolve("Philemon"))
        assertEquals("REV", BookNames.resolve("Revelation of John"))
        assertEquals("JHN", BookNames.resolve("43"))
        assertEquals(null, BookNames.resolve("Tobit"))
    }

    @Test fun csvWithHeaderAndQuotes() {
        val b = parse("Book,Chapter,Verse,Text\nGenesis,1,1,\"In the beginning, God\"\n" + tenVerses.lines().drop(1).joinToString("\n") + "\nTobit,1,1,skip me")
        assertEquals("In the beginning, God", b.verse("GEN", 1, 1))
        assertTrue("Tobit" in b.skippedBooks)
    }

    @Test fun csvNoHeaderUnquotedCommas() {
        val b = parse("Genesis,1,1,And God said, Let there be light\n" + tenVerses.lines().drop(1).joinToString("\n"))
        assertEquals("And God said, Let there be light", b.verse("GEN", 1, 1))
    }

    @Test fun tsvWithIdColumnAndNumericBooks() {
        val rows = (1..10).joinToString("\n") { "${43003000 + it}\t43\t3\t$it\tText $it" }
        val b = parse(rows)
        assertEquals("TSV", b.format)
        assertEquals("Text 5", b.verse("JHN", 3, 5))
    }

    @Test fun referenceColumn() {
        val b = parse((1..10).joinToString("\n") { "John 3:$it\tWords $it" })
        assertEquals("Words 7", b.verse("JHN", 3, 7))
    }

    @Test fun jsonFlatVerses() {
        val b = parse("[" + (1..10).joinToString(",") { """{"book_name":"Romans","chapter":8,"verse":$it,"text":"R $it"}""" } + "]")
        assertEquals("R 3", b.verse("ROM", 8, 3))
    }

    @Test fun jsonNestedMap() {
        val b = parse("""{"Ruth":{"1":{""" + (1..10).joinToString(",") { "\"$it\":\"Ruth $it\"" } + "}}}")
        assertEquals("Ruth 9", b.verse("RUT", 1, 9))
    }

    @Test fun zefaniaSkipsNotes() {
        val b = parse("""<?xml version="1.0"?><XMLBIBLE biblename="Test Bible"><BIBLEBOOK bnumber="19"><CHAPTER cnumber="23">""" +
            (1..10).joinToString("") { """<VERS vnumber="$it">Psalm <NOTE>note</NOTE>text $it</VERS>""" } + "</CHAPTER></BIBLEBOOK></XMLBIBLE>")
        assertEquals("Psalm text 1", b.verse("PSA", 23, 1))
        assertEquals("Test Bible", b.title)
        assertEquals("TB", b.code)
    }

    @Test fun osisContainerVersesWithPoetry() {
        val verses = (1..10).joinToString("") { """<verse osisID="Ps.23.$it"><l>Line A $it</l><l>Line B<note>fn</note></l></verse>""" }
        val b = parse("""<osis><osisText osisIDWork="T"><header><work osisWork="TST"><title>Test OSIS</title></work></header><div type="book" osisID="Ps"><chapter osisID="Ps.23">$verses</chapter></div></osisText></osis>""")
        assertEquals("Line A 1|Line B", b.verse("PSA", 23, 1))
        assertEquals("TST", b.code)
        assertEquals("Test OSIS", b.title)
    }

    @Test(expected = ImportException::class) fun garbageFails() { parse("hello world this is not a bible") }

    @Test(expected = ImportException::class) fun unknownXmlFails() { parse("<html><body>nope</body></html>") }

    // ---- Real files downloaded from the internet (skipped unless IMPORT_SAMPLES is set) ----

    private fun sample(name: String): ParsedBible {
        val dir = System.getenv("IMPORT_SAMPLES")
        assumeTrue(dir != null && File(dir, name).exists())
        val b = Importer.parse(File(dir, name).readBytes(), name)
        println("SAMPLE $name: ${b.format} '${b.title}' [${b.code}] books=${b.bookCount} chapters=${b.chapterCount} verses=${b.verseCount} skipped=${b.skippedBooks.take(5)}")
        println("   JHN 3:16 = ${b.verse("JHN", 3, 16)}")
        println("   PSA 23:1 = ${b.verse("PSA", 23, 1)}")
        return b
    }

    private fun assertFullBible(b: ParsedBible) {
        assertEquals(66, b.bookCount)
        assertEquals(1189, b.chapterCount)
        assertTrue("verses ${b.verseCount}", b.verseCount in 30_900..31_200)
    }

    @Test fun realZefania() {
        val b = sample("eng-darby.zefania.xml"); assertFullBible(b)
        assertTrue(b.verse("JHN", 3, 16)!!.startsWith("For God so loved the world"))
    }

    @Test fun realZefaniaZipped() = assertFullBible(sample("darby.zip"))

    @Test fun realOsisMilestones() {
        val b = sample("eng-kjv.osis.xml"); assertFullBible(b)
        assertTrue(b.verse("JHN", 3, 16)!!.startsWith("For God so loved the world"))
        assertTrue(b.verse("GEN", 1, 1)!!.startsWith("In the beginning"))
        assertEquals("King James Version", b.title)
        assertEquals("KJV", b.code)
    }

    @Test fun realOsisPartialGzipped() {
        val b = sample("eng-us-oeb.osis.xml.gz")
        assertTrue(b.bookCount in 1..65)
        assertEquals("eng us oeb", b.title)
    }

    @Test fun realOsisGerman() {
        val b = sample("deu-luther1912.osis.xml"); assertFullBible(b)
        assertTrue(b.verse("JHN", 3, 16)!!.startsWith("Also hat Gott die Welt geliebt"))
    }

    @Test fun realJsonPositionalBooks() {
        val b = sample("en_kjv.json"); assertFullBible(b)
        assertTrue(b.verse("JHN", 3, 16)!!.contains("For God so loved the world"))
        assertTrue(b.verse("JON", 1, 1)!!.contains("Jonah"))
    }

    @Test fun realJsonNestedBooks() {
        val b = sample("YLT.json"); assertFullBible(b)
        assertEquals("Young's Literal Translation (1898)", b.title)
        assertEquals("YLT", b.code)
    }

    @Test fun realCsvWithApocrypha() {
        val b = sample("KJVA.csv"); assertFullBible(b)
        assertTrue(b.skippedBooks.isNotEmpty())
        assertTrue(b.verse("JHN", 3, 16)!!.startsWith("For God so loved the world"))
    }
}
