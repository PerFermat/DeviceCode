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
}

/** Meldet einen Code erst, wenn er in [requiredFrames] aufeinanderfolgenden Frames gleich war. */
class StableCodeDetector(private val requiredFrames: Int = 3) {

    private var candidate: String? = null
    private var count = 0

    /** Gibt den Code genau einmal pro ununterbrochener Serie zurück, sonst null. */
    fun onFrame(code: String?): String? {
        if (code != null && code == candidate) {
            count++
        } else {
            candidate = code
            count = if (code == null) 0 else 1
        }
        return if (count == requiredFrames) code else null
    }
}
