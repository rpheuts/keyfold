package com.keyfold.terminal.settings

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keyfold.terminal.R
import com.keyfold.terminal.ime.KeyFoldInputMethodService
import com.keyfold.terminal.layout.LayoutRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        layoutRepository = LayoutRepository(this)
        postureDetector = DevicePostureDetector(this, activityScope)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_background))
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header Title
        val titleText = TextView(this).apply {
            text = "KeyFold Developer Keyboard"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_accent))
            setPadding(0, 0, 0, 16)
        }
        root.addView(titleText)

        // Subtitle & Posture Status
        postureStatusText = TextView(this).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 0, 0, 24)
            typeface = Typeface.MONOSPACE
        }
        root.addView(postureStatusText)

        // Button Row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val enableImeButton = Button(this).apply {
            text = "Enable in System Settings"
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
                Toast.makeText(this@KeyFoldSettingsActivity, "Layouts reloaded!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(reloadButton)

        val exportButton = Button(this).apply {
            text = "Reset Default JSONs"
            setOnClickListener {
                layoutRepository.ensureDefaultLayoutsExported()
                updatePostureDisplay()
                Toast.makeText(this@KeyFoldSettingsActivity, "Default JSONs re-exported!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonRow.addView(exportButton)
        root.addView(buttonRow)

        // Storage path info
        val storageInfoText = TextView(this).apply {
            val dir = layoutRepository.getLayoutsDirectory().absolutePath
            text = "Config JSON Path:\n$dir\n(Directly editable in Termux via vim/nano)"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_secondary))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }
        root.addView(storageInfoText)

        // Test Input Field
        val testLabel = TextView(this).apply {
            text = "Interactive Test Area:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setPadding(0, 0, 0, 8)
        }
        root.addView(testLabel)

        testInputField = EditText(this).apply {
            hint = "Tap here to test typing, Ctrl+C, arrows, etc."
            setTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_primary))
            setHintTextColor(ContextCompat.getColor(this@KeyFoldSettingsActivity, R.color.kb_text_secondary))
            setBackgroundColor(Color.parseColor("#1C2028"))
            setPadding(24, 24, 24, 24)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }
        root.addView(testInputField)

        // Keyboard Live Preview
        previewKeyboardView = KeyFoldKeyboardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                550
            )
            onKeyPressed = { key ->
                if (!key.output.isNullOrEmpty()) {
                    testInputField.append(key.output)
                } else if (key.label.length == 1) {
                    testInputField.append(key.label)
                }
            }
        }
        root.addView(previewKeyboardView)

        setContentView(root)

        postureDetector.setOnPostureChangedListener { _, _ ->
            updatePostureDisplay()
        }
        postureDetector.start()
        updatePostureDisplay()
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
    }

    private fun updatePostureDisplay() {
        val posture = postureDetector.posture.value
        val config = resources.configuration
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val sw = config.smallestScreenWidthDp

        postureStatusText.text = "Detected Posture: ${posture.name}\n" +
                "Description: ${posture.description}\n" +
                "Screen: sw=${sw}dp, orientation=${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}\n" +
                "Active Layout: ${posture.defaultLayoutId}.json"

        val layout = layoutRepository.getLayoutForPosture(posture)
        previewKeyboardView.currentLayout = layout
    }
}
