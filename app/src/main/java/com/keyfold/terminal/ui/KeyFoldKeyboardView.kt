package com.keyfold.terminal.ui

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.keyfold.terminal.R
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyType
import com.keyfold.terminal.model.KeyboardLayout

class KeyFoldKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onKeyPressed: ((KeyDefinition) -> Unit)? = null
    var onKeyLongPressed: ((KeyDefinition) -> Unit)? = null

    var currentLayout: KeyboardLayout? = null
        set(value) {
            field = value
            cachedKeyBounds.clear()
            requestLayout()
            invalidate()
        }

    // Modifier states
    var isShiftLatched: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isShiftLocked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isCtrlLatched: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isCtrlLocked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isAltLatched: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var isAltLocked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    val isShiftActive: Boolean get() = isShiftLatched || isShiftLocked
    val isCtrlActive: Boolean get() = isCtrlLatched || isCtrlLocked
    val isAltActive: Boolean get() = isAltLatched || isAltLocked

    // Dimensions
    private val keyCornerRadius = 8f * resources.displayMetrics.density
    private val keyHorizontalGap = 4f * resources.displayMetrics.density
    private val keyVerticalGap = 5f * resources.displayMetrics.density
    private val keyboardPadding = 6f * resources.displayMetrics.density

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kb_background)
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.kb_key_border)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val shiftLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        color = ContextCompat.getColor(context, R.color.kb_text_secondary)
    }
    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.kb_led_active)
    }

    // Touch & Key tracking
    private val cachedKeyBounds = mutableListOf<KeyBound>()
    private val activePointerKeys = mutableMapOf<Int, KeyDefinition>()
    private var pressedKey: KeyDefinition? = null

    // Key Repeat
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatingKey: KeyDefinition? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatingKey?.let { key ->
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onKeyPressed?.invoke(key)
                repeatHandler.postDelayed(this, 50)
            }
        }
    }

    private data class KeyBound(
        val key: KeyDefinition,
        val rect: RectF
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyBounds(w, h)
    }

    private fun recomputeKeyBounds(w: Int, h: Int) {
        cachedKeyBounds.clear()
        val layout = currentLayout ?: return
        val rows = layout.rows
        if (rows.isEmpty() || w <= 0 || h <= 0) return

        val availableHeight = h - 2 * keyboardPadding - (rows.size - 1) * keyVerticalGap
        val rowHeight = availableHeight / rows.size

        var top = keyboardPadding
        for (row in rows) {
            val totalWeight = row.keys.sumOf { it.width.toDouble() }.toFloat()
            val totalGaps = (row.keys.size - 1) * keyHorizontalGap
            val availableWidth = w - 2 * keyboardPadding - totalGaps

            var left = keyboardPadding
            for (key in row.keys) {
                val keyWidth = (key.width / totalWeight) * availableWidth
                val rect = RectF(left, top, left + keyWidth, top + rowHeight)
                cachedKeyBounds.add(KeyBound(key, rect))
                left += keyWidth + keyHorizontalGap
            }
            top += rowHeight + keyVerticalGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (cachedKeyBounds.isEmpty()) {
            recomputeKeyBounds(width, height)
        }

        for (kb in cachedKeyBounds) {
            drawKey(canvas, kb.key, kb.rect)
        }
    }

    private fun drawKey(canvas: Canvas, key: KeyDefinition, rect: RectF) {
        val isPressed = pressedKey == key || activePointerKeys.values.contains(key)
        val isModifier = key.type == KeyType.modifier
        val isLatched = isModifier && isModifierLatched(key.code)
        val isLocked = isModifier && isModifierLocked(key.code)

        // Keycap background
        keyPaint.color = when {
            isLocked -> ContextCompat.getColor(context, R.color.kb_modifier_locked_bg)
            isLatched -> ContextCompat.getColor(context, R.color.kb_modifier_latched_bg)
            isPressed -> ContextCompat.getColor(context, R.color.kb_key_pressed)
            isModifier -> ContextCompat.getColor(context, R.color.kb_modifier_bg)
            key.type == KeyType.action -> ContextCompat.getColor(context, R.color.kb_action_bg)
            else -> ContextCompat.getColor(context, R.color.kb_key_bg)
        }

        canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)

        // Keycap border
        borderPaint.color = when {
            isLocked || isLatched -> ContextCompat.getColor(context, R.color.kb_modifier_latched_border)
            else -> ContextCompat.getColor(context, R.color.kb_key_border)
        }
        canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, borderPaint)

        // LED Indicator on modifier key
        if (isModifier && (isLatched || isLocked)) {
            val ledRadius = 3.5f * resources.displayMetrics.density
            val ledX = rect.left + 8f * resources.displayMetrics.density
            val ledY = rect.top + 8f * resources.displayMetrics.density
            canvas.drawCircle(ledX, ledY, ledRadius, ledPaint)
        }

        // Secondary Shift label (top-right corner)
        if (key.shift != null && rect.width() > 30 * resources.displayMetrics.density) {
            shiftLabelPaint.textSize = rect.height() * 0.28f
            val shiftX = rect.right - 6f * resources.displayMetrics.density
            val shiftY = rect.top + shiftLabelPaint.textSize + 3f * resources.displayMetrics.density
            canvas.drawText(key.shift, shiftX, shiftY, shiftLabelPaint)
        }

        // Primary Label (center)
        val displayLabel = if (isShiftActive && key.shift != null && key.label.length == 1 && key.label[0].isLetter()) {
            key.shift
        } else {
            key.label
        }

        val textScale = when {
            displayLabel.length > 3 -> 0.32f
            displayLabel.length > 1 -> 0.36f
            else -> 0.44f
        }
        labelPaint.textSize = rect.height() * textScale
        labelPaint.color = when {
            isLocked || isLatched -> ContextCompat.getColor(context, R.color.kb_led_active)
            isPressed -> ContextCompat.getColor(context, R.color.kb_accent)
            else -> ContextCompat.getColor(context, R.color.kb_text_primary)
        }

        val fontMetrics = labelPaint.fontMetrics
        val textY = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(displayLabel, rect.centerX(), textY, labelPaint)
    }

    private fun isModifierLatched(code: String?): Boolean {
        return when (code) {
            "SHIFT" -> isShiftLatched
            "CTRL" -> isCtrlLatched
            "ALT" -> isAltLatched
            else -> false
        }
    }

    private fun isModifierLocked(code: String?): Boolean {
        return when (code) {
            "SHIFT" -> isShiftLocked
            "CTRL" -> isCtrlLocked
            "ALT" -> isAltLocked
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val key = findKeyAt(x, y)
                if (key != null) {
                    activePointerKeys[pointerId] = key
                    pressedKey = key
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onKeyPressed?.invoke(key)

                    if (key.repeat) {
                        startRepeating(key)
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val key = activePointerKeys.remove(pointerId)
                if (key != null) {
                    stopRepeating()
                    pressedKey = activePointerKeys.values.lastOrNull()
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                activePointerKeys.clear()
                pressedKey = null
                stopRepeating()
                invalidate()
            }
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): KeyDefinition? {
        return cachedKeyBounds.firstOrNull { it.rect.contains(x, y) }?.key
    }

    private fun startRepeating(key: KeyDefinition) {
        stopRepeating()
        repeatingKey = key
        repeatHandler.postDelayed(repeatRunnable, 400)
    }

    private fun stopRepeating() {
        repeatingKey = null
        repeatHandler.removeCallbacks(repeatRunnable)
    }
}
