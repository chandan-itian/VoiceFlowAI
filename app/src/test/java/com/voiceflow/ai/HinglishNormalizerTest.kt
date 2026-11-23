package com.voiceflow.ai

import com.voiceflow.ai.utils.HinglishNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HinglishNormalizerTest {

    @Test
    fun repairsMixedModelComparisonWithoutChangingEnglishMen() {
        val input = "To donon mdal men se kaun sa mdal ko main yooj karoo?"
        assertEquals("To dono models mein se kaun sa model ko main use karun?", HinglishNormalizer.normalize(input, true))
        assertEquals("Two men use models.", HinglishNormalizer.normalize("Two men use models.", true))
        assertEquals("Dono models mein se.", HinglishNormalizer.normalize("दोनों मॉडल में से", true))
    }

    @Test
    fun restoresEnglishWordsInMixedDictation() {
        assertEquals("Mujhe knowledge hai.", HinglishNormalizer.normalize("मुझे नॉलेज है", true))
        assertEquals("Meri meeting ka feedback do.", HinglishNormalizer.normalize("meri मीटिंग ka फीडबैक do", true))
        assertEquals("Mujhe knowledge hai.", HinglishNormalizer.normalize("mujhe nolej hai", true))
        assertEquals("Her knowledge is invaluable.", HinglishNormalizer.normalize("Her knowledge is invaluable.", true))
    }

    @Test
    fun testHinglishSpokenSentenceNormalization() {
        val input = "bhai meri website ka dashboard update kar do aur booking system bhi check kar lena"
        val output = HinglishNormalizer.normalize(
            rawText = input,
            isHinglishMode = true,
            removeFillers = true,
            fixPunctuation = true,
            fixCapitalization = true
        )

        // Verifies first letter capital, technical terms preserved, sentence terminated with period
        assertTrue(output.startsWith("Bhai"))
        assertTrue(output.contains("dashboard"))
        assertTrue(output.endsWith("."))
    }

    @Test
    fun testDevanagariDetection() {
        assertTrue(HinglishNormalizer.containsDevanagari("नमस्ते भारत"))
        assertTrue(HinglishNormalizer.containsDevanagari("भाई डैशबोर्ड अपडेट कर दो"))
        assertTrue(!HinglishNormalizer.containsDevanagari("Hello world"))
    }

    @Test
    fun testTechnicalTermsPreserved() {
        val input = "please send the apk with kotlin and android sdk"
        val output = HinglishNormalizer.normalize(input)
        assertTrue(output.contains("APK"))
        assertTrue(output.contains("Kotlin"))
        assertTrue(output.contains("Android"))
        assertTrue(output.contains("SDK"))
    }
}
