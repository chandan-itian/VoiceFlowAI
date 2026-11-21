package com.voiceflow.ai.utils

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

object SensitiveFieldDetector {

    private val SENSITIVE_PACKAGE_PREFIXES = listOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay (Tez)
        "net.one97.paytm",                        // Paytm
        "com.phonepe.app",                        // PhonePe
        "in.org.npci.upiapp",                     // BHIM
        "com.sbi.lotusintouch",                   // Yono SBI
        "com.sbi.SBIFreedomPlus",                 // SBI Anywhere
        "com.msf.kbank.mobile",                   // Kotak Mobile Banking
        "com.snapwork.hdfc",                      // HDFC Bank
        "com.csam.icici.bank.imobile",            // iMobile Pay by ICICI
        "com.axis.mobile",                        // Axis Mobile
        "com.infrasofttech.indianbank",           // Indian Bank
        "com.bankofbaroda.mconnect",              // BOB World
        "com.dreamplug.androidapp",               // CRED
        "com.zerodha.kite3",                      // Zerodha Kite
        "com.nextbillion.groww",                  // Groww
        "com.upstox.pro",                         // Upstox
        "com.angelbroking.iandroid"               // Angel One
    )

    private val SENSITIVE_KEYWORDS = listOf(
        "password",
        "passcode",
        "pin",
        "mpin",
        "upi pin",
        "otp",
        "one time password",
        "cvv",
        "cvc",
        "card number",
        "security code"
    )

    fun isSensitiveField(node: AccessibilityNodeInfo?, packageName: String?): Boolean {
        if (node == null) return false

        // 1. Check if node is explicitly marked as password by Android
        if (node.isPassword) {
            return true
        }

        // 2. Check InputType flags
        val inputType = node.inputType
        if (inputType != 0) {
            val isPasswordType = (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                    (inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0

            if (isPasswordType) {
                return true
            }
        }

        // 3. Check banking / financial packages
        if (packageName != null) {
            val lowerPkg = packageName.lowercase()
            if (SENSITIVE_PACKAGE_PREFIXES.any { lowerPkg.startsWith(it.lowercase()) }) {
                // If it's a banking app, check extra carefully: any numeric, short, or security input
                val hint = node.hintText?.toString()?.lowercase() ?: ""
                val text = node.text?.toString()?.lowercase() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                if (SENSITIVE_KEYWORDS.any { hint.contains(it) || text.contains(it) || viewId.contains(it) }) {
                    return true
                }
            }
        }

        // 4. Check hint text, content description, and view ID for sensitive keywords
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val combinedText = "$hint $contentDesc $viewId"
        if (SENSITIVE_KEYWORDS.any { combinedText.contains(it) }) {
            return true
        }

        return false
    }

    fun isBankingPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase()
        return SENSITIVE_PACKAGE_PREFIXES.any { lower.startsWith(it.lowercase()) }
    }
}
