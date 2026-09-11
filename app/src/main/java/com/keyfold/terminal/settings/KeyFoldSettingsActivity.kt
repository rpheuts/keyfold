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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.InputType
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

    // JSON Editor UI
    private lateinit var jsonLayoutSpinner: Spinner
    private lateinit var jsonEditorText: EditText
    private val editorLayoutList = listOf(
        "folded_portrait",
        "folded_portrait_sym",
        "unfolded_landscape_pda",
        "unfolded_landscape_flat",
        "unfolded_portrait",
        "folded_landscape",
        "fn_layer"
    )

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

        // Termux / File Storage Location Card
        val storageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C2028"))
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val storageTitle = TextView(this).apply {
            text = "📁 Termux & File Manager Access"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 0, 0, 8)
        }
        storageCard.addView(storageTitle)

        val termuxPath = "~/storage/shared/Documents/KeyFold/layouts/"
        val activeDir = layoutRepository.getLayoutsDirectory().absolutePath
        val pathDesc = TextView(this).apply {
            text = "Android Scoped Storage blocks apps from accessing /sdcard/Android/data/.\n\nKeyFold exports layouts to your shared Documents directory so you can edit them directly in Termux, Samsung My Files, or any code editor:\n\n• Termux: $termuxPath\n• Android: $activeDir"
            textSize = 13f
            setTextColor(Color.parseColor("#C9D1D9"))
            setPadding(0, 0, 0, 16)
        }
        storageCard.addView(pathDesc)

        val storageBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val copyPathBtn = Button(this).apply {
            text = "Copy Termux Path"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("KeyFold Layouts Path", termuxPath)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@KeyFoldSettingsActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        storageBtnRow.addView(copyPathBtn)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val permBtn = Button(this).apply {
                text = "Grant Storage Permission"
                setTextColor(Color.parseColor("#FFCC00"))
                setOnClickListener {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            }
            storageBtnRow.addView(permBtn)
        }
        storageCard.addView(storageBtnRow)
        root.addView(storageCard)

        // In-App JSON Layout Editor
        val editorSectionTitle = TextView(this).apply {
            text = "In-App Layout JSON Editor"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 12, 0, 12)
        }
        root.addView(editorSectionTitle)

        val editorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C2028"))
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val selectLayoutLabel = TextView(this).apply {
            text = "Select Layout to Edit:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 0, 0, 8)
        }
        editorCard.addView(selectLayoutLabel)

        jsonLayoutSpinner = Spinner(this).apply {
            val names = editorLayoutList.map { "$it.json" }
            adapter = ArrayAdapter(this@KeyFoldSettingsActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setPadding(0, 0, 0, 16)
        }
        editorCard.addView(jsonLayoutSpinner)

        jsonEditorText = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextColor(Color.parseColor("#E6EDF3"))
            setBackgroundColor(Color.parseColor("#0D1117"))
            setPadding(24, 24, 24, 24)
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (280 * resources.displayMetrics.density).toInt()
            ).apply { setMargins(0, 0, 0, 16) }
        }
        editorCard.addView(jsonEditorText)

        val editorBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val saveJsonBtn = Button(this).apply {
            text = "Save & Apply"
            setOnClickListener {
                val selectedId = editorLayoutList[jsonLayoutSpinner.selectedItemPosition]
                val content = jsonEditorText.text.toString()
                val (success, error) = layoutRepository.saveLayoutRaw(selectedId, content)
                if (success) {
                    sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
                    updatePostureDisplay()
                    loadCurrentModeHeightConfig()
                    Toast.makeText(this@KeyFoldSettingsActivity, "Saved and applied $selectedId.json!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@KeyFoldSettingsActivity, "JSON Syntax Error: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
        editorBtnRow.addView(saveJsonBtn)

        val resetLayoutBtn = Button(this).apply {
            text = "Reset This Layout"
            setOnClickListener {
                val selectedId = editorLayoutList[jsonLayoutSpinner.selectedItemPosition]
                if (layoutRepository.resetLayoutToDefault(selectedId)) {
                    sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
                    jsonEditorText.setText(layoutRepository.getLayoutRaw(selectedId))
                    updatePostureDisplay()
                    loadCurrentModeHeightConfig()
                    Toast.makeText(this@KeyFoldSettingsActivity, "Reset $selectedId.json to default!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        editorBtnRow.addView(resetLayoutBtn)
        editorCard.addView(editorBtnRow)
        root.addView(editorCard)

        jsonLayoutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedId = editorLayoutList[position]
                jsonEditorText.setText(layoutRepository.getLayoutRaw(selectedId))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        if (editorLayoutList.isNotEmpty()) {
            jsonEditorText.setText(layoutRepository.getLayoutRaw(editorLayoutList[0]))
        }

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
