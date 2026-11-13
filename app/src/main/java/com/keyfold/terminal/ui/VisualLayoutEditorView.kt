package com.keyfold.terminal.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.keyfold.terminal.R
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyType
import com.keyfold.terminal.model.KeyboardLayout
import kotlin.math.abs
import kotlin.math.max

/**
 * Interactive visual keyboard layout editor.
 * - Edit mode (isDragMode = false): tap any key to edit its properties, or tap '+' on a row to add a key.
 * - Drag mode (isDragMode = true): drag any key to reorder within a row or move across rows.
 */
class VisualLayoutEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        isClickable = true
        isFocusable = false
    }

    var onKeyTapped: ((rowIndex: Int, keyIndex: Int, key: KeyDefinition) -> Unit)? = null
    var onAddKeyToRow: ((rowIndex: Int) -> Unit)? = null
    var onKeyMoved: ((fromRow: Int, fromIndex: Int, toRow: Int, toIndex: Int) -> Unit)? = null

    var currentLayout: KeyboardLayout? = null
        set(value) {
            field = value
            recomputeBounds(width, height)
            invalidate()
        }

    var isDragMode: Boolean = false
        set(value) {
            field = value
            cancelDrag()
            recomputeBounds(width, height)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val keyCornerRadius = 7f * density
    private val keyboardPadding = 6f * density
    private val addBtnWidth = 26f * density

    private fun currentKeyHorizontalGap(): Float =
        (currentLayout?.horizontalPaddingDp ?: 4f).coerceIn(0f, 24f) * density

    private fun currentKeyVerticalGap(): Float =
        (currentLayout?.verticalPaddingDp ?: 5f).coerceIn(0f, 24f) * density

    private data class KeySlot(
        val rowIndex: Int,
        val keyIndex: Int,
        val key: KeyDefinition,
        val rect: RectF
    )

    private data class RowSlot(
        val rowIndex: Int,
        val rect: RectF,
        val addButtonRect: RectF
    )

    private val keySlots = mutableListOf<KeySlot>()
    private val rowSlots = mutableListOf<RowSlot>()

    // Tap & Drag State
    private var touchedKey: KeySlot? = null
    private var touchedAddRow: Int? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var isDragging = false
    private var dropTargetRow = -1
    private var dropTargetIndex = -1

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kb_background)
    }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val dashedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        pathEffect = DashPathEffect(floatArrayOf(5f * density, 4f * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        color = ContextCompat.getColor(context, R.color.kb_text_secondary)
    }
    private val addBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#212834")
    }
    private val dropIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.kb_accent)
    }
    private val rowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(35, 88, 166, 255)
    }

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
        recomputeBounds(w, h)
    }

    private fun recomputeBounds(w: Int, h: Int) {
        keySlots.clear()
        rowSlots.clear()
        val layout = currentLayout ?: return
        val rows = layout.rows
        if (rows.isEmpty() || w <= 0 || h <= 0) return

        val keyHorizontalGap = currentKeyHorizontalGap()
        val keyVerticalGap = currentKeyVerticalGap()
        val availableHeight = h - 2 * keyboardPadding - (rows.size - 1) * keyVerticalGap
        val rowHeight = max(24f * density, availableHeight / rows.size)

        var top = keyboardPadding
        for ((rowIndex, row) in rows.withIndex()) {
            val rowRight = w - keyboardPadding
            val addBtnRect = RectF(rowRight - addBtnWidth, top, rowRight, top + rowHeight)
            val keysAreaWidth = (rowRight - addBtnWidth - keyHorizontalGap) - keyboardPadding
            rowSlots.add(RowSlot(rowIndex, RectF(keyboardPadding, top, rowRight, top + rowHeight), addBtnRect))

            if (row.keys.isNotEmpty() && keysAreaWidth > 0f) {
                val totalWeight = row.keys.sumOf { it.width.toDouble() }.toFloat().coerceAtLeast(0.1f)
                val totalGaps = (row.keys.size - 1) * keyHorizontalGap
                val availableWidth = (keysAreaWidth - totalGaps).coerceAtLeast(10f)

                var left = keyboardPadding
                for ((keyIndex, key) in row.keys.withIndex()) {
                    val keyWidth = (key.width / totalWeight) * availableWidth
                    val rect = RectF(left, top, left + keyWidth, top + rowHeight)
                    keySlots.add(KeySlot(rowIndex, keyIndex, key, rect))
                    left += keyWidth + keyHorizontalGap
                }
            }
            top += rowHeight + keyVerticalGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (rowSlots.isEmpty() && width > 0 && height > 0) {
            recomputeBounds(width, height)
        }

        // Highlight target row while dragging
        if (isDragging && dropTargetRow in rowSlots.indices) {
            canvas.drawRoundRect(rowSlots[dropTargetRow].rect, keyCornerRadius, keyCornerRadius, rowHighlightPaint)
        }

        // Draw '+' add-key buttons at end of each row
        for (rs in rowSlots) {
            canvas.drawRoundRect(rs.addButtonRect, keyCornerRadius, keyCornerRadius, addBtnPaint)
            borderPaint.color = Color.parseColor("#3B4658")
            canvas.drawRoundRect(rs.addButtonRect, keyCornerRadius, keyCornerRadius, borderPaint)

            labelPaint.color = ContextCompat.getColor(context, R.color.kb_accent)
            labelPaint.textSize = (rs.addButtonRect.height() * 0.44f).coerceAtMost(18f * density)
            val fm = labelPaint.fontMetrics
            val textY = rs.addButtonRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText("+", rs.addButtonRect.centerX(), textY, labelPaint)
        }

        // Draw keys
        for (slot in keySlots) {
            val isSourceDragged = isDragging && touchedKey?.rowIndex == slot.rowIndex && touchedKey?.keyIndex == slot.keyIndex
            drawEditorKey(canvas, slot.key, slot.rect, dimmed = isSourceDragged)
        }

        // Draw drop insertion bar
        if (isDragging && dropTargetRow >= 0 && dropTargetIndex >= 0) {
            drawDropIndicator(canvas, dropTargetRow, dropTargetIndex)
        }

        // Draw floating ghost keycap
        if (isDragging) {
            touchedKey?.let { src ->
                val w = src.rect.width()
                val h = src.rect.height()
                val ghostRect = RectF(dragX - w / 2f, dragY - h / 2f, dragX + w / 2f, dragY + h / 2f)
                drawEditorKey(canvas, src.key, ghostRect, dimmed = false, isGhost = true)
            }
        }
    }

    private fun drawEditorKey(
        canvas: Canvas,
        key: KeyDefinition,
        rect: RectF,
        dimmed: Boolean,
        isGhost: Boolean = false
    ) {
        val isSpacer = key.type == KeyType.spacer
        if (isSpacer) {
            keyPaint.color = Color.argb(if (dimmed) 25 else 55, 45, 55, 72)
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)
            dashedBorderPaint.color = if (isDragMode) ContextCompat.getColor(context, R.color.kb_accent) else Color.parseColor("#4B5565")
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, dashedBorderPaint)

            labelPaint.color = ContextCompat.getColor(context, R.color.kb_text_secondary)
            labelPaint.textSize = (rect.height() * 0.24f).coerceAtMost(11f * density)
            val fm = labelPaint.fontMetrics
            val textY = rect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText("␣ ${formatWeight(key.width)}", rect.centerX(), textY, labelPaint)
            return
        }

        keyPaint.color = when {
            isGhost -> Color.argb(230, 48, 62, 84)
            key.type == KeyType.modifier -> ContextCompat.getColor(context, R.color.kb_modifier_bg)
            key.type == KeyType.action -> ContextCompat.getColor(context, R.color.kb_action_bg)
            key.type == KeyType.layer -> Color.parseColor("#232938")
            else -> ContextCompat.getColor(context, R.color.kb_key_bg)
        }
        if (dimmed) keyPaint.alpha = 70
        canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)

        val activeBorder = if (isGhost || isDragMode) {
            ContextCompat.getColor(context, R.color.kb_accent)
        } else {
            ContextCompat.getColor(context, R.color.kb_key_border)
        }
        if (isDragMode && !isGhost) {
            dashedBorderPaint.color = Color.argb(140, Color.red(activeBorder), Color.green(activeBorder), Color.blue(activeBorder))
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, dashedBorderPaint)
        } else {
            borderPaint.color = activeBorder
            if (dimmed) borderPaint.alpha = 70
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, borderPaint)
        }

        // Primary label
        val displayLabel = key.label.ifEmpty { key.code ?: "?" }
        val textScale = when {
            displayLabel.length > 3 -> 0.28f
            displayLabel.length > 1 -> 0.34f
            else -> 0.42f
        }
        labelPaint.textSize = (rect.height() * textScale).coerceAtLeast(9f * density)
        labelPaint.color = ContextCompat.getColor(context, R.color.kb_text_primary)
        if (dimmed) labelPaint.alpha = 80
        val fm = labelPaint.fontMetrics
        val textY = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(displayLabel, rect.centerX(), textY, labelPaint)

        // Weight badge in bottom-right when weight != 1.0 or when wide enough
        if (abs(key.width - 1.0f) > 0.05f && rect.width() > 26f * density) {
            badgePaint.textSize = (rect.height() * 0.19f).coerceIn(7f * density, 10f * density)
            badgePaint.alpha = if (dimmed) 80 else 210
            canvas.drawText(
                formatWeight(key.width),
                rect.right - 4f * density,
                rect.bottom - 3.5f * density,
                badgePaint
            )
        }
    }

    private fun drawDropIndicator(canvas: Canvas, targetRow: Int, targetIndex: Int) {
        val rs = rowSlots.getOrNull(targetRow) ?: return
        val rowKeys = keySlots.filter { it.rowIndex == targetRow }
        val keyHorizontalGap = currentKeyHorizontalGap()
        val barX = when {
            rowKeys.isEmpty() -> keyboardPadding
            targetIndex <= 0 -> rowKeys.first().rect.left - keyHorizontalGap / 2f
            targetIndex >= rowKeys.size -> rowKeys.last().rect.right + keyHorizontalGap / 2f
            else -> rowKeys[targetIndex].rect.left - keyHorizontalGap / 2f
        }
        val barWidth = 3f * density
        val barRect = RectF(barX - barWidth / 2f, rs.rect.top, barX + barWidth / 2f, rs.rect.bottom)
        canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, dropIndicatorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                dragX = event.x
                dragY = event.y

                touchedAddRow = rowSlots.firstOrNull { it.addButtonRect.contains(event.x, event.y) }?.rowIndex
                touchedKey = if (touchedAddRow == null) {
                    keySlots.firstOrNull { it.rect.contains(event.x, event.y) }
                } else null

                if (isDragMode && touchedKey != null) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    updateDropTarget(event.x, event.y)
                    invalidate()
                }
                return touchedKey != null || touchedAddRow != null
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    dragX = event.x
                    dragY = event.y
                    updateDropTarget(event.x, event.y)
                    invalidate()
                    return true
                }
                val dx = abs(event.x - touchDownX)
                val dy = abs(event.y - touchDownY)
                if (dx > touchSlop || dy > touchSlop) {
                    touchedKey = null
                    touchedAddRow = null
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val src = touchedKey
                    val tRow = dropTargetRow
                    val tIdx = dropTargetIndex
                    cancelDrag()
                    invalidate()
                    if (src != null && tRow >= 0 && tIdx >= 0) {
                        onKeyMoved?.invoke(src.rowIndex, src.keyIndex, tRow, tIdx)
                    }
                    return true
                }

                touchedAddRow?.let { rowIdx ->
                    if (rowSlots.getOrNull(rowIdx)?.addButtonRect?.contains(event.x, event.y) == true) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onAddKeyToRow?.invoke(rowIdx)
                    }
                } ?: touchedKey?.let { slot ->
                    if (slot.rect.contains(event.x, event.y)) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onKeyTapped?.invoke(slot.rowIndex, slot.keyIndex, slot.key)
                    }
                }
                touchedKey = null
                touchedAddRow = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelDrag()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateDropTarget(x: Float, y: Float) {
        if (rowSlots.isEmpty()) {
            dropTargetRow = -1
            dropTargetIndex = -1
            return
        }

        // Find closest row vertically
        var bestRow = 0
        var bestRowDist = Float.MAX_VALUE
        for (rs in rowSlots) {
            val dist = when {
                y < rs.rect.top -> rs.rect.top - y
                y > rs.rect.bottom -> y - rs.rect.bottom
                else -> 0f
            }
            if (dist < bestRowDist) {
                bestRowDist = dist
                bestRow = rs.rowIndex
            }
        }
        dropTargetRow = bestRow

        val rowKeys = keySlots.filter { it.rowIndex == bestRow }
        if (rowKeys.isEmpty()) {
            dropTargetIndex = 0
            return
        }

        var insertIdx = rowKeys.size
        for ((i, slot) in rowKeys.withIndex()) {
            if (x < slot.rect.centerX()) {
                insertIdx = i
                break
            }
        }
        dropTargetIndex = insertIdx
    }

    private fun cancelDrag() {
        isDragging = false
        touchedKey = null
        touchedAddRow = null
        dropTargetRow = -1
        dropTargetIndex = -1
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun formatWeight(w: Float): String {
        val rounded = kotlin.math.round(w * 10f) / 10f
        return if (rounded % 1f == 0f) "${rounded.toInt()}" else "$rounded"
    }
}
