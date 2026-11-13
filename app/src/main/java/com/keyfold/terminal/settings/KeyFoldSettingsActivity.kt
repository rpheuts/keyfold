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
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keyfold.terminal.R
import com.keyfold.terminal.ime.KeyFoldInputMethodService
import com.keyfold.terminal.layout.LayoutEditorMutator
import com.keyfold.terminal.layout.LayoutRepository
import com.keyfold.terminal.model.HeightConfig
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.posture.DevicePosture
import com.keyfold.terminal.posture.DevicePostureDetector
import com.keyfold.terminal.ui.VisualLayoutEditorView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.cancel

class KeyFoldSettingsActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var layoutRepository: LayoutRepository
    private lateinit var postureDetector: DevicePostureDetector

    private lateinit var postureStatusText: TextView

    // Height Configuration UI
    private lateinit var modeSpinner: Spinner
    private lateinit var heightModeGroup: RadioGroup
    private lateinit var radioPercent: RadioButton
    private lateinit var radioHinge: RadioButton
    private lateinit var radioDp: RadioButton
    private lateinit var heightSlider: SeekBar
    private lateinit var heightValueLabel: TextView
    private lateinit var saveStatusLabel: TextView

    // Visual Layout Editor UI
    private lateinit var layoutSpinner: Spinner
    private lateinit var visualEditorView: VisualLayoutEditorView
    private lateinit var dragModeSwitch: Switch
    private lateinit var undoBtn: Button
    private var editingLayout: KeyboardLayout? = null
    private val undoStack = mutableListOf<KeyboardLayout>()
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

        // In-App Visual Layout Editor
        val editorSectionTitle = TextView(this).apply {
            text = "Visual Layout Editor"
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

        layoutSpinner = Spinner(this).apply {
            val names = editorLayoutList.map { "$it.json" }
            adapter = ArrayAdapter(this@KeyFoldSettingsActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setPadding(0, 0, 0, 12)
        }
        editorCard.addView(layoutSpinner)

        val visualHintText = TextView(this).apply {
            text = "Tap a key to edit/resize/delete, tap '+' to add a key, or enable Drag Mode to move keys."
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, 10)
        }
        editorCard.addView(visualHintText)

        dragModeSwitch = Switch(this).apply {
            text = "Drag Mode (Move Keys)"
            setTextColor(Color.parseColor("#F0F6FC"))
            textSize = 14f
            isChecked = false
            setPadding(0, 0, 0, 10)
            setOnCheckedChangeListener { _, isChecked ->
                visualEditorView.isDragMode = isChecked
            }
        }
        editorCard.addView(dragModeSwitch)

        val visualActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        val addRowBtn = Button(this).apply {
            text = "+ Row"
            setOnClickListener {
                editingLayout?.let { layout ->
                    applyVisualMutation(LayoutEditorMutator.addRow(layout))
                }
            }
        }
        undoBtn = Button(this).apply {
            text = "Undo"
            isEnabled = false
            setOnClickListener {
                if (undoStack.isNotEmpty()) {
                    editingLayout = undoStack.removeAt(undoStack.lastIndex)
                    visualEditorView.currentLayout = editingLayout
                    undoBtn.isEnabled = undoStack.isNotEmpty()
                    updateVisualEditorHeight()
                }
            }
        }
        val saveVisualBtn = Button(this).apply {
            text = "Save & Apply"
            setOnClickListener { saveCurrentVisualLayout() }
        }
        val resetVisualBtn = Button(this).apply {
            text = "Reset"
            setOnClickListener { resetSelectedLayout() }
        }
        visualActionRow.addView(addRowBtn)
        visualActionRow.addView(undoBtn)
        visualActionRow.addView(saveVisualBtn)
        visualActionRow.addView(resetVisualBtn)
        editorCard.addView(visualActionRow)

        visualEditorView = VisualLayoutEditorView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (260 * resources.displayMetrics.density).toInt()
            )
            onKeyTapped = { rowIndex, keyIndex, key ->
                KeyEditorDialog.show(
                    context = this@KeyFoldSettingsActivity,
                    initialKey = key,
                    isNewKey = false,
                    onSave = { updatedKey ->
                        editingLayout?.let { layout ->
                            applyVisualMutation(LayoutEditorMutator.updateKey(layout, rowIndex, keyIndex, updatedKey))
                        }
                    },
                    onDelete = {
                        editingLayout?.let { layout ->
                            applyVisualMutation(LayoutEditorMutator.deleteKey(layout, rowIndex, keyIndex))
                        }
                    }
                )
            }
            onAddKeyToRow = { rowIndex ->
                KeyEditorDialog.show(
                    context = this@KeyFoldSettingsActivity,
                    initialKey = KeyDefinition(label = "", width = 1.0f),
                    isNewKey = true,
                    onSave = { newKey ->
                        editingLayout?.let { layout ->
                            applyVisualMutation(LayoutEditorMutator.addKey(layout, rowIndex, newKey))
                        }
                    }
                )
            }
            onKeyMoved = { fromRow, fromIndex, toRow, toIndex ->
                editingLayout?.let { layout ->
                    applyVisualMutation(LayoutEditorMutator.moveKey(layout, fromRow, fromIndex, toRow, toIndex))
                }
            }
        }
        editorCard.addView(visualEditorView)
        root.addView(editorCard)

        layoutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadSelectedLayoutIntoEditor(editorLayoutList[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Appearance & Frosted Glass Card
        val appearanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1C2028"))
            setPadding(28, 24, 28, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val appearanceTitle = TextView(this).apply {
            text = "✨ Appearance & Glass Effects"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 0, 0, 8)
        }
        appearanceCard.addView(appearanceTitle)

        val prefs = getSharedPreferences("keyfold_prefs", Context.MODE_PRIVATE)
        val frostedSwitch = Switch(this).apply {
            text = "Frosted Glass & Background Blur"
            setTextColor(Color.parseColor("#F0F6FC"))
            textSize = 15f
            isChecked = prefs.getBoolean("frosted_glass_enabled", true)
        }
        appearanceCard.addView(frostedSwitch)

        val appearanceDesc = TextView(this).apply {
            text = "Uses translucent smoked glass keycaps and Android 12+ hardware compositor background blur (setBackgroundBlurRadius). Blurs underlying content in apps that pan or don't resize."
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 6, 0, 0)
        }
        appearanceCard.addView(appearanceDesc)

        val opacityContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
            visibility = if (frostedSwitch.isChecked) View.VISIBLE else View.GONE
        }

        val currentOpacity = prefs.getInt("frosted_bg_opacity", 75)
        val opacityLabel = TextView(this).apply {
            text = "Background Opacity: $currentOpacity%"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        opacityContainer.addView(opacityLabel)

        val opacitySliderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val minusOpacityBtn = Button(this).apply {
            text = "- 5%"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val s = opacitySliderRow.getChildAt(1) as? SeekBar
                s?.let { it.progress = (it.progress - 5).coerceAtLeast(10) }
            }
        }
        opacitySliderRow.addView(minusOpacityBtn)

        val opacitySlider = SeekBar(this).apply {
            min = 10
            max = 100
            progress = currentOpacity
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    opacityLabel.text = "Background Opacity: $progress%"
                    if (fromUser) {
                        prefs.edit().putInt("frosted_bg_opacity", progress).apply()
                        sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        opacitySliderRow.addView(opacitySlider)

        val plusOpacityBtn = Button(this).apply {
            text = "+ 5%"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val s = opacitySliderRow.getChildAt(1) as? SeekBar
                s?.let { it.progress = (it.progress + 5).coerceAtMost(100) }
            }
        }
        opacitySliderRow.addView(plusOpacityBtn)
        opacityContainer.addView(opacitySliderRow)
        appearanceCard.addView(opacityContainer)

        frostedSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("frosted_glass_enabled", isChecked).apply()
            opacityContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
            val stateText = if (isChecked) "Enabled" else "Disabled"
            Toast.makeText(this@KeyFoldSettingsActivity, "Frosted Glass: $stateText", Toast.LENGTH_SHORT).show()
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val isBlurSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wm?.isCrossWindowBlurEnabled == true
        } else false

        val blurStatusText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            if (isBlurSupported) {
                text = "✓ Hardware Blur Engine: Active (Supported)"
                setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_led_active))
            } else {
                text = "⚠ Hardware Blur: Disabled by System / One UI\n  (Tip: Ensure Power Saving is OFF, check Settings > Accessibility > Vision enhancements > Reduce transparency and blur, or Developer options > Allow window-level blurs)"
                setTextColor(Color.parseColor("#FFCC00"))
            }
            setPadding(0, 10, 0, 0)
        }
        appearanceCard.addView(blurStatusText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && wm != null) {
            try {
                wm.addCrossWindowBlurEnabledListener { enabled ->
                    runOnUiThread {
                        if (enabled) {
                            blurStatusText.text = "✓ Hardware Blur Engine: Active (Supported)"
                            blurStatusText.setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_led_active))
                        } else {
                            blurStatusText.text = "⚠ Hardware Blur: Disabled by System / One UI\n  (Tip: Ensure Power Saving is OFF, check Settings > Accessibility > Vision enhancements > Reduce transparency and blur, or Developer options > Allow window-level blurs)"
                            blurStatusText.setTextColor(Color.parseColor("#FFCC00"))
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        root.addView(appearanceCard)

        setContentView(scrollView)

        postureDetector.setOnPostureChangedListener { _, _ ->
            runOnUiThread {
                updatePostureDisplay()
            }
        }
        postureDetector.start()
        updatePostureDisplay()
        loadCurrentModeHeightConfig()
        if (editorLayoutList.isNotEmpty()) {
            loadSelectedLayoutIntoEditor(editorLayoutList[0])
        }
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
        }
    }

    private fun loadSelectedLayoutIntoEditor(layoutId: String) {
        editingLayout = layoutRepository.getLayoutById(layoutId)
        undoStack.clear()
        undoBtn.isEnabled = false
        visualEditorView.currentLayout = editingLayout
        updateVisualEditorHeight()
    }

    private fun applyVisualMutation(updated: KeyboardLayout) {
        editingLayout?.let { previous ->
            undoStack.add(previous)
            if (undoStack.size > 30) undoStack.removeAt(0)
        }
        editingLayout = updated
        visualEditorView.currentLayout = updated
        undoBtn.isEnabled = undoStack.isNotEmpty()
        updateVisualEditorHeight()
    }

    private fun updateVisualEditorHeight() {
        val rows = editingLayout?.rows?.size ?: 4
        val density = resources.displayMetrics.density
        val targetHeight = ((rows * 56 + 16) * density).toInt().coerceAtLeast((180 * density).toInt())
        visualEditorView.layoutParams.height = targetHeight
        visualEditorView.requestLayout()
    }

    private fun saveCurrentVisualLayout() {
        val layout = editingLayout ?: return
        val (success, error) = layoutRepository.saveLayout(layout)
        if (success) {
            undoStack.clear()
            undoBtn.isEnabled = false
            sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
            updatePostureDisplay()
            loadCurrentModeHeightConfig()
            Toast.makeText(this, "Saved and applied ${layout.id}.json!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to save layout: $error", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetSelectedLayout() {
        val selectedId = editorLayoutList[layoutSpinner.selectedItemPosition]
        if (layoutRepository.resetLayoutToDefault(selectedId)) {
            sendBroadcast(Intent(KeyFoldInputMethodService.ACTION_RELOAD_CONFIG))
            loadSelectedLayoutIntoEditor(selectedId)
            updatePostureDisplay()
            loadCurrentModeHeightConfig()
            Toast.makeText(this, "Reset $selectedId.json to default!", Toast.LENGTH_SHORT).show()
        }
    }
}
