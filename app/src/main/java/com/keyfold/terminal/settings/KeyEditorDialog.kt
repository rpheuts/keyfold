package com.keyfold.terminal.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyType
import kotlin.math.roundToInt

/**
 * Popup dialog for editing or adding a [KeyDefinition] in the visual layout editor.
 */
object KeyEditorDialog {

    private val commonCodes = listOf(
        "SPACE", "ENTER", "DEL", "SHIFT", "CTRL", "ALT",
        "SYM", "FN", "TAB", "ESC", "LEFT", "DOWN", "UP", "RIGHT"
    )

    fun show(
        context: Context,
        initialKey: KeyDefinition,
        isNewKey: Boolean,
        onSave: (KeyDefinition) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#1C2028"))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(container)

        fun addSectionLabel(text: String): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#8B949E"))
                setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
                container.addView(this)
            }
        }

        fun addDarkEditText(initialValue: String?, hintText: String): EditText {
            return EditText(context).apply {
                setText(initialValue ?: "")
                hint = hintText
                setTextColor(Color.parseColor("#F0F6FC"))
                setHintTextColor(Color.parseColor("#6E7681"))
                setBackgroundColor(Color.parseColor("#0D1117"))
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                textSize = 14f
                typeface = Typeface.MONOSPACE
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                container.addView(this)
            }
        }

        // Label
        addSectionLabel("Label (displayed on keycap)")
        val labelInput = addDarkEditText(initialKey.label, "e.g. a, ⇧, ⏎, SPACE")

        // Type
        addSectionLabel("Key Type")
        val keyTypes = KeyType.values().toList()
        val typeSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                keyTypes.map { it.name }
            )
            setSelection(keyTypes.indexOf(initialKey.type).coerceAtLeast(0))
        }
        container.addView(typeSpinner)

        // Weight (width)
        var currentWeight = ((initialKey.width.coerceIn(0.2f, 6.0f) * 10f).roundToInt()) / 10f
        val weightLabel = addSectionLabel("Weight (Width): ${currentWeight}x")
        weightLabel.setTextColor(Color.parseColor("#58A6FF"))

        val weightRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val weightSeekBar = SeekBar(context).apply {
            max = 58 // 0.2 .. 6.0 in 0.1 steps -> progress 0..58
            progress = ((currentWeight - 0.2f) * 10f).roundToInt().coerceIn(0, 58)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun updateWeightDisplay() {
            currentWeight = ((weightSeekBar.progress + 2) / 10f)
            weightLabel.text = "Weight (Width): ${currentWeight}x"
        }
        weightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateWeightDisplay()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val minusWeightBtn = Button(context).apply {
            text = "-0.1"
            setOnClickListener {
                weightSeekBar.progress = (weightSeekBar.progress - 1).coerceAtLeast(0)
            }
        }
        val plusWeightBtn = Button(context).apply {
            text = "+0.1"
            setOnClickListener {
                weightSeekBar.progress = (weightSeekBar.progress + 1).coerceAtMost(weightSeekBar.max)
            }
        }
        weightRow.addView(minusWeightBtn)
        weightRow.addView(weightSeekBar)
        weightRow.addView(plusWeightBtn)
        container.addView(weightRow)

        // Code + Quick-Pick Chips
        addSectionLabel("Code (optional, for actions/modifiers/layers)")
        val codeInput = addDarkEditText(initialKey.code, "e.g. SPACE, ENTER, DEL, SHIFT")

        val codeChipsScroll = HorizontalScrollView(context).apply {
            setPadding(0, (6 * density).toInt(), 0, (4 * density).toInt())
        }
        val codeChipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (code in commonCodes) {
            val chip = Button(context).apply {
                text = code
                textSize = 11f
                setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                setOnClickListener { codeInput.setText(code) }
            }
            codeChipsRow.addView(chip)
        }
        codeChipsScroll.addView(codeChipsRow)
        container.addView(codeChipsScroll)

        // Output
        addSectionLabel("Output (optional text inserted on tap)")
        val outputInput = addDarkEditText(initialKey.output, "Leave blank to use label")

        // Shift & Shift Output
        addSectionLabel("Shift Label (optional)")
        val shiftInput = addDarkEditText(initialKey.shift, "e.g. ! or A")

        addSectionLabel("Shift Output (optional)")
        val shiftOutputInput = addDarkEditText(initialKey.shiftOutput, "Leave blank to use Shift Label")

        // Hint
        addSectionLabel("Hint (top-right corner label, optional)")
        val hintInput = addDarkEditText(initialKey.hint, "e.g. 1, ?")

        // Flags
        val flagsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }
        val stickySwitch = Switch(context).apply {
            text = "Sticky  "
            isChecked = initialKey.sticky
            setTextColor(Color.parseColor("#F0F6FC"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val repeatSwitch = Switch(context).apply {
            text = "Repeat  "
            isChecked = initialKey.repeat
            setTextColor(Color.parseColor("#F0F6FC"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        flagsRow.addView(stickySwitch)
        flagsRow.addView(repeatSwitch)
        container.addView(flagsRow)

        val builder = AlertDialog.Builder(context)
            .setTitle(if (isNewKey) "Add Key" else "Edit Key")
            .setView(scrollView)
            .setPositiveButton(if (isNewKey) "Add" else "Apply") { _, _ ->
                val selectedType = keyTypes[typeSpinner.selectedItemPosition]
                val updated = KeyDefinition(
                    label = labelInput.text.toString(),
                    shift = shiftInput.text.toString().ifEmpty { null },
                    output = outputInput.text.toString().ifEmpty { null },
                    shiftOutput = shiftOutputInput.text.toString().ifEmpty { null },
                    hint = hintInput.text.toString().ifEmpty { null },
                    code = codeInput.text.toString().trim().ifEmpty { null },
                    type = selectedType,
                    width = currentWeight,
                    sticky = stickySwitch.isChecked,
                    repeat = repeatSwitch.isChecked
                )
                onSave(updated)
            }
            .setNegativeButton("Cancel", null)

        if (!isNewKey && onDelete != null) {
            builder.setNeutralButton("Delete Key") { _, _ ->
                onDelete()
            }
        }

        val dialog = builder.create()
        dialog.show()
        if (!isNewKey && onDelete != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.parseColor("#F85149"))
        }
    }
}
