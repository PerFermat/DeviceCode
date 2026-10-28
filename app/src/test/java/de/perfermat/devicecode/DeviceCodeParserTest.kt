package de.perfermat.devicecode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCodeParserTest {

    @Test
    fun findsCodeInSurroundingText() {
        val text = "Gehe zu google.com/device\nund gib ABC-DEF-GHI ein"
        assertEquals("ABC-DEF-GHI", DeviceCodeParser.find(text))
    }

    @Test
    fun acceptsSpacesAndFourCharacterGroups() {
        assertEquals("ABCD-EFG-HIJK", DeviceCodeParser.find("ABCD EFG – HIJK"))
    }

    @Test
    fun fixesTypicalOcrConfusions() {
        assertEquals("OIS-BZG-IAB", DeviceCodeParser.find("0I5-8ZG-|AB"))
    }

    @Test
    fun ignoresNonCodes() {
        assertNull(DeviceCodeParser.find("1080 720 480"))
        assertNull(DeviceCodeParser.find("ABC-DEF"))
        assertNull(DeviceCodeParser.find("ABCDE-FGH-IJK"))
        assertNull(DeviceCodeParser.find("ABC\nDEF\nGHI"))
        assertNull(DeviceCodeParser.find("Abc-Def-Ghi"))
    }

    @Test
    fun suggestsCodeLikeStrings() {
        listOf("12345678", "AB12CD", "QXRTZK", "A1B2", "ABCD-1234").forEach {
            assertEquals(it, true, DeviceCodeParser.isCodeLike(it))
        }
    }

    @Test
    fun doesNotSuggestWords() {
        listOf("YOUTUBE", "NETFLIX", "ANMELDEN", "1080P", "H264", "2026", "Hallo", "HTTPS", "OK", "4K").forEach {
            assertEquals(it, false, DeviceCodeParser.isCodeLike(it))
        }
    }

    @Test
    fun detectorFiresOnceAfterThreeEqualFrames() {
        val detector = StableCodeDetector()
        val results = listOf("A", "A", null, "A", "A", "A", "A", "B").map(detector::onFrame)
        assertEquals(listOf(null, null, null, null, null, "A", null, null), results)
    }
}
