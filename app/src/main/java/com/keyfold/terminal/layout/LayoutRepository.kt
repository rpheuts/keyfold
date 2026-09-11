package com.keyfold.terminal.layout

import android.content.Context
import android.os.Environment
import android.util.Log
import com.keyfold.terminal.model.HeightConfig
import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.posture.DevicePosture
import kotlinx.serialization.json.Json
import java.io.File

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

    fun getPublicDocumentsLayoutsDirectory(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "KeyFold/layouts")
    }

    fun getAppSpecificLayoutsDirectory(): File {
        return context.getExternalFilesDir("layouts") ?: File(context.filesDir, "layouts")
    }

    fun getLayoutsDirectory(): File {
        val publicDir = getPublicDocumentsLayoutsDirectory()
        try {
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }
            if (publicDir.exists() && publicDir.canWrite()) {
                return publicDir
            }
        } catch (_: Exception) {}

        val appDir = getAppSpecificLayoutsDirectory()
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        return appDir
    }

    fun ensureDefaultLayoutsExported(force: Boolean = false) {
        val targets = mutableListOf<File>()
        try {
            val pub = getPublicDocumentsLayoutsDirectory()
            if (!pub.exists()) pub.mkdirs()
            if (pub.exists() && pub.canWrite()) targets.add(pub)
        } catch (_: Exception) {}

        val appDir = getAppSpecificLayoutsDirectory()
        if (!appDir.exists()) appDir.mkdirs()
        if (appDir.exists()) targets.add(appDir)

        try {
            val assetFiles = context.assets.list(ASSETS_LAYOUT_DIR) ?: return
            for (dir in targets) {
                for (filename in assetFiles) {
                    if (filename.endsWith(".json")) {
                        val destFile = File(dir, filename)
                        if (force || !destFile.exists()) {
                            try {
                                context.assets.open("$ASSETS_LAYOUT_DIR/$filename").use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Log.i(TAG, "Exported default layout: $filename to ${destFile.absolutePath}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error exporting $filename to ${destFile.absolutePath}: ${e.message}")
                            }
                        }
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

        // 1. Try loading from external storage (public Documents, shared KeyFold, app-specific)
        val candidates = listOf(
            File(getPublicDocumentsLayoutsDirectory(), "$id.json"),
            File(File(Environment.getExternalStorageDirectory(), "KeyFold/layouts"), "$id.json"),
            File(getAppSpecificLayoutsDirectory(), "$id.json")
        )

        for (candidate in candidates) {
            if (candidate.exists()) {
                try {
                    val content = candidate.readText()
                    val layout = json.decodeFromString<KeyboardLayout>(content)
                    layoutCache[id] = layout
                    Log.d(TAG, "Loaded layout '$id' from: ${candidate.absolutePath}")
                    return layout
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse layout file ${candidate.absolutePath}, trying next: ${e.message}")
                }
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

    fun getLayoutRaw(id: String): String {
        val candidates = listOf(
            File(getPublicDocumentsLayoutsDirectory(), "$id.json"),
            File(File(Environment.getExternalStorageDirectory(), "KeyFold/layouts"), "$id.json"),
            File(getAppSpecificLayoutsDirectory(), "$id.json")
        )
        for (candidate in candidates) {
            if (candidate.exists()) {
                try {
                    return candidate.readText()
                } catch (_: Exception) {}
            }
        }

        return try {
            context.assets.open("$ASSETS_LAYOUT_DIR/$id.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    fun saveLayoutRaw(id: String, jsonContent: String): Pair<Boolean, String?> {
        return try {
            val parsed = json.decodeFromString<KeyboardLayout>(jsonContent)
            val dir = getLayoutsDirectory()
            val file = File(dir, "$id.json")
            file.writeText(jsonContent)
            layoutCache[id] = parsed

            val appDir = getAppSpecificLayoutsDirectory()
            if (appDir.absolutePath != dir.absolutePath) {
                try {
                    File(appDir, "$id.json").writeText(jsonContent)
                } catch (_: Exception) {}
            }
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, e.message)
        }
    }

    fun resetLayoutToDefault(id: String): Boolean {
        return try {
            val assetContent = context.assets.open("$ASSETS_LAYOUT_DIR/$id.json").bufferedReader().use { it.readText() }
            val (success, _) = saveLayoutRaw(id, assetContent)
            success
        } catch (e: Exception) {
            false
        }
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

            val appDir = getAppSpecificLayoutsDirectory()
            if (appDir.absolutePath != dir.absolutePath) {
                try {
                    File(appDir, "$layoutId.json").writeText(jsonString)
                } catch (_: Exception) {}
            }

            Log.i(TAG, "Saved updated height config to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save layout height: ${e.message}", e)
            false
        }
    }
}
