package com.keyfold.terminal.layout

import android.content.Context
import android.util.Log
import com.keyfold.terminal.model.HeightConfig
import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.posture.DevicePosture
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

class LayoutRepository(private val context: Context) {

    companion object {
        private const val TAG = "KeyFoldLayouts"
        private const val ASSETS_LAYOUT_DIR = "layouts"
        private const val CURRENT_LAYOUT_VERSION = 2
        private const val PREFS_NAME = "keyfold_layout_prefs"
        private const val KEY_LAYOUT_VERSION = "exported_layout_version"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val layoutCache = mutableMapOf<String, KeyboardLayout>()

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt(KEY_LAYOUT_VERSION, 0)
        val force = lastVersion < CURRENT_LAYOUT_VERSION
        ensureDefaultLayoutsExported(force = force)
        if (force) {
            prefs.edit().putInt(KEY_LAYOUT_VERSION, CURRENT_LAYOUT_VERSION).apply()
        }
    }

    fun getLayoutsDirectory(): File {
        val dir = context.getExternalFilesDir("layouts") ?: File(context.filesDir, "layouts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun ensureDefaultLayoutsExported(force: Boolean = false) {
        try {
            val dir = getLayoutsDirectory()
            val assetFiles = context.assets.list(ASSETS_LAYOUT_DIR) ?: return
            for (filename in assetFiles) {
                if (filename.endsWith(".json")) {
                    val destFile = File(dir, filename)
                    if (force || !destFile.exists()) {
                        context.assets.open("$ASSETS_LAYOUT_DIR/$filename").use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(TAG, "Exported default layout: $filename to ${destFile.absolutePath}")
                    }
                }
            }
            if (force) {
                reload()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting default layouts: ${e.message}", e)
        }
    }

    fun reload() {
        layoutCache.clear()
        Log.i(TAG, "Layout cache cleared, reloading from disk...")
    }

    fun getLayoutForPosture(posture: DevicePosture): KeyboardLayout {
        val layoutId = posture.defaultLayoutId
        return getLayoutById(layoutId)
    }

    fun getFnLayer(): KeyboardLayout {
        return getLayoutById("fn_layer")
    }

    fun getSymLayer(baseLayoutId: String = "folded_portrait"): KeyboardLayout {
        val specificSymId = "${baseLayoutId}_sym"
        val specific = getLayoutById(specificSymId)
        if (specific.rows.isNotEmpty()) {
            return specific
        }
        return getLayoutById("folded_portrait_sym")
    }

    fun getLayoutById(id: String): KeyboardLayout {
        layoutCache[id]?.let { return it }

        // 1. Try loading from external storage first (user customized)
        val externalFile = File(getLayoutsDirectory(), "$id.json")
        if (externalFile.exists()) {
            try {
                val content = externalFile.readText()
                val layout = json.decodeFromString<KeyboardLayout>(content)
                layoutCache[id] = layout
                Log.d(TAG, "Loaded layout '$id' from external storage: ${externalFile.absolutePath}")
                return layout
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse external layout file ${externalFile.name}, falling back to asset: ${e.message}")
            }
        }

        // 2. Fall back to bundled assets
        try {
            val assetPath = "$ASSETS_LAYOUT_DIR/$id.json"
            val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val layout = json.decodeFromString<KeyboardLayout>(content)
            layoutCache[id] = layout
            Log.d(TAG, "Loaded layout '$id' from assets: $assetPath")
            return layout
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load layout '$id' from assets: ${e.message}", e)
        }

        // 3. Fallback emergency layout
        val fallback = KeyboardLayout(
            id = id,
            name = "Emergency Fallback",
            rows = emptyList()
        )
        return fallback
    }

    fun listAvailableLayouts(): List<File> {
        val dir = getLayoutsDirectory()
        return dir.listFiles { file -> file.extension == "json" }?.toList() ?: emptyList()
    }

    fun saveLayoutHeight(layoutId: String, heightConfig: HeightConfig): Boolean {
        return try {
            val currentLayout = getLayoutById(layoutId)
            val updatedLayout = currentLayout.copy(height = heightConfig)
            layoutCache[layoutId] = updatedLayout

            val dir = getLayoutsDirectory()
            val file = File(dir, "$layoutId.json")
            val jsonString = json.encodeToString(KeyboardLayout.serializer(), updatedLayout)
            file.writeText(jsonString)
            Log.i(TAG, "Saved updated height config to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save layout height: ${e.message}", e)
            false
        }
    }
}
