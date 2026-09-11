package com.keyfold.terminal.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.keyfold.terminal.layout.LayoutRepository
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyType
import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.posture.DevicePosture
import com.keyfold.terminal.posture.DevicePostureDetector
import com.keyfold.terminal.settings.KeyFoldSettingsActivity
import com.keyfold.terminal.ui.KeyFoldKeyboardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class KeyFoldInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "KeyFoldIME"
        const val ACTION_RELOAD_CONFIG = "com.keyfold.terminal.RELOAD_CONFIG"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var layoutRepository: LayoutRepository
    private lateinit var postureDetector: DevicePostureDetector

    private var rootContainer: FrameLayout? = null
    private var keyboardView: KeyFoldKeyboardView? = null

    private var isFnActive = false
    private var isSymActive = false
    private var baseLayout: KeyboardLayout? = null

    // Modifier Double-Tap Tracking
    private var lastShiftTapTime = 0L
    private var lastCtrlTapTime = 0L
    private var lastAltTapTime = 0L
    private val doubleTapTimeout = 350L

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RELOAD_CONFIG) {
                reloadLayout()
                Toast.makeText(this@KeyFoldInputMethodService, "KeyFold: Layouts Reloaded", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        layoutRepository = LayoutRepository(this)
        postureDetector = DevicePostureDetector(this, serviceScope)

        postureDetector.setOnPostureChangedListener { newPosture, hingeBounds ->
            Log.d(TAG, "Posture changed to $newPosture (hinge: $hingeBounds)")
            applyCurrentPostureLayout(newPosture)
        }
        postureDetector.start()

        val filter = IntentFilter(ACTION_RELOAD_CONFIG)
        ContextCompat.registerReceiver(
            this,
            reloadReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        postureDetector.stop()
        serviceScope.cancel()
        try {
            unregisterReceiver(reloadReceiver)
        } catch (_: Exception) {}
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onCreateInputView(): View {
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer = container

        val kb = KeyFoldKeyboardView(this).apply {
            onKeyPressed = { key -> handleKeyPress(key) }
            onKeyLongPressed = { key -> handleLongPress(key) }
            onCursorMove = { stepsX, stepsY -> handleCursorMove(stepsX, stepsY) }
        }
        keyboardView = kb
        container.addView(kb)

        applyCurrentPostureLayout(postureDetector.posture.value)
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        postureDetector.onConfigurationChanged()
        applyCurrentPostureLayout(postureDetector.posture.value)
        rootContainer?.requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        postureDetector.onConfigurationChanged()
        applyCurrentPostureLayout(postureDetector.posture.value)
        rootContainer?.requestLayout()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val container = rootContainer
        if (container != null && container.isShown) {
            val loc = IntArray(2)
            container.getLocationInWindow(loc)
            outInsets.contentTopInsets = loc[1]
            outInsets.visibleTopInsets = loc[1]
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
        }
    }

    private fun reloadLayout() {
        layoutRepository.reload()
        applyCurrentPostureLayout(postureDetector.posture.value)
    }

    private fun applyCurrentPostureLayout(posture: DevicePosture) {
        val layout = layoutRepository.getLayoutForPosture(posture)
        baseLayout = layout
        isFnActive = false
        isSymActive = false

        updateViewLayout(layout, posture)
    }

    private fun updateViewLayout(layout: KeyboardLayout, posture: DevicePosture) {
        val kb = keyboardView ?: return
        val container = rootContainer ?: return

        kb.currentLayout = layout
        val calculatedHeight = postureDetector.calculateKeyboardHeight(posture, layout.height)

        val params = kb.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            calculatedHeight
        )
        params.height = calculatedHeight
        kb.layoutParams = params

        container.requestLayout()
        kb.requestLayout()
        kb.invalidate()
    }

    private fun handleKeyPress(key: KeyDefinition) {
        when (key.type) {
            KeyType.modifier -> handleModifierPress(key)
            KeyType.layer -> handleLayerPress(key)
            KeyType.action -> handleActionPress(key)
            KeyType.character -> handleCharacterPress(key)
            KeyType.spacer -> { /* no-op */ }
        }
    }

    private fun handleModifierPress(key: KeyDefinition) {
        val kb = keyboardView ?: return
        val now = SystemClock.uptimeMillis()

        when (key.code) {
            "SHIFT" -> {
                if (kb.isShiftLocked) {
                    kb.isShiftLocked = false
                    kb.isShiftLatched = false
                } else if (kb.isShiftLatched) {
                    if (now - lastShiftTapTime < doubleTapTimeout) {
                        kb.isShiftLocked = true
                        kb.isShiftLatched = false
                    } else {
                        kb.isShiftLatched = false
                    }
                } else {
                    kb.isShiftLatched = true
                }
                lastShiftTapTime = now
            }
            "CTRL" -> {
                if (kb.isCtrlLocked) {
                    kb.isCtrlLocked = false
                    kb.isCtrlLatched = false
                } else if (kb.isCtrlLatched) {
                    if (now - lastCtrlTapTime < doubleTapTimeout) {
                        kb.isCtrlLocked = true
                        kb.isCtrlLatched = false
                    } else {
                        kb.isCtrlLatched = false
                    }
                } else {
                    kb.isCtrlLatched = true
                }
                lastCtrlTapTime = now
            }
            "ALT" -> {
                if (kb.isAltLocked) {
                    kb.isAltLocked = false
                    kb.isAltLatched = false
                } else if (kb.isAltLatched) {
                    if (now - lastAltTapTime < doubleTapTimeout) {
                        kb.isAltLocked = true
                        kb.isAltLatched = false
                    } else {
                        kb.isAltLatched = false
                    }
                } else {
                    kb.isAltLatched = true
                }
                lastAltTapTime = now
            }
        }
    }

    private fun handleLayerPress(key: KeyDefinition) {
        when (key.code) {
            "FN" -> {
                isFnActive = !isFnActive
                isSymActive = false
                val targetLayout = if (isFnActive) {
                    layoutRepository.getFnLayer()
                } else {
                    baseLayout ?: layoutRepository.getLayoutForPosture(postureDetector.posture.value)
                }
                keyboardView?.currentLayout = targetLayout
            }
            "SYM" -> {
                isSymActive = !isSymActive
                isFnActive = false
                val targetLayout = if (isSymActive) {
                    layoutRepository.getSymLayer(baseLayout?.id ?: "folded_portrait")
                } else {
                    baseLayout ?: layoutRepository.getLayoutForPosture(postureDetector.posture.value)
                }
                keyboardView?.currentLayout = targetLayout
            }
        }
    }

    private fun handleLongPress(key: KeyDefinition) {
        val ic = currentInputConnection ?: return
        val textToEmit = when {
            !key.hint.isNullOrEmpty() -> key.hint
            !key.shiftOutput.isNullOrEmpty() && key.shiftOutput != key.label.uppercase() -> key.shiftOutput
            !key.shift.isNullOrEmpty() && key.shift != key.label.uppercase() -> key.shift
            else -> null
        }
        if (!textToEmit.isNullOrEmpty()) {
            ic.commitText(textToEmit, 1)
            consumeLatchedModifiers()
        }
    }

    private fun handleCursorMove(stepsX: Int, stepsY: Int) {
        if (stepsX > 0) {
            repeat(stepsX) {
                sendKeyWithModifiers(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        } else if (stepsX < 0) {
            repeat(-stepsX) {
                sendKeyWithModifiers(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        }

        if (stepsY > 0) {
            repeat(stepsY) {
                sendKeyWithModifiers(KeyEvent.KEYCODE_DPAD_DOWN)
            }
        } else if (stepsY < 0) {
            repeat(-stepsY) {
                sendKeyWithModifiers(KeyEvent.KEYCODE_DPAD_UP)
            }
        }
    }

    private fun handleActionPress(key: KeyDefinition) {
        when (key.code) {
            "SETTINGS" -> {
                val intent = Intent(this, KeyFoldSettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            }
            else -> {
                val keyCode = mapKeyActionToAndroidKeyCode(key.code)
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    sendKeyWithModifiers(keyCode)
                }
            }
        }
        consumeLatchedModifiers()
    }

    private fun handleCharacterPress(key: KeyDefinition) {
        val ic = currentInputConnection ?: return
        val kb = keyboardView

        val isShift = kb?.isShiftActive == true
        val isCtrl = kb?.isCtrlActive == true
        val isAlt = kb?.isAltActive == true

        val isLetter = key.label.length == 1 && key.label[0].isLetter()
        val textToEmit = when {
            isShift && isLetter -> key.label.uppercase()
            isShift && !key.shiftOutput.isNullOrEmpty() -> key.shiftOutput
            isShift && !key.shift.isNullOrEmpty() -> key.shift
            !key.output.isNullOrEmpty() -> key.output
            else -> key.label
        }

        // If Ctrl or Alt is active, dispatch as low-level KeyEvent so Termux intercepts SIGINT, EOF, etc.
        if ((isCtrl || isAlt) && textToEmit.length == 1) {
            val ch = textToEmit[0].lowercaseChar()
            val keyCode = when (ch) {
                in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch - 'a')
                in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
                ' ' -> KeyEvent.KEYCODE_SPACE
                '\t' -> KeyEvent.KEYCODE_TAB
                '/' -> KeyEvent.KEYCODE_SLASH
                '-' -> KeyEvent.KEYCODE_MINUS
                '=' -> KeyEvent.KEYCODE_EQUALS
                '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
                ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
                '\\' -> KeyEvent.KEYCODE_BACKSLASH
                ';' -> KeyEvent.KEYCODE_SEMICOLON
                '\'' -> KeyEvent.KEYCODE_APOSTROPHE
                ',' -> KeyEvent.KEYCODE_COMMA
                '.' -> KeyEvent.KEYCODE_PERIOD
                '`' -> KeyEvent.KEYCODE_GRAVE
                else -> KeyEvent.KEYCODE_UNKNOWN
            }

            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                sendKeyWithModifiers(keyCode)
            } else {
                ic.commitText(textToEmit, 1)
            }
        } else {
            ic.commitText(textToEmit, 1)
        }

        consumeLatchedModifiers()
    }

    private fun sendKeyWithModifiers(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val kb = keyboardView

        var metaState = 0
        if (kb?.isShiftActive == true) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        if (kb?.isCtrlActive == true) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (kb?.isAltActive == true) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }

        val now = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        val downEvent = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags
        )
        val upEvent = KeyEvent(
            now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags
        )

        ic.sendKeyEvent(downEvent)
        ic.sendKeyEvent(upEvent)
    }

    private fun consumeLatchedModifiers() {
        val kb = keyboardView ?: return
        if (kb.isShiftLatched && !kb.isShiftLocked) {
            kb.isShiftLatched = false
        }
        if (kb.isCtrlLatched && !kb.isCtrlLocked) {
            kb.isCtrlLatched = false
        }
        if (kb.isAltLatched && !kb.isAltLocked) {
            kb.isAltLatched = false
        }
    }

    private fun mapKeyActionToAndroidKeyCode(code: String?): Int {
        return when (code) {
            "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
            "TAB" -> KeyEvent.KEYCODE_TAB
            "ENTER" -> KeyEvent.KEYCODE_ENTER
            "DEL" -> KeyEvent.KEYCODE_DEL
            "FORWARD_DEL" -> KeyEvent.KEYCODE_FORWARD_DEL
            "SPACE" -> KeyEvent.KEYCODE_SPACE
            "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
            "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
            "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
            "MOVE_HOME" -> KeyEvent.KEYCODE_MOVE_HOME
            "MOVE_END" -> KeyEvent.KEYCODE_MOVE_END
            "INSERT" -> KeyEvent.KEYCODE_INSERT
            "F1" -> KeyEvent.KEYCODE_F1
            "F2" -> KeyEvent.KEYCODE_F2
            "F3" -> KeyEvent.KEYCODE_F3
            "F4" -> KeyEvent.KEYCODE_F4
            "F5" -> KeyEvent.KEYCODE_F5
            "F6" -> KeyEvent.KEYCODE_F6
            "F7" -> KeyEvent.KEYCODE_F7
            "F8" -> KeyEvent.KEYCODE_F8
            "F9" -> KeyEvent.KEYCODE_F9
            "F10" -> KeyEvent.KEYCODE_F10
            "F11" -> KeyEvent.KEYCODE_F11
            "F12" -> KeyEvent.KEYCODE_F12
            "SYSRQ" -> KeyEvent.KEYCODE_SYSRQ
            "BREAK" -> KeyEvent.KEYCODE_BREAK
            "SCROLL_LOCK" -> KeyEvent.KEYCODE_SCROLL_LOCK
            "MENU" -> KeyEvent.KEYCODE_MENU
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }
}
