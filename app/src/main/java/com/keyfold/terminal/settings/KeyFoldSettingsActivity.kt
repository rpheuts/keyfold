package com.keyfold.terminal.settings

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keyfold.terminal.R
import com.keyfold.terminal.ime.KeyFoldInputMethodService
import com.keyfold.terminal.layout.LayoutRepository
import com.keyfold.terminal.model.HeightConfig
import com.keyfold.terminal.posture.DevicePosture
import com.keyfold.terminal.posture.DevicePostureDetector
import com.keyfold.terminal.ui.KeyFoldKeyboardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class KeyFoldSettingsActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var layoutRepository: LayoutRepository
    private lateinit var postureDetector: DevicePostureDetector

    private lateinit var postureStatusText: TextView
    private lateinit var previewKeyboardView: KeyFoldKeyboardView
    private lateinit var testInputField: EditText

    // Height Configuration UI
    private lateinit var modeSpinner: Spinner
    private lateinit var heightModeGroup: RadioGroup
    private lateinit var radioPercent: RadioButton
    private lateinit var radioHinge: RadioButton
    private lateinit var radioDp: RadioButton
    private lateinit var heightSlider: SeekBar
    private lateinit var heightValueLabel: TextView
    private lateinit var saveStatusLabel: TextView

    private val posturesList = listOf(
        DevicePosture.UNFOLDED_LANDSCAPE_HALF,
        DevicePosture.UNFOLDED_LANDSCAPE_FLAT,
        DevicePosture.UNFOLDED_PORTRAIT_FLAT,
        DevicePosture.FOLDED_PORTRAIT,
        DevicePosture.FOLDED_LANDSCAPE
    )

    private var selectedPosture: DevicePosture = DevicePosture.UNFOLDED_LANDSCAPE_HALF
    private var isUpdatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        layoutRepository = LayoutRepository(this)
        postureDetector = DevicePostureDetector(this, activityScope)
        selectedPosture = postureDetector.posture.value

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_background))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(root)

        // Header Title
        val titleText = TextView(this).apply {
            text = "KeyFold Developer Keyboard"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 0, 0, 12)
        }
        root.addView(titleText)

        // Posture Status
        postureStatusText = TextView(this).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 0, 0, 20)
            typeface = Typeface.MONOSPACE
        }
        root.addView(postureStatusText)

        // System Action Buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val enableImeButton = Button(this).apply {
            text = "System Settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }
        buttonRow.addView(enableImeButton)

        val reloadButton = Button(this).apply {
            text = "Reload Layouts"
            setOnClickListener {
                sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
                updatePostureDisplay()
                loadCurrentModeHeightConfig()
                Toast.makeText(this@KeyFoldSettingsActivity, "Layouts reloaded!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(reloadButton)

        val exportButton = Button(this).apply {
            text = "Reset Defaults"
            setOnClickListener {
                layoutRepository.ensureDefaultLayoutsExported(force = true)
                sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
                updatePostureDisplay()
                loadCurrentModeHeightConfig()
                Toast.makeText(this@KeyFoldSettingsActivity, "Default JSONs re-exported!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(exportButton)
        root.addView(buttonRow)

        // Height Configuration Section Header
        val heightSectionTitle = TextView(this).apply {
            text = "Keyboard Height Tuning"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 12, 0, 12)
        }
        root.addView(heightSectionTitle)

        val heightCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C2028"))
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val modeLabel = TextView(this).apply {
            text = "Select Mode to Adjust:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 0, 0, 8)
        }
        heightCard.addView(modeLabel)

        modeSpinner = Spinner(this).apply {
            val names = posturesList.map { p ->
                val shortName = when (p) {
                    DevicePosture.UNFOLDED_LANDSCAPE_HALF -> "Tabletop / PDA Mode (Flex)"
                    DevicePosture.UNFOLDED_LANDSCAPE_FLAT -> "Tablet Landscape (Flat)"
                    DevicePosture.UNFOLDED_PORTRAIT_FLAT -> "Tablet Portrait"
                    DevicePosture.FOLDED_PORTRAIT -> "Cover Screen Portrait"
                    DevicePosture.FOLDED_LANDSCAPE -> "Cover Screen Landscape"
                    else -> p.name
                }
                "$shortName [${p.defaultLayoutId}.json]"
            }
            val adapter = ArrayAdapter(this@KeyFoldSettingsActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setAdapter(adapter)
            setSelection(posturesList.indexOf(selectedPosture).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedPosture = posturesList[position]
                    loadCurrentModeHeightConfig()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        heightCard.addView(modeSpinner)

        // Height Mode Radio Group
        val modeTypeLabel = TextView(this).apply {
            text = "Height Calculation Mode:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 16, 0, 6)
        }
        heightCard.addView(modeTypeLabel)

        heightModeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        radioPercent = RadioButton(this).apply {
            text = "Percentage of Screen (Recommended)"
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
        }
        radioHinge = RadioButton(this).apply {
            text = "Snap Below Hinge (PDA Tabletop)"
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
        }
        radioDp = RadioButton(this).apply {
            text = "Fixed DP Height"
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
        }

        heightModeGroup.addView(radioPercent)
        heightModeGroup.addView(radioHinge)
        heightModeGroup.addView(radioDp)
        heightCard.addView(heightModeGroup)

        heightModeGroup.setOnCheckedChangeListener { _, _ ->
            if (!isUpdatingUi) {
                saveAndApplyHeight()
            }
        }

        // Height Value Label & Steppers
        heightValueLabel = TextView(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 16, 0, 8)
        }
        heightCard.addView(heightValueLabel)

        val sliderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val minusBtn = Button(this).apply {
            text = "- 1%"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                heightSlider.progress = (heightSlider.progress - 1).coerceAtLeast(0)
            }
        }
        sliderRow.addView(minusBtn)

        heightSlider = SeekBar(this).apply {
            max = 40 // 25% + 40 = 65%
            progress = 20 // default 45%
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateSliderLabel()
                    if (fromUser && !isUpdatingUi) {
                        saveAndApplyHeight()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        sliderRow.addView(heightSlider)

        val plusBtn = Button(this).apply {
            text = "+ 1%"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                heightSlider.progress = (heightSlider.progress + 1).coerceAtMost(heightSlider.max)
            }
        }
        sliderRow.addView(plusBtn)
        heightCard.addView(sliderRow)

        saveStatusLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_led_active))
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 0)
        }
        heightCard.addView(saveStatusLabel)

        root.addView(heightCard)

        // Storage path info
        val storageInfoText = TextView(this).apply {
            val dir = layoutRepository.getLayoutsDirectory().absolutePath
            text = "JSON Path: $dir\n(Any edits here or via Termux auto-sync)"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_secondary))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 20)
        }
        root.addView(storageInfoText)

        // Interactive Test Area
        val testLabel = TextView(this).apply {
            text = "Interactive Test & Preview Area:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 0, 0, 8)
        }
        root.addView(testLabel)

        testInputField = EditText(this).apply {
            hint = "Test typing here..."
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setHintTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_secondary))
            setBackgroundColor(Color.parseColor("#1C2028"))
            setPadding(24, 24, 24, 24)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
        }
        root.addView(testInputField)

        // Keyboard Live Preview
        previewKeyboardView = KeyFoldKeyboardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onKeyPressed = { key ->
                if (!key.output.isNullOrEmpty()) {
                    testInputField.append(key.output)
                } else if (key.label.length == 1) {
                    testInputField.append(key.label)
                }
            }
            onCursorMove = { stepsX, _ ->
                val current = testInputField.selectionStart
                val len = testInputField.text.length
                val target = (current + stepsX).coerceIn(0, len)
                testInputField.setSelection(target)
            }
        }
        root.addView(previewKeyboardView)

        setContentView(scrollView)

        postureDetector.setOnPostureChangedListener { _, _ ->
            runOnUiThread {
                updatePostureDisplay()
            }
        }
        postureDetector.start()
        updatePostureDisplay()
        loadCurrentModeHeightConfig()
    }

    override fun onDestroy() {
        super.onDestroy()
        postureDetector.stop()
        activityScope.cancel()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        postureDetector.onConfigurationChanged()
        updatePostureDisplay()
        loadCurrentModeHeightConfig()
    }

    private fun updatePostureDisplay() {
        val currentPosture = postureDetector.posture.value
        val config = resources.configuration
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sw = config.smallestScreenWidthDp

        postureStatusText.text = "Device Posture: ${currentPosture.name}\n" +
                "Screen: sw=${sw}dp, ${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}\n" +
                "Active Layout: ${currentPosture.defaultLayoutId}.json"
    }

    private fun loadCurrentModeHeightConfig() {
        isUpdatingUi = true
        val layout = layoutRepository.getLayoutById(selectedPosture.defaultLayoutId)
        val h = layout.height

        when (h.mode) {
            "fold_hinge" -> radioHinge.isChecked = true
            "dp" -> radioDp.isChecked = true
            else -> radioPercent.isChecked = true
        }

        val percent = if (h.percent > 0) h.percent else h.fallbackPercent
        heightSlider.progress = (percent - 25).coerceIn(0, heightSlider.max)
        updateSliderLabel()

        // Update preview keyboard
        previewKeyboardView.currentLayout = layout
        val calculatedHeight = postureDetector.calculateKeyboardHeight(selectedPosture, h)
        previewKeyboardView.layoutParams.height = calculatedHeight
        previewKeyboardView.requestLayout()
        previewKeyboardView.invalidate()

        saveStatusLabel.text = "Loaded from ${selectedPosture.defaultLayoutId}.json"
        isUpdatingUi = false
    }

    private fun updateSliderLabel() {
        val percent = heightSlider.progress + 25
        heightValueLabel.text = "Height: $percent% of screen"
    }

    private fun saveAndApplyHeight() {
        val percent = heightSlider.progress + 25
        val mode = when {
            radioHinge.isChecked -> "fold_hinge"
            radioDp.isChecked -> "dp"
            else -> "percentage"
        }

        val newHeight = HeightConfig(
            mode = mode,
            percent = percent,
            fallbackPercent = percent,
            offsetDp = 0
        )

        val layoutId = selectedPosture.defaultLayoutId
        val success = layoutRepository.saveLayoutHeight(layoutId, newHeight)
        if (success) {
            saveStatusLabel.text = "Saved & applied to $layoutId.json ($percent%)"
            sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))

            // Update preview
            val updatedLayout = layoutRepository.getLayoutById(layoutId)
            previewKeyboardView.currentLayout = updatedLayout
            val calculatedHeight = postureDetector.calculateKeyboardHeight(selectedPosture, newHeight)
            previewKeyboardView.layoutParams.height = calculatedHeight
            previewKeyboardView.requestLayout()
            previewKeyboardView.invalidate()
        }
    }
}
