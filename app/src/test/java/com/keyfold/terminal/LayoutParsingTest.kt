package com.keyfold.terminal

import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.posture.DevicePosture
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LayoutParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testAllBundledLayoutsParseSuccessfully() {
        val assetDir = File("src/main/assets/layouts")
        val jsonFiles = assetDir.listFiles { f -> f.extension == "json" }
        assertNotNull("Assets layout directory should exist", jsonFiles)
        assertTrue("Should have at least 5 layout files", jsonFiles!!.size >= 5)

        for (file in jsonFiles) {
            val content = file.readText()
            val layout = json.decodeFromString<KeyboardLayout>(content)
            assertNotNull("Layout in ${file.name} should parse", layout)
            assertFalse("Layout ID should not be empty in ${file.name}", layout.id.isEmpty())
            assertTrue("Layout should have rows in ${file.name}", layout.rows.isNotEmpty())

            for ((rowIndex, row) in layout.rows.withIndex()) {
                assertTrue("Row $rowIndex in ${file.name} should have keys", row.keys.isNotEmpty())
                for (key in row.keys) {
                    if (key.type != com.keyfold.terminal.model.KeyType.spacer) {
                        assertFalse("Key label in ${file.name} should not be empty", key.label.isEmpty())
                    }
                    assertTrue("Key width should be > 0 in ${file.name}", key.width > 0f)
                }
            }
        }
    }

    @Test
    fun testPosturesMapToValidLayoutFiles() {
        val assetDir = File("src/main/assets/layouts")
        for (posture in DevicePosture.values()) {
            val layoutFile = File(assetDir, "${posture.defaultLayoutId}.json")
            assertTrue(
                "Layout file for posture $posture should exist: ${layoutFile.name}",
                layoutFile.exists()
            )
        }
    }
}
