package com.keyfold.terminal

import com.keyfold.terminal.layout.LayoutEditorMutator
import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyType
import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.model.KeyboardRow
import org.junit.Assert.*
import org.junit.Test

class LayoutEditorMutatorTest {

    private fun sampleLayout(): KeyboardLayout {
        return KeyboardLayout(
            id = "test_layout",
            name = "Test",
            rows = listOf(
                KeyboardRow(
                    keys = listOf(
                        KeyDefinition(label = "a", width = 1.0f),
                        KeyDefinition(label = "b", width = 1.0f),
                        KeyDefinition(label = "c", width = 1.0f)
                    )
                ),
                KeyboardRow(
                    keys = listOf(
                        KeyDefinition(label = "SPACE", code = "SPACE", type = KeyType.character, width = 3.0f),
                        KeyDefinition(label = "⏎", code = "ENTER", type = KeyType.action, width = 1.5f)
                    )
                )
            )
        )
    }

    @Test
    fun updateKeyReplacesLabelAndWeight() {
        val updated = LayoutEditorMutator.updateKey(
            sampleLayout(),
            rowIndex = 0,
            keyIndex = 1,
            newKey = KeyDefinition(label = "B+", width = 2.2f)
        )
        assertEquals("B+", updated.rows[0].keys[1].label)
        assertEquals(2.2f, updated.rows[0].keys[1].width, 0.001f)
    }

    @Test
    fun addAndDeleteKey() {
        val added = LayoutEditorMutator.addKey(
            sampleLayout(),
            rowIndex = 0,
            newKey = KeyDefinition(label = "d", width = 1.0f)
        )
        assertEquals(4, added.rows[0].keys.size)
        assertEquals("d", added.rows[0].keys[3].label)

        val deleted = LayoutEditorMutator.deleteKey(added, rowIndex = 0, keyIndex = 1)
        assertEquals(listOf("a", "c", "d"), deleted.rows[0].keys.map { it.label })
    }

    @Test
    fun moveKeyWithinSameRowForwardAndBackward() {
        val movedForward = LayoutEditorMutator.moveKey(
            sampleLayout(),
            fromRow = 0,
            fromIndex = 0,
            toRow = 0,
            toIndex = 3
        )
        assertEquals(listOf("b", "c", "a"), movedForward.rows[0].keys.map { it.label })

        val movedBackward = LayoutEditorMutator.moveKey(
            movedForward,
            fromRow = 0,
            fromIndex = 2,
            toRow = 0,
            toIndex = 0
        )
        assertEquals(listOf("a", "b", "c"), movedBackward.rows[0].keys.map { it.label })
    }

    @Test
    fun moveKeyAcrossRows() {
        val moved = LayoutEditorMutator.moveKey(
            sampleLayout(),
            fromRow = 0,
            fromIndex = 1,
            toRow = 1,
            toIndex = 1
        )
        assertEquals(listOf("a", "c"), moved.rows[0].keys.map { it.label })
        assertEquals(listOf("SPACE", "b", "⏎"), moved.rows[1].keys.map { it.label })
    }

    @Test
    fun addAndRemoveRow() {
        val withRow = LayoutEditorMutator.addRow(sampleLayout())
        assertEquals(3, withRow.rows.size)
        val removed = LayoutEditorMutator.removeRow(withRow, 2)
        assertEquals(2, removed.rows.size)
    }
}
