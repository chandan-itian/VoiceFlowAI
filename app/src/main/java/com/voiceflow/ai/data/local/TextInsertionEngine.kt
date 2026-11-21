package com.voiceflow.ai.data.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.voiceflow.ai.domain.model.FieldInfo
import com.voiceflow.ai.domain.repository.TextInsertionRepository
import com.voiceflow.ai.presentation.service.FloatingMicService
import com.voiceflow.ai.presentation.service.VoiceFlowAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextInsertionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextInsertionRepository {

    companion object {
        private const val TAG = "TextInsertionEngine"
    }

    @Volatile
    private var focusedNode: AccessibilityNodeInfo? = null

    @Volatile
    private var currentCachedFieldInfo: FieldInfo? = null

    @Volatile
    private var lastInsertedText: String? = null

    /**
     * Called by the AccessibilityService whenever the focused editable node changes.
     */
    fun setFocusedNode(node: AccessibilityNodeInfo?) {
        focusedNode = node?.let {
            try {
                AccessibilityNodeInfo.obtain(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun setActiveNode(node: AccessibilityNodeInfo?, fieldInfo: FieldInfo?) {
        setFocusedNode(node)
        currentCachedFieldInfo = fieldInfo
    }

    fun clearActiveNode() {
        if (FloatingMicService.isRecordingOrProcessing) {
            Log.d(TAG, "clearActiveNode skipped: recording/processing is active")
            return
        }
        focusedNode = null
        currentCachedFieldInfo = null
    }

    private fun getTargetNode(): AccessibilityNodeInfo? {
        val current = focusedNode
        if (current != null) {
            try {
                if (current.refresh() && current.isEditable) {
                    return current
                }
            } catch (_: Exception) {}
        }

        val a11y = VoiceFlowAccessibilityService.instance ?: return null

        // 1. Search root in active window
        try {
            val root = a11y.rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                return focused
            }
        } catch (_: Exception) {}

        // 2. Search application windows
        try {
            val appWindows = a11y.windows?.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            } ?: emptyList()

            for (w in appWindows) {
                val root = w.root ?: continue
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused.isEditable) {
                    return focused
                }

                val viewId = currentCachedFieldInfo?.viewId
                if (!viewId.isNullOrBlank()) {
                    val matching = root.findAccessibilityNodeInfosByViewId(viewId)
                    val editable = matching?.firstOrNull { it.isEditable }
                    if (editable != null) return editable
                }

                val editable = findFirstEditableNode(root)
                if (editable != null) return editable
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding target node in windows: ${e.message}")
        }

        return null
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    override suspend fun insertText(text: String, fieldInfo: FieldInfo): Boolean =
        withContext(Dispatchers.IO) {
            val node = getTargetNode()
            if (node == null) {
                Log.w(TAG, "No target input node available, copying to clipboard")
                copyToClipboard(text)
                return@withContext false
            }

            // Duplicate prevention
            if (text == lastInsertedText && text.isNotEmpty()) {
                Log.d(TAG, "Duplicate text, skipping insertion")
                return@withContext true
            }

            try {
                // Focus node before inserting
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                val effectiveFieldInfo = currentCachedFieldInfo ?: fieldInfo
                val inserted = when {
                    tryActionSetText(node, text, effectiveFieldInfo) -> true
                    tryActionPaste(node, text) -> true
                    else -> {
                        copyToClipboard(text)
                        false
                    }
                }
                if (inserted) {
                    lastInsertedText = text
                    Log.i(TAG, "Successfully inserted transcription into field: '$text'")
                }
                inserted
            } catch (e: Exception) {
                Log.e(TAG, "insertText error", e)
                copyToClipboard(text)
                false
            }
        }

    override suspend fun copyToClipboard(text: String) {
        withContext(Dispatchers.Main) {
            try {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("VoiceFlow Transcription", text)
                )
                Log.d(TAG, "Text copied to clipboard")
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard copy error", e)
            }
        }
    }

    /**
     * The real user text of a field. An empty field's node.text is its
     * hint/placeholder (e.g. "Message" in WhatsApp), not user content.
     * isShowingHintText alone is not reliable — WhatsApp does not always set
     * it — so text that exactly matches the node's hint is also treated as
     * empty, unless the cursor sits inside it (the user actually typed it).
     */
    fun extractRealText(node: AccessibilityNodeInfo): String {
        if (node.isShowingHintText) return ""
        val text = node.text?.toString() ?: return ""
        val hint = try { node.hintText?.toString() } catch (_: Exception) { null }
        if (!hint.isNullOrEmpty() && text == hint &&
            node.textSelectionStart <= 0 && node.textSelectionEnd <= 0
        ) return ""
        return text
    }

    override fun getCurrentFieldInfo(): FieldInfo? {
        val node = focusedNode ?: return currentCachedFieldInfo
        return try {
            FieldInfo(
                packageName = node.packageName?.toString() ?: "",
                className = node.className?.toString() ?: "",
                viewId = node.viewIdResourceName ?: "",
                isEditable = node.isEditable,
                isSensitive = false,
                isPassword = node.isPassword,
                currentText = extractRealText(node),
                selectionStart = node.textSelectionStart,
                selectionEnd = node.textSelectionEnd
            )
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentFieldInfo error", e)
            currentCachedFieldInfo
        }
    }

    override fun isFieldAvailable(): Boolean {
        val node = getTargetNode() ?: return false
        return try {
            node.isEditable
        } catch (_: Exception) {
            false
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun tryActionSetText(
        node: AccessibilityNodeInfo,
        text: String,
        fieldInfo: FieldInfo
    ): Boolean {
        return try {
            // When the field is empty, node.text returns the hint/placeholder
            // (e.g. "Message" in WhatsApp), not real content — treat it as empty
            // so the hint is never prepended to the transcription.
            val liveText = extractRealText(node).ifEmpty {
                // Node reported no real text; fall back to the cached text,
                // unless the cached text is itself just the hint.
                val cached = fieldInfo.currentText
                val hint = try { node.hintText?.toString() } catch (_: Exception) { null }
                if (!hint.isNullOrEmpty() && cached == hint) "" else cached
            }
            val liveFieldInfo = fieldInfo.copy(currentText = liveText)
            val finalText = buildInsertionText(text, liveFieldInfo)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) {
                val selArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, finalText.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, finalText.length)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun tryActionPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            withContext(Dispatchers.Main) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("VoiceFlow", text)
                )
            }
            delay(50)
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * If there is a selection/cursor position, build insertion-aware text
     * (insert at cursor rather than replace all).
     */
    private fun buildInsertionText(newText: String, fieldInfo: FieldInfo): String {
        val existing = fieldInfo.currentText
        if (existing.isEmpty()) return newText

        val start = fieldInfo.selectionStart
        val end = fieldInfo.selectionEnd

        // No valid selection info → append with space
        if (start < 0) {
            return if (existing.endsWith(" ")) "$existing$newText"
            else "$existing $newText"
        }

        // Replace selection range (or insert at cursor if start==end)
        val before = existing.substring(0, start.coerceAtMost(existing.length))
        val after = existing.substring(end.coerceAtMost(existing.length))

        val prefix = if (before.isNotEmpty() && !before.endsWith(" ")) "$before " else before
        val suffix = if (after.isNotEmpty() && !after.startsWith(" ")) " $after" else after

        return "$prefix$newText$suffix"
    }
}
