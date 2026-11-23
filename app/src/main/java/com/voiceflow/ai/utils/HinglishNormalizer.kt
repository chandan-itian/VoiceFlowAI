package com.voiceflow.ai.utils

object HinglishNormalizer {

    // Recover common English loanwords even when the cloud formatter is unavailable.
    private val englishSpellings = mapOf(
        "नॉलेज" to "knowledge", "नॉलेज़" to "knowledge", "नालेज" to "knowledge",
        "नॉलेड्ज" to "knowledge", "nolej" to "knowledge", "nawlej" to "knowledge",
        "naalej" to "knowledge", "naulej" to "knowledge", "nolege" to "knowledge", "knolej" to "knowledge",
        "मॉडल" to "model", "मॉडेल" to "model", "मॉडल्स" to "models",
        "mdal" to "model", "mdals" to "models",
        "यूज" to "use", "यूज़" to "use", "यूज़" to "use", "yooj" to "use", "yooz" to "use",
        "बेस्ट" to "best",
        "मीटिंग" to "meeting", "प्रोजेक्ट" to "project", "अपडेट" to "update",
        "फीडबैक" to "feedback", "डेडलाइन" to "deadline", "ईमेल" to "email",
        "सॉफ्टवेयर" to "software", "डैशबोर्ड" to "dashboard"
    )
    private val englishWord = Regex(
        "(?<![\\p{L}\\p{M}\\p{N}_])(?:" + englishSpellings.keys.joinToString("|") { Regex.escape(it) } +
            ")(?![\\p{L}\\p{M}\\p{N}_])", RegexOption.IGNORE_CASE
    )

    // Technical terms that should always maintain proper casing
    private val TECHNICAL_TERMS = mapOf(
        "api" to "API",
        "apis" to "APIs",
        "ui" to "UI",
        "ux" to "UX",
        "url" to "URL",
        "urls" to "URLs",
        "http" to "HTTP",
        "https" to "HTTPS",
        "json" to "JSON",
        "sql" to "SQL",
        "db" to "DB",
        "database" to "database",
        "dashboard" to "dashboard",
        "backend" to "backend",
        "frontend" to "frontend",
        "fullstack" to "fullstack",
        "server" to "server",
        "login" to "login",
        "logout" to "logout",
        "signup" to "signup",
        "sign in" to "sign in",
        "sign out" to "sign out",
        "otp" to "OTP",
        "sms" to "SMS",
        "pdf" to "PDF",
        "apk" to "APK",
        "sdk" to "SDK",
        "ai" to "AI",
        "ml" to "ML",
        "llm" to "LLM",
        "whatsapp" to "WhatsApp",
        "instagram" to "Instagram",
        "chrome" to "Chrome",
        "gmail" to "Gmail",
        "chatgpt" to "ChatGPT",
        "youtube" to "YouTube",
        "github" to "GitHub",
        "google" to "Google",
        "android" to "Android",
        "kotlin" to "Kotlin",
        "gboard" to "Gboard",
        "wifi" to "Wi-Fi"
    )

    // Standardized Hinglish spelling dictionary for common colloquial variations
    private val HINGLISH_STANDARDIZATIONS = mapOf(
        "donon" to "dono", "bhee" to "bhi", "kyon" to "kyun", "karoo" to "karun",
        "बताएं" to "batayein", "करूँ" to "karun", "करूं" to "karun",
        "दोनों" to "dono", "में" to "mein", "मैं" to "main", "भी" to "bhi",
        "क्यों" to "kyun", "हैं" to "hain",
        "kardo" to "kar do",
        "karlo" to "kar lo",
        "karlena" to "kar lena",
        "dekhlo" to "dekh lo",
        "dekhlena" to "dekh lena",
        "batao" to "batao",
        "plz" to "please",
        "pls" to "please",
        "thx" to "thanks",
        "ty" to "thank you",
        "abt" to "about",
        "bcoz" to "because",
        "cuz" to "because",
        "msg" to "message",
        "dm" to "DM",
        "wfh" to "WFH"
    )

    // "men" is also a valid English word. Repair it only in a Hindi construction.
    private val comparison = Regex("""(?iu)\b(dono|donon) (model|models) (men|mein|me) se\b""")
    private val hindiPostposition = Regex("""(?iu)\bmen(?= se\b)""")

    // Hindi and English filler words
    private val FILLER_PATTERNS = listOf(
        "\\bumm+\\b",
        "\\buh+\\b",
        "\\bah+\\b",
        "\\bhmm+\\b",
        "\\bmatlab\\b(?=\\s*,)",
        "\\bjaise ki\\b(?=\\s*,)",
        "\\barre\\b(?=\\s*,)",
        "\\byou know\\b(?=\\s*,)",
        "\\blike\\b(?=\\s*,)"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val slangPatterns = HINGLISH_STANDARDIZATIONS.map { (slang, standard) ->
        Regex("\\b${Regex.escape(slang)}\\b", RegexOption.IGNORE_CASE) to standard
    }

    // Devanagari character to Roman transliteration map for Hinglish fallback
    private val DEVANAGARI_MAP = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'ा' to "a", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ॉ' to "o", 'ॅ' to "e", 'ँ' to "n",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ं' to "n", 'ः' to "h", '्' to ""
    )

    fun containsDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    /**
     * Converts Devanagari text into readable phonetic Romanized Hinglish.
     */
    fun devanagariToHinglish(text: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c in '\u0900'..'\u097F') {
                val roman = DEVANAGARI_MAP[c]
                if (roman != null) {
                    result.append(roman)
                    // Check if next char is consonant (implicit 'a' in devanagari)
                    val nextChar = if (i + 1 < text.length) text[i + 1] else null
                    if (isDevanagariConsonant(c) && (nextChar == null || isDevanagariConsonant(nextChar))) {
                        result.append("a")
                    }
                } else if (c == '।' ) {
                    result.append(".")
                }
            } else {
                result.append(c)
            }
            i++
        }
        return result.toString().trim()
    }

    private fun isDevanagariConsonant(c: Char): Boolean {
        return c in '\u0915'..'\u0939'
    }

    /**
     * Normalizes text according to Hinglish and technical vocabulary rules.
     */
    fun normalize(
        rawText: String,
        isHinglishMode: Boolean = false,
        removeFillers: Boolean = true,
        fixPunctuation: Boolean = true,
        fixCapitalization: Boolean = true
    ): String {
        var text = rawText.trim()
        if (text.isEmpty()) return ""

        // If user explicitly selected Hinglish mode and Whisper produced Devanagari script,
        // convert to clean Romanized Hinglish.
        if (isHinglishMode) {
            for ((pattern, standard) in slangPatterns) text = text.replace(pattern, standard)
            text = englishWord.replace(text) { englishSpellings.getValue(it.value.lowercase()) }
            if (containsDevanagari(text)) text = devanagariToHinglish(text)
            text = comparison.replace(text, "dono models mein se")
            text = hindiPostposition.replace(text, "mein")
        }

        // Remove filler words
        if (removeFillers) {
            for (pattern in FILLER_PATTERNS) {
                text = text.replace(pattern, "")
            }
            text = text.replace(Regex("[ \\t]{2,}"), " ")
        }

        // Apply technical vocabulary and brand casing
        val words = text.split(" ").toMutableList()
        for (idx in words.indices) {
            val cleanWord = words[idx].trim().lowercase().removeSuffix(",").removeSuffix(".")
            val replacement = TECHNICAL_TERMS[cleanWord]
            if (replacement != null) {
                val trailing = if (words[idx].endsWith(",")) "," else if (words[idx].endsWith(".")) "." else ""
                words[idx] = "$replacement$trailing"
            }
        }
        text = words.joinToString(" ")

        // Punctuation
        if (fixPunctuation) {
            text = text.trim()
            if (text.isNotEmpty() && !text.endsWith(".") && !text.endsWith("!") &&
                !text.endsWith("?") && !text.endsWith("।")) {
                text += if (containsDevanagari(text)) "।" else "."
            }
        }

        // Capitalization
        if (fixCapitalization && text.isNotEmpty()) {
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return text.trim()
    }
}
