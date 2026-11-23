package com.voiceflow.ai.presentation.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.voiceflow.ai.R
import com.voiceflow.ai.VoiceFlowApp
import com.voiceflow.ai.domain.model.FieldInfo
import com.voiceflow.ai.domain.model.TranscriptionState
import com.voiceflow.ai.domain.repository.AudioRepository
import com.voiceflow.ai.domain.repository.SettingsRepository
import com.voiceflow.ai.domain.repository.TextInsertionRepository
import com.voiceflow.ai.domain.repository.WhisperRepository
import com.voiceflow.ai.domain.usecase.InsertTranscriptionUseCase
import com.voiceflow.ai.domain.usecase.StartRecordingUseCase
import com.voiceflow.ai.domain.usecase.StopRecordingAndTranscribeUseCase
import com.voiceflow.ai.presentation.ui.MainActivity
import com.voiceflow.ai.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

enum class DockSide {
    LEFT, RIGHT
}

@AndroidEntryPoint
class FloatingMicService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "FloatingMicService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW_MIC = "com.voiceflow.ai.ACTION_SHOW_MIC"
        const val ACTION_HIDE_MIC = "com.voiceflow.ai.ACTION_HIDE_MIC"
        const val ACTION_STOP_SERVICE = "com.voiceflow.ai.ACTION_STOP_SERVICE"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        private const val PREFS_NAME = "floating_mic_prefs"
        private const val KEY_POS_X = "pos_x"
        private const val KEY_POS_Y = "pos_y"
        private const val KEY_IS_DOCKED = "is_docked"
        private const val KEY_DOCK_SIDE = "dock_side"

        @Volatile
        var isRecordingOrProcessing: Boolean = false
    }

    @Inject
    lateinit var startRecordingUseCase: StartRecordingUseCase

    @Inject
    lateinit var stopRecordingAndTranscribeUseCase: StopRecordingAndTranscribeUseCase

    @Inject
    lateinit var insertTranscriptionUseCase: InsertTranscriptionUseCase

    @Inject
    lateinit var textInsertionRepository: TextInsertionRepository

    @Inject
    lateinit var audioRepository: AudioRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var whisperRepository: WhisperRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var floatingContainer: FrameLayout? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached = false

    private val micStateFlow = MutableStateFlow(TranscriptionState.IDLE)
    private val amplitudeStateFlow = MutableStateFlow(0f)
    private val isDockedFlow = MutableStateFlow(true)
    private val dockSideFlow = MutableStateFlow(DockSide.RIGHT)

    // Compose in Service Lifecycle support
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, createNotification("VoiceFlow AI is active"))

        // Track recording/processing state globally to prevent race conditions
        serviceScope.launch {
            micStateFlow.collect { state ->
                isRecordingOrProcessing = (state == TranscriptionState.RECORDING || state == TranscriptionState.PROCESSING)
            }
        }

        // Collect audio amplitude for waveform animation
        serviceScope.launch {
            audioRepository.amplitudeFlow.collect { amp ->
                amplitudeStateFlow.value = amp
            }
        }

        // Auto-load and pre-warm Whisper offline model in background
        serviceScope.launch(Dispatchers.IO) {
            try {
                val loaded = whisperRepository.ensureModelLoaded()
                Log.i(TAG, "Pre-warmed whisper model in service: $loaded")
            } catch (e: Exception) {
                Log.w(TAG, "Pre-warm whisper model error: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_MIC -> {
                showFloatingOverlay()
            }
            ACTION_HIDE_MIC -> {
                hideFloatingOverlay()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun showFloatingOverlay() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission missing")
            return
        }

        if (isOverlayAttached && floatingContainer?.visibility == View.VISIBLE) {
            return
        }

        if (floatingContainer == null) {
            createFloatingView()
        }

        floatingContainer?.visibility = View.VISIBLE
    }

    private fun hideFloatingOverlay() {
        // Do not hide abruptly while user is actively recording speech or waiting for transcription
        if (micStateFlow.value == TranscriptionState.RECORDING || micStateFlow.value == TranscriptionState.PROCESSING) {
            return
        }
        floatingContainer?.visibility = View.GONE
    }

    private fun createFloatingView() {
        val container = FrameLayout(this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val isDocked = prefs.getBoolean(KEY_IS_DOCKED, true)
        val savedSideName = prefs.getString(KEY_DOCK_SIDE, DockSide.RIGHT.name) ?: DockSide.RIGHT.name
        val side = if (savedSideName == DockSide.LEFT.name) DockSide.LEFT else DockSide.RIGHT
        dockSideFlow.value = side
        isDockedFlow.value = isDocked

        val defaultY = (screenHeight * 0.55f).toInt()
        val savedY = prefs.getInt(KEY_POS_Y, defaultY).coerceIn(80, (screenHeight - (80 * density)).toInt().coerceAtLeast(80))

        val tabWidthPx = (22 * density).toInt()
        val buttonWidthPx = (56 * density).toInt()
        val marginPx = (14 * density).toInt()

        val initialX = if (isDocked) {
            if (side == DockSide.RIGHT) (screenWidth - tabWidthPx).coerceAtLeast(0) else 0
        } else {
            if (side == DockSide.RIGHT) (screenWidth - buttonWidthPx - marginPx).coerceAtLeast(0) else marginPx
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = savedY
        }
        windowLayoutParams = params

        // ViewTree setup for Jetpack Compose in Service
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)
        container.setViewTreeViewModelStoreOwner(this)

        val composeView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    FloatingMicUI(
                        stateFlow = micStateFlow,
                        amplitudeFlow = amplitudeStateFlow,
                        isDockedFlow = isDockedFlow,
                        dockSideFlow = dockSideFlow,
                        onExpand = { expandView() },
                        onDock = { targetSide -> dockView(targetSide) },
                        onStartRecording = { handleStartRecording() },
                        onCancelRecording = { handleCancelRecording() },
                        onCommitRecording = { handleCommitRecording() },
                        onDrag = { dx, dy ->
                            val currentDensity = resources.displayMetrics.density
                            val currentWidth = resources.displayMetrics.widthPixels
                            val currentHeight = resources.displayMetrics.heightPixels
                            val viewWidth = when {
                                isDockedFlow.value -> (22 * currentDensity).toInt()
                                micStateFlow.value == TranscriptionState.IDLE -> (56 * currentDensity).toInt()
                                else -> (240 * currentDensity).toInt()
                            }
                            val viewHeight = (56 * currentDensity).toInt()

                            params.x = (params.x + dx).toInt().coerceIn(0, (currentWidth - viewWidth).coerceAtLeast(0))
                            params.y = (params.y + dy).toInt().coerceIn(0, (currentHeight - viewHeight).coerceAtLeast(0))
                            windowManager?.updateViewLayout(container, params)
                        },
                        onDragEnd = { totalDx, totalDy ->
                            val currentDensity = resources.displayMetrics.density
                            val currentWidth = resources.displayMetrics.widthPixels

                            if (isDockedFlow.value) {
                                // Save Y position for docked tab
                                prefs.edit().putInt(KEY_POS_Y, params.y).apply()
                            } else {
                                // Check if user dragged or swiped towards screen edge to dock/hide
                                val isNearRightEdge = params.x > (currentWidth - (85 * currentDensity))
                                val isNearLeftEdge = params.x < (35 * currentDensity)
                                val isSwipeRight = totalDx > (30 * currentDensity) && params.x > (currentWidth / 3)
                                val isSwipeLeft = totalDx < (-30 * currentDensity) && params.x < (currentWidth * 2 / 3)

                                if (isNearRightEdge || isSwipeRight) {
                                    dockView(DockSide.RIGHT)
                                } else if (isNearLeftEdge || isSwipeLeft) {
                                    dockView(DockSide.LEFT)
                                } else {
                                    val sideDetermined = if (params.x > currentWidth / 2) DockSide.RIGHT else DockSide.LEFT
                                    dockSideFlow.value = sideDetermined
                                    prefs.edit()
                                        .putInt(KEY_POS_X, params.x)
                                        .putInt(KEY_POS_Y, params.y)
                                        .putBoolean(KEY_IS_DOCKED, false)
                                        .putString(KEY_DOCK_SIDE, sideDetermined.name)
                                        .apply()
                                }
                            }
                        }
                    )
                }
            }
        }

        container.addView(composeView)
        floatingContainer = container

        try {
            windowManager?.addView(container, params)
            isOverlayAttached = true
            Log.i(TAG, "Floating overlay attached successfully at ($initialX, $savedY), docked=$isDocked")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating overlay", e)
        }
    }

    private fun expandView() {
        isDockedFlow.value = false
        triggerHaptic()
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val buttonWidthPx = (56 * density).toInt()
        val marginPx = (14 * density).toInt()

        windowLayoutParams?.let { params ->
            params.x = if (dockSideFlow.value == DockSide.RIGHT) {
                (screenWidth - buttonWidthPx - marginPx).coerceAtLeast(0)
            } else {
                marginPx
            }
            floatingContainer?.let { container ->
                windowManager?.updateViewLayout(container, params)
            }
        }
        prefs.edit().putBoolean(KEY_IS_DOCKED, false).apply()
        Log.i(TAG, "Expanded floating mic from docked tab")
    }

    private fun dockView(side: DockSide) {
        isDockedFlow.value = true
        dockSideFlow.value = side
        triggerHaptic()
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val tabWidthPx = (22 * density).toInt()

        windowLayoutParams?.let { params ->
            params.x = if (side == DockSide.RIGHT) {
                (screenWidth - tabWidthPx).coerceAtLeast(0)
            } else {
                0
            }
            floatingContainer?.let { container ->
                windowManager?.updateViewLayout(container, params)
            }
        }
        prefs.edit()
            .putBoolean(KEY_IS_DOCKED, true)
            .putString(KEY_DOCK_SIDE, side.name)
            .apply()
        Log.i(TAG, "Docked floating mic to side: $side")
    }

    private fun adjustPositionForExpansion() {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val pillWidthPx = (240 * density).toInt()
        windowLayoutParams?.let { params ->
            if (params.x + pillWidthPx > screenWidth - 16) {
                params.x = (screenWidth - pillWidthPx - 16).coerceAtLeast(16)
                floatingContainer?.let { container ->
                    windowManager?.updateViewLayout(container, params)
                }
            }
        }
    }

    private fun handleStartRecording() {
        Log.i(TAG, "handleStartRecording called")
        triggerHaptic()
        if (!PermissionUtils.hasRecordAudioPermission(this)) {
            Toast.makeText(this, "Microphone permission required for VoiceFlow AI", Toast.LENGTH_SHORT).show()
            return
        }

        isDockedFlow.value = false
        adjustPositionForExpansion()
        serviceScope.launch {
            micStateFlow.value = TranscriptionState.RECORDING
            val started = startRecordingUseCase()
            if (!started) {
                Log.w(TAG, "startRecordingUseCase returned false")
                micStateFlow.value = TranscriptionState.ERROR
                delay(1200)
                micStateFlow.value = TranscriptionState.IDLE
            }
        }
    }

    private fun handleCancelRecording() {
        Log.i(TAG, "handleCancelRecording called")
        triggerHaptic()
        serviceScope.launch {
            audioRepository.cancelRecording()
            micStateFlow.value = TranscriptionState.IDLE
        }
    }

    private fun handleCommitRecording() {
        Log.i(TAG, "handleCommitRecording called")
        triggerHaptic()
        stopAndTranscribe()
    }

    private fun stopAndTranscribe() {
        serviceScope.launch {
            micStateFlow.value = TranscriptionState.PROCESSING
            val result = stopRecordingAndTranscribeUseCase()

            result.onSuccess { transcription ->
                if (transcription.text.isBlank()) {
                    Log.i(TAG, "Transcription produced empty text (silence)")
                    Toast.makeText(this@FloatingMicService, "No speech detected", Toast.LENGTH_SHORT).show()
                    micStateFlow.value = TranscriptionState.IDLE
                    return@launch
                }

                val fieldInfo = textInsertionRepository.getCurrentFieldInfo()
                    ?: FieldInfo(isEditable = true)

                val inserted = insertTranscriptionUseCase(transcription.text, fieldInfo)
                triggerHaptic()

                if (inserted) {
                    micStateFlow.value = TranscriptionState.SUCCESS
                } else {
                    Toast.makeText(
                        this@FloatingMicService,
                        "Copied to clipboard: ${transcription.text.take(30)}...",
                        Toast.LENGTH_SHORT
                    ).show()
                    micStateFlow.value = TranscriptionState.SUCCESS
                }

                delay(800)
                micStateFlow.value = TranscriptionState.IDLE
            }.onFailure { error ->
                Log.e(TAG, "Transcription failed", error)
                Toast.makeText(
                    this@FloatingMicService,
                    "Error: ${error.localizedMessage ?: "Transcription failed"}",
                    Toast.LENGTH_SHORT
                ).show()
                micStateFlow.value = TranscriptionState.ERROR
                delay(1500)
                micStateFlow.value = TranscriptionState.IDLE
            }
        }
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Haptic vibration not available: ${e.message}")
        }
    }

    private fun createNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingMicService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VoiceFlowApp.CHANNEL_FLOATING_MIC)
            .setContentTitle("VoiceFlow AI Dictation")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()

        if (isOverlayAttached && floatingContainer != null) {
            try {
                windowManager?.removeView(floatingContainer)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating overlay", e)
            }
            floatingContainer = null
            isOverlayAttached = false
        }
        serviceScope.cancel()
        Log.i(TAG, "FloatingMicService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingMicUI(
    stateFlow: MutableStateFlow<TranscriptionState>,
    amplitudeFlow: MutableStateFlow<Float>,
    isDockedFlow: MutableStateFlow<Boolean>,
    dockSideFlow: MutableStateFlow<DockSide>,
    onExpand: () -> Unit,
    onDock: (DockSide) -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onCommitRecording: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: (Float, Float) -> Unit
) {
    val state by stateFlow.collectAsState()
    val amplitude by amplitudeFlow.collectAsState()
    val isDocked by isDockedFlow.collectAsState()
    val dockSide by dockSideFlow.collectAsState()

    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            // 1. DOCKED STATE: Slim edge tab tucked flush against the screen bezel
            isDocked && state == TranscriptionState.IDLE -> {
                DockedTabUI(
                    dockSide = dockSide,
                    onExpand = onExpand,
                    onVerticalDrag = { dy -> onDrag(0f, dy) },
                    onDragEnd = { onDragEnd(0f, 0f) }
                )
            }

            // 2. EXPANDED IDLE BUTTON: Full circular Wispr Flow button with soundwave icon
            state == TranscriptionState.IDLE -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.86f))
                        .border(BorderStroke(1.2.dp, Color.Black.copy(alpha = 0.12f)), CircleShape)
                        .pointerInput(dockSide) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var isDrag = false
                                var totalDeltaX = 0f
                                var totalDeltaY = 0f
                                val touchSlop = viewConfiguration.touchSlop

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        if (!isDrag) {
                                            onStartRecording()
                                        } else {
                                            onDragEnd(totalDeltaX, totalDeltaY)
                                        }
                                        break
                                    }
                                    val dragDelta = change.position - change.previousPosition
                                    totalDeltaX += dragDelta.x
                                    totalDeltaY += dragDelta.y
                                    if ((totalDeltaX * totalDeltaX + totalDeltaY * totalDeltaY) > touchSlop * touchSlop) {
                                        isDrag = true
                                        change.consume()
                                        onDrag(dragDelta.x, dragDelta.y)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wispr_flow),
                        contentDescription = "VoiceFlow AI Dictate",
                        tint = Color(0xFF191919),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 3. RECORDING STATE: Translucent glassmorphism pill with dancing waveform
            state == TranscriptionState.RECORDING -> {
                Surface(
                    shape = RoundedCornerShape(27.dp),
                    shadowElevation = 8.dp,
                    color = Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .height(54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Left: Cancel Button (✕)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x14000000))
                                .clickable { onCancelRecording() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Recording",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Center: Draggable pill body with transparent Dancing Waveform
                        Box(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            onDrag(dragAmount.x, dragAmount.y)
                                        },
                                        onDragEnd = { onDragEnd(0f, 0f) }
                                    )
                                }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WisprWaveform(
                                amplitude = amplitude,
                                modifier = Modifier.background(Color.Transparent)
                            )
                        }

                        // Right: Commit Checkmark Button (✔)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(elevation = 4.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6)) // Vibrant Wispr Purple
                                .clickable { onCommitRecording() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Insert Transcription",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 4. PROCESSING STATE
            state == TranscriptionState.PROCESSING -> {
                Surface(
                    shape = RoundedCornerShape(27.dp),
                    shadowElevation = 8.dp,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF8B5CF6),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Transcribing...",
                            color = Color(0xFF1E293B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 5. SUCCESS STATE
            state == TranscriptionState.SUCCESS -> {
                Surface(
                    shape = RoundedCornerShape(27.dp),
                    shadowElevation = 8.dp,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                    modifier = Modifier.height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done!",
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 6. ERROR STATE
            state == TranscriptionState.ERROR -> {
                Surface(
                    shape = RoundedCornerShape(27.dp),
                    shadowElevation = 8.dp,
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                    modifier = Modifier.height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(54.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Error",
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek vertical Edge Tab that hugs the screen bezel when docked/hidden.
 * Tapping it expands the full Wispr Flow button.
 * Dragging it vertically moves it along the screen edge to user's preferred height.
 */
@Composable
fun DockedTabUI(
    dockSide: DockSide,
    onExpand: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val shape = if (dockSide == DockSide.RIGHT) {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .width(22.dp)
            .height(54.dp)
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.86f))
            .border(BorderStroke(1.2.dp, Color.Black.copy(alpha = 0.12f)), shape)
            .pointerInput(dockSide) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isDrag = false
                    var totalDragY = 0f
                    val touchSlop = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            if (!isDrag) {
                                onExpand()
                            } else {
                                onDragEnd()
                            }
                            break
                        }
                        val dragDelta = change.position - change.previousPosition
                        totalDragY += kotlin.math.abs(dragDelta.y)
                        if (totalDragY > touchSlop) {
                            isDrag = true
                            change.consume()
                            onVerticalDrag(dragDelta.y)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_wispr_flow),
            contentDescription = "Expand dictation microphone",
            tint = Color(0xFF191919),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun WisprWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    // 9 wave bars with distinct sensitivity weights across frequencies
    val barWeights = listOf(0.35f, 0.55f, 0.8f, 1.0f, 0.9f, 0.75f, 0.85f, 0.55f, 0.35f)

    Row(
        modifier = modifier.background(Color.Transparent),
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barWeights.forEachIndexed { index, weight ->
            val targetHeight = (3.5.dp + (22.5.dp * (amplitude * weight).coerceIn(0f, 1f)))
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 500f
                ),
                label = "barHeight$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1B4B)) // Charcoal / deep slate bars matching Wispr Flow
            )
        }
    }
}
