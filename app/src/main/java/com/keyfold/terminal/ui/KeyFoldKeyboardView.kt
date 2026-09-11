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
import kotlin.math.abs
import com.keyfold.terminal.R
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyType
import com.keyfold.terminal.model.KeyboardLayout

class KeyFoldKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        isClickable = true
        isFocusable = false
    }

    var onKeyPressed: ((KeyDefinition) -> Unit)? = null
    var onKeyLongPressed: ((KeyDefinition) -> Unit)? = null
    var onCursorMove: ((stepsX: Int, stepsY: Int) -> Unit)? = null

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

    // Trackpad Paints (Space Bar Cursor Mode)
    private val trackpadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val trackpadGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val trackpadBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val trackpadLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // Spacebar Cursor Trackpad State
    private var isSpaceTrackpadActive = false
    private var isSpaceTrackpadPending = false
    private var spacePointerId: Int? = null
    private var spaceTouchStartX = 0f
    private var spaceTouchStartY = 0f
    private var spaceLastX = 0f
    private var spaceLastY = 0f
    private var spaceAccumulatedDx = 0f
    private var spaceAccumulatedDy = 0f
    private val spaceTrackpadHandler = Handler(Looper.getMainLooper())
    private val spaceTrackpadRunnable = Runnable {
        if (isSpaceTrackpadPending) {
            isSpaceTrackpadActive = true
            isSpaceTrackpadPending = false
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    private fun performHapticTick() {
        if (!performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (layoutParams != null && layoutParams.height > 0) {
            layoutParams.height
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(width, height)
    }

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
        if (key.type == KeyType.spacer) return

        val isSpace = key.code == "SPACE"
        if (isSpace && isSpaceTrackpadActive) {
            val accentColor = ContextCompat.getColor(context, R.color.kb_accent)
            val density = resources.displayMetrics.density

            // Glowing translucent fill
            trackpadPaint.color = Color.argb(64, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, trackpadPaint)

            // Outer wide glow aura
            trackpadGlowPaint.strokeWidth = 5f * density
            trackpadGlowPaint.color = Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, trackpadGlowPaint)

            // Sharp inner glowing border
            trackpadBorderPaint.strokeWidth = 2.5f * density
            trackpadBorderPaint.color = accentColor
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, trackpadBorderPaint)

            // Only draw text if the space key is wide enough to comfortably contain it without spilling
            val minWidthForText = 240f * density
            if (rect.width() >= minWidthForText) {
                val trackpadText = "◀   SLIDE TO MOVE CURSOR   ▶"
                trackpadLabelPaint.textSize = rect.height() * 0.36f
                trackpadLabelPaint.color = Color.WHITE
                val textWidth = trackpadLabelPaint.measureText(trackpadText)
                if (textWidth <= rect.width() - 32f * density) {
                    val fontMetrics = trackpadLabelPaint.fontMetrics
                    val textY = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
                    canvas.drawText(trackpadText, rect.centerX(), textY, trackpadLabelPaint)
                }
            }
            return
        }

        val dim = isSpaceTrackpadActive
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
        if (dim) keyPaint.alpha = (keyPaint.alpha * 0.40f).toInt()
        canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)

        // Keycap border
        borderPaint.color = when {
            isLocked || isLatched -> ContextCompat.getColor(context, R.color.kb_modifier_latched_border)
            else -> ContextCompat.getColor(context, R.color.kb_key_border)
        }
        if (dim) borderPaint.alpha = (borderPaint.alpha * 0.30f).toInt()
        canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, borderPaint)

        // LED Indicator on modifier key
        if (isModifier && (isLatched || isLocked)) {
            val ledRadius = 3.5f * resources.displayMetrics.density
            val ledX = rect.left + 8f * resources.displayMetrics.density
            val ledY = rect.top + 8f * resources.displayMetrics.density
            if (dim) ledPaint.alpha = (ledPaint.alpha * 0.40f).toInt() else ledPaint.alpha = 255
            canvas.drawCircle(ledX, ledY, ledRadius, ledPaint)
        }

        // Secondary Shift label (top-right corner)
        if (key.shift != null && rect.width() > 30 * resources.displayMetrics.density) {
            shiftLabelPaint.textSize = rect.height() * 0.28f
            val shiftX = rect.right - 6f * resources.displayMetrics.density
            val shiftY = rect.top + shiftLabelPaint.textSize + 3f * resources.displayMetrics.density
            if (dim) shiftLabelPaint.alpha = (shiftLabelPaint.alpha * 0.25f).toInt() else shiftLabelPaint.alpha = 255
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
        if (dim) labelPaint.alpha = (labelPaint.alpha * 0.30f).toInt()
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

    private var longPressKey: KeyDefinition? = null
    private var longPressTriggered = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressKey?.let { key ->
            longPressTriggered = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onKeyLongPressed?.invoke(key)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // If trackpad mode is already active, ignore other finger touches
                if (isSpaceTrackpadActive) {
                    return true
                }

                // If user was pending a space tap and touches another key, flush space immediately for fast typing
                if (isSpaceTrackpadPending && spacePointerId != null && spacePointerId != pointerId) {
                    spaceTrackpadHandler.removeCallbacks(spaceTrackpadRunnable)
                    val spaceKey = activePointerKeys.remove(spacePointerId!!)
                    if (spaceKey != null) {
                        onKeyPressed?.invoke(spaceKey)
                    }
                    isSpaceTrackpadPending = false
                    spacePointerId = null
                }

                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val key = findKeyAt(x, y)
                if (key != null) {
                    activePointerKeys[pointerId] = key
                    pressedKey = key
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    if (key.code == "SPACE") {
                        spacePointerId = pointerId
                        spaceTouchStartX = x
                        spaceTouchStartY = y
                        spaceLastX = x
                        spaceLastY = y
                        spaceAccumulatedDx = 0f
                        spaceAccumulatedDy = 0f
                        isSpaceTrackpadPending = true
                        isSpaceTrackpadActive = false
                        spaceTrackpadHandler.removeCallbacks(spaceTrackpadRunnable)
                        spaceTrackpadHandler.postDelayed(spaceTrackpadRunnable, 250)
                    } else {
                        val hasSecondary = !key.shift.isNullOrEmpty() || !key.shiftOutput.isNullOrEmpty()
                        if (key.repeat) {
                            onKeyPressed?.invoke(key)
                            startRepeating(key)
                        } else if (hasSecondary) {
                            longPressKey = key
                            longPressTriggered = false
                            longPressHandler.removeCallbacks(longPressRunnable)
                            longPressHandler.postDelayed(longPressRunnable, 400)
                        } else {
                            onKeyPressed?.invoke(key)
                        }
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val spId = spacePointerId
                if (spId != null) {
                    val pIndex = event.findPointerIndex(spId)
                    if (pIndex != -1) {
                        val curX = event.getX(pIndex)
                        val curY = event.getY(pIndex)
                        val totalDistX = abs(curX - spaceTouchStartX)
                        val totalDistY = abs(curY - spaceTouchStartY)
                        val slop = 10f * resources.displayMetrics.density

                        if (!isSpaceTrackpadActive && (totalDistX > slop || totalDistY > slop)) {
                            isSpaceTrackpadActive = true
                            isSpaceTrackpadPending = false
                            spaceTrackpadHandler.removeCallbacks(spaceTrackpadRunnable)
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            spaceLastX = curX
                            spaceLastY = curY
                            spaceAccumulatedDx = 0f
                            spaceAccumulatedDy = 0f
                            invalidate()
                        }

                        if (isSpaceTrackpadActive) {
                            val dx = curX - spaceLastX
                            val dy = curY - spaceLastY
                            spaceLastX = curX
                            spaceLastY = curY

                            spaceAccumulatedDx += dx
                            spaceAccumulatedDy += dy

                            val stepThresholdX = 10f * resources.displayMetrics.density
                            val stepThresholdY = 16f * resources.displayMetrics.density

                            if (abs(spaceAccumulatedDx) >= stepThresholdX) {
                                val stepsX = (spaceAccumulatedDx / stepThresholdX).toInt()
                                if (stepsX != 0) {
                                    onCursorMove?.invoke(stepsX, 0)
                                    spaceAccumulatedDx -= stepsX * stepThresholdX
                                    performHapticTick()
                                }
                            }

                            if (abs(spaceAccumulatedDy) >= stepThresholdY) {
                                val stepsY = (spaceAccumulatedDy / stepThresholdY).toInt()
                                if (stepsY != 0) {
                                    onCursorMove?.invoke(0, stepsY)
                                    spaceAccumulatedDy -= stepsY * stepThresholdY
                                    performHapticTick()
                                }
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val key = activePointerKeys.remove(pointerId)

                if (pointerId == spacePointerId) {
                    spaceTrackpadHandler.removeCallbacks(spaceTrackpadRunnable)
                    val wasActive = isSpaceTrackpadActive
                    val wasPending = isSpaceTrackpadPending
                    isSpaceTrackpadActive = false
                    isSpaceTrackpadPending = false
                    spacePointerId = null

                    if (!wasActive && wasPending && key != null) {
                        onKeyPressed?.invoke(key)
                    }
                    pressedKey = activePointerKeys.values.lastOrNull()
                    invalidate()
                } else if (key != null) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    stopRepeating()
                    if (!key.repeat && (!key.shift.isNullOrEmpty() || !key.shiftOutput.isNullOrEmpty())) {
                        if (!longPressTriggered) {
                            onKeyPressed?.invoke(key)
                        }
                    }
                    longPressKey = null
                    longPressTriggered = false
                    pressedKey = activePointerKeys.values.lastOrNull()
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                spaceTrackpadHandler.removeCallbacks(spaceTrackpadRunnable)
                isSpaceTrackpadActive = false
                isSpaceTrackpadPending = false
                spacePointerId = null

                longPressHandler.removeCallbacks(longPressRunnable)
                longPressKey = null
                longPressTriggered = false
                activePointerKeys.clear()
                pressedKey = null
                stopRepeating()
                invalidate()
            }
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): KeyDefinition? {
        val exact = cachedKeyBounds.firstOrNull { it.key.type != KeyType.spacer && it.rect.contains(x, y) }
        if (exact != null) return exact.key

        val tolerance = 10f * resources.displayMetrics.density
        var closestKey: KeyDefinition? = null
        var minDistanceSq = Float.MAX_VALUE

        for (kb in cachedKeyBounds) {
            if (kb.key.type == KeyType.spacer) continue
            val r = kb.rect
            val dx = if (x < r.left) r.left - x else if (x > r.right) x - r.right else 0f
            val dy = if (y < r.top) r.top - y else if (y > r.bottom) y - r.bottom else 0f
            val distSq = dx * dx + dy * dy
            if (distSq < tolerance * tolerance && distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestKey = kb.key
            }
        }
        return closestKey
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
