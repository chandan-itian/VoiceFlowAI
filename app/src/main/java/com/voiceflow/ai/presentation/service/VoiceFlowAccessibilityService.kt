package com.voiceflow.ai.presentation.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.voiceflow.ai.data.local.TextInsertionEngine
import com.voiceflow.ai.domain.model.FieldInfo
import com.voiceflow.ai.domain.repository.AudioRepository
import com.voiceflow.ai.domain.repository.SettingsRepository
import com.voiceflow.ai.utils.SensitiveFieldDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class VoiceFlowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceFlowA11y"
        var instance: VoiceFlowAccessibilityService? = null
            private set
    }

    @Inject
    lateinit var textInsertionEngine: TextInsertionEngine

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var audioRepository: AudioRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isAppGloballyEnabled = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "VoiceFlow Accessibility Service connected")

        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                isAppGloballyEnabled = settings.isAppEnabled
                if (!isAppGloballyEnabled) {
                    hideFloatingMic()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isAppGloballyEnabled) return

        val eventPkg = event.packageName?.toString() ?: ""

        // NEVER process accessibility events from our own app (VoiceFlow AI)
        // Touching the floating mic overlay, dragging, or tapping commit must never cancel or hide the overlay!
        if (eventPkg == "com.voiceflow.ai" || eventPkg == packageName) {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleNodeInteraction(event)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChange(event)
            }
        }
    }

    private fun handleNodeInteraction(event: AccessibilityEvent) {
        val sourceNode = event.source
        val packageName = event.packageName?.toString() ?: ""

        if (sourceNode != null && isEligibleField(sourceNode, packageName)) {
            showFloatingMicForNode(sourceNode, packageName)
        } else {
            handleWindowChange(event)
        }
    }

    private fun handleWindowChange(event: AccessibilityEvent) {
        // While user is actively recording or waiting for transcription, NEVER hide the mic or clear the node!
        if (audioRepository.isRecording() || FloatingMicService.isRecordingOrProcessing) {
            return
        }

        val packageName = event.packageName?.toString() ?: ""
        val keyboardOpen = isKeyboardVisible()

        val root = rootInActiveWindow
        val focusedInput = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (keyboardOpen && focusedInput != null && isEligibleField(focusedInput, packageName)) {
            showFloatingMicForNode(focusedInput, packageName)
        } else if (!keyboardOpen) {
            hideFloatingMic()
        }
    }

    private fun isKeyboardVisible(): Boolean {
        return try {
            val imeWindow = windows?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (imeWindow == null) return false
            val rect = android.graphics.Rect()
            imeWindow.getBoundsInScreen(rect)
            rect.height() > 300
        } catch (e: Exception) {
            false
        }
    }

    private fun isEligibleField(node: AccessibilityNodeInfo, packageName: String?): Boolean {
        // 1. Must be editable or actively focused for text input
        if (!node.isEditable && !node.isFocused) {
            return false
        }

        // 2. Must not be in password or sensitive inputs (banking, PIN, OTP)
        if (SensitiveFieldDetector.isSensitiveField(node, packageName)) {
            Log.d(TAG, "Field rejected: Sensitive/Password input detected in $packageName")
            return false
        }

        return true
    }

    private fun showFloatingMicForNode(node: AccessibilityNodeInfo, packageName: String) {
        // An empty field's node.text is its hint/placeholder (e.g. WhatsApp's
        // "Message") — that is not user text and must not be kept as a prefix.
        val text = if (node.isShowingHintText) "" else node.text?.toString() ?: ""
        val selectionStart = node.textSelectionStart
        val selectionEnd = node.textSelectionEnd
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        val fieldInfo = FieldInfo(
            packageName = packageName,
            className = className,
            viewId = viewId,
            isEditable = node.isEditable,
            isSensitive = false,
            isPassword = false,
            currentText = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd
        )

        // Set the active node and field info in the insertion engine
        textInsertionEngine.setActiveNode(node, fieldInfo)
        Log.i(TAG, "showFloatingMicForNode called for pkg=$packageName, viewId=$viewId")

        // Signal the floating mic service to display the overlay
        val showIntent = Intent(this, FloatingMicService::class.java).apply {
            action = FloatingMicService.ACTION_SHOW_MIC
            putExtra(FloatingMicService.EXTRA_PACKAGE_NAME, packageName)
        }
        startService(showIntent)
    }

    private fun hideFloatingMic() {
        if (audioRepository.isRecording() || FloatingMicService.isRecordingOrProcessing) {
            return
        }
        Log.i(TAG, "hideFloatingMic called")
        textInsertionEngine.clearActiveNode()
        val hideIntent = Intent(this, FloatingMicService::class.java).apply {
            action = FloatingMicService.ACTION_HIDE_MIC
        }
        startService(hideIntent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceFlow Accessibility Service interrupted")
        hideFloatingMic()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        textInsertionEngine.clearActiveNode()
        serviceScope.cancel()
        Log.i(TAG, "VoiceFlow Accessibility Service destroyed")
    }
}
