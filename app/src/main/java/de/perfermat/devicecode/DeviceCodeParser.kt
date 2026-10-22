package de.perfermat.devicecode

/**
 * Sucht im OCR-Text nach einem Geräte-Code wie "ABC-DEF-GHI": drei Gruppen à 3–4 Zeichen,
 * getrennt durch Bindestrich oder Leerzeichen.
 */
object DeviceCodeParser {

    private const val GROUP = "[A-Z0-9|]{3,4}"

    // Bindestrich (auch die typografischen Varianten, die OCR gern liefert) oder Leerzeichen.
    // Zeilenumbrüche zählen bewusst nicht, sonst verschmelzen fremde Zeilen zu einem "Code".
    private const val SEPARATOR = "(?:[ \\t]*[-‐‑–—][ \\t]*|[ \\t]+)"

    private val pattern = Regex(
        "(?<![A-Za-z0-9|])($GROUP)$SEPARATOR($GROUP)$SEPARATOR($GROUP)(?![A-Za-z0-9|])"
    )

    // Google-Codes bestehen praktisch nur aus Buchstaben – im Zweifel also den Buchstaben nehmen.
    private val ocrFixes = mapOf(
        '0' to 'O', '1' to 'I', '|' to 'I', '2' to 'Z', '5' to 'S', '6' to 'G', '8' to 'B',
    )

    // Adressen wie "google.com/device", "www.netflix.com/tv8" oder "https://plex.tv/link".
    private val urlPattern = Regex(
        "(?i)(?<![\\w.@/])(https?://)?((?:[a-z0-9-]+\\.)+[a-z]{2,})(/[\\w\\-./]*)?"
    )

    /** Liefert den ersten plausiblen Code in normalisierter Form ("ABC-DEF-GHI") oder null. */
    fun find(text: String): String? =
        pattern.findAll(text)
            .map { it.groupValues.drop(1) }
            // Reine Zahlengruppen ("1080 720 480") sind keine Codes.
            .firstOrNull { groups -> groups.all { group -> group.any { it in 'A'..'Z' } } }
            ?.joinToString("-") { group -> group.map { ocrFixes[it] ?: it }.joinToString("") }

    // Technische Angaben, die wie Codes aussehen: "1080P", "H264", "4K", "60HZ", "32GB" …
    private val technicalTerms = Regex("\\d{3,4}P|[HX]26[45]|\\d+(?:K|HZ|FPS|KB|MB|GB|TB|MP)")
    private val nonCodes = setOf("HTTP", "HTTPS", "HDMI", "WLAN", "WIFI", "DVBT", "DVBC")
    private const val VOWELS = "AEIOUYÄÖÜ"

    /**
     * Sieht der Text wie ein Code aus und nicht wie ein Wort? Dient nur als Vorschlag beim
     * Markieren von Hand – für eine automatische Auslösung ist das zu unsicher.
     */
    fun isCodeLike(text: String): Boolean {
        val s = text.filterNot { it == '-' || it.isWhitespace() }
        if (s.length !in 4..12 || !s.all { it in 'A'..'Z' || it in '0'..'9' }) return false
        if (s in nonCodes || technicalTerms.matches(s)) return false
        val hasDigit = s.any { it.isDigit() }
        val hasLetter = s.any { it.isLetter() }
        return when {
            hasDigit && hasLetter -> true
            hasDigit -> s.length >= 5 // Vierstellige Zahlen sind meist Jahreszahlen oder Uhrzeiten.
            else -> !looksLikeWord(s)
        }
    }

    // Wörter haben genug Vokale und keine langen Konsonantenketten; zufällige Buchstabenfolgen schon.
    private fun looksLikeWord(s: String): Boolean {
        val vowelShare = s.count { it in VOWELS }.toDouble() / s.length
        val longestConsonantRun = s.split(*VOWELS.toCharArray()).maxOf { it.length }
        return vowelShare >= 0.25 && longestConsonantRun < 4
    }

    /**
     * Ersetzt eine gelesene Adresse durch eine bekannte, wenn sie bis auf Lesefehler gleich ist
     * ("google.com/devlce" → "https://www.google.com/device"). Sonst bleibt sie, wie sie ist.
     */
    fun snapToKnown(url: String, known: List<String>): String {
        val key = comparable(url)
        val closest = known.minByOrNull { editDistance(comparable(it), key) } ?: return url
        return if (editDistance(comparable(closest), key) <= 2) closest else url
    }

    private fun comparable(url: String) =
        url.substringAfter("://").removePrefix("www.").substringBefore('?').trimEnd('/').lowercase()

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(substitution, previous[j + 1] + 1, current[j] + 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    /** Liefert die erste Adresse im Text als "https://…" oder null. */
    fun findUrl(text: String): String? =
        urlPattern.findAll(text)
            // Ohne Schema muss ein Pfad dabei sein, sonst wäre schon "z.B." oder "Version 1.2" ein Treffer.
            .firstOrNull { it.groups[1] != null || it.groupValues[3].length > 1 }
            ?.let { "https://" + it.groupValues[2].lowercase() + it.groupValues[3].trimEnd('.', '/') }
}

/** Ein stabil erkannter Code und – falls im Bild zu lesen – die Adresse, unter der er einzugeben ist. */
data class Detection(val code: String, val url: String?)

/** Meldet einen Code erst, wenn er in [requiredFrames] aufeinanderfolgenden Frames gleich war. */
class StableCodeDetector(private val requiredFrames: Int = 3) {

    private var candidate: String? = null
    private val urls = mutableListOf<String>()
    private var count = 0

    /** Gibt den Code genau einmal pro ununterbrochener Serie zurück, sonst null. */
    fun onFrame(code: String?, url: String? = null): Detection? {
        if (code != null && code == candidate) {
            count++
        } else {
            candidate = code
            count = if (code == null) 0 else 1
            urls.clear()
        }
        if (code != null && url != null) urls += url
        return if (count == requiredFrames && code != null) Detection(code, mostFrequentUrl()) else null
    }

    // Die Adresse ist kleiner gedruckt und wird nicht in jedem Frame gleich gelesen. Ein einzelner
    // Lesefehler soll nicht gewinnen: Es gilt die häufigste, bei Gleichstand die zuletzt gelesene.
    private fun mostFrequentUrl(): String? {
        val counts = urls.groupingBy { it }.eachCount()
        return counts.keys.maxWithOrNull(compareBy({ counts.getValue(it) }, { urls.lastIndexOf(it) }))
    }
}
