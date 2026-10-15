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
    fun detectorFiresOnceAfterThreeEqualFrames() {
        val detector = StableCodeDetector()
        val results = listOf("A", "A", null, "A", "A", "A", "A", "B").map { detector.onFrame(it)?.code }
        assertEquals(listOf(null, null, null, null, null, "A", null, null), results)
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
