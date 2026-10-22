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
    fun findsUrls() {
        assertEquals("https://google.com/device", DeviceCodeParser.findUrl("Gehe zu google.com/device und gib"))
        assertEquals("https://www.google.com/device", DeviceCodeParser.findUrl("Gehe zu www.Google.com/device."))
        assertEquals("https://www.netflix.com/tv8", DeviceCodeParser.findUrl("Besuche www.netflix.com/tv8"))
        assertEquals("https://plex.tv", DeviceCodeParser.findUrl("öffne https://plex.tv/"))
    }

    @Test
    fun ignoresThingsThatAreNoUrls() {
        assertNull(DeviceCodeParser.findUrl("z.B. Version 1.2 oder google.com"))
        assertNull(DeviceCodeParser.findUrl("mail an jemand@example.com/x"))
        assertNull(DeviceCodeParser.findUrl("ABC-DEF-GHI"))
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
        val results = listOf("A", "A", null, "A", "A", "A", "A", "B").map { detector.onFrame(it)?.code }
        assertEquals(listOf(null, null, null, null, null, "A", null, null), results)
    }

    @Test
    fun snapsMisreadUrlsToKnownOnes() {
        val known = listOf("https://www.google.com/device", "https://netflix.com/tv8")
        assertEquals("https://www.google.com/device", DeviceCodeParser.snapToKnown("https://google.com/devlce", known))
        assertEquals("https://www.google.com/device", DeviceCodeParser.snapToKnown("https://gooqle.com/device", known))
        assertEquals("https://netflix.com/tv8", DeviceCodeParser.snapToKnown("https://www.netflix.com/tvB", known))
        assertEquals("https://plex.tv/link", DeviceCodeParser.snapToKnown("https://plex.tv/link", known))
    }

    @Test
    fun detectorPrefersMostFrequentUrl() {
        val detector = StableCodeDetector()
        detector.onFrame("B", "https://google.com/device")
        detector.onFrame("B", "https://google.com/devlce")
        assertEquals(Detection("B", "https://google.com/device"), detector.onFrame("B", "https://google.com/device"))
    }

    @Test
    fun detectorKeepsUrlSeenDuringStreak() {
        val detector = StableCodeDetector()
        detector.onFrame("A", "https://x.com/old")
        detector.onFrame("B", null)
        detector.onFrame("B", "https://y.com/new")
        assertEquals(Detection("B", "https://y.com/new"), detector.onFrame("B", null))
    }
}
