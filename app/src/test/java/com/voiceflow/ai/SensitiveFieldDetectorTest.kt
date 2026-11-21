package com.voiceflow.ai

import com.voiceflow.ai.utils.SensitiveFieldDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFieldDetectorTest {

    @Test
    fun testBankingPackageDetection() {
        assertTrue(SensitiveFieldDetector.isBankingPackage("com.phonepe.app"))
        assertTrue(SensitiveFieldDetector.isBankingPackage("net.one97.paytm"))
        assertTrue(SensitiveFieldDetector.isBankingPackage("com.google.android.apps.nbu.paisa.user"))
        assertTrue(SensitiveFieldDetector.isBankingPackage("com.sbi.lotusintouch"))
        assertTrue(SensitiveFieldDetector.isBankingPackage("com.snapwork.hdfc"))
        assertFalse(SensitiveFieldDetector.isBankingPackage("com.whatsapp"))
        assertFalse(SensitiveFieldDetector.isBankingPackage("com.instagram.android"))
    }
}
