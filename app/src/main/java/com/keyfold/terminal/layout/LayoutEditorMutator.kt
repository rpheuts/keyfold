package com.keyfold.terminal.layout

import com.keyfold.terminal.model.KeyDefinition
import com.keyfold.terminal.model.KeyboardLayout
import com.keyfold.terminal.model.KeyboardRow

/**
 * Pure immutable transformations for editing a [KeyboardLayout] in the visual layout editor.
 */
object LayoutEditorMutator {

    fun updateKey(
        layout: KeyboardLayout,
        rowIndex: Int,
        keyIndex: Int,
        newKey: KeyDefinition
    ): KeyboardLayout {
        if (rowIndex !in layout.rows.indices) return layout
        val row = layout.rows[rowIndex]
        if (keyIndex !in row.keys.indices) return layout

        val updatedKeys = row.keys.toMutableList().apply { set(keyIndex, newKey) }
        val updatedRows = layout.rows.toMutableList().apply { set(rowIndex, row.copy(keys = updatedKeys)) }
        return layout.copy(rows = updatedRows)
    }

    fun deleteKey(
        layout: KeyboardLayout,
        rowIndex: Int,
        keyIndex: Int
    ): KeyboardLayout {
        if (rowIndex !in layout.rows.indices) return layout
        val row = layout.rows[rowIndex]
        if (keyIndex !in row.keys.indices) return layout

        val updatedKeys = row.keys.toMutableList().apply { removeAt(keyIndex) }
        val updatedRows = layout.rows.toMutableList()
        if (updatedKeys.isEmpty() && updatedRows.size > 1) {
            updatedRows.removeAt(rowIndex)
        } else {
            updatedRows[rowIndex] = row.copy(keys = updatedKeys)
        }
        return layout.copy(rows = updatedRows)
    }

    fun addKey(
        layout: KeyboardLayout,
        rowIndex: Int,
        newKey: KeyDefinition,
        insertIndex: Int? = null
    ): KeyboardLayout {
        if (layout.rows.isEmpty()) {
            return layout.copy(rows = listOf(KeyboardRow(keys = listOf(newKey))))
        }
        val safeRowIndex = rowIndex.coerceIn(0, layout.rows.lastIndex)
        val row = layout.rows[safeRowIndex]
        val updatedKeys = row.keys.toMutableList()
        val targetIndex = (insertIndex ?: updatedKeys.size).coerceIn(0, updatedKeys.size)
        updatedKeys.add(targetIndex, newKey)

        val updatedRows = layout.rows.toMutableList().apply {
            set(safeRowIndex, row.copy(keys = updatedKeys))
        }
        return layout.copy(rows = updatedRows)
    }

    fun moveKey(
        layout: KeyboardLayout,
        fromRow: Int,
        fromIndex: Int,
        toRow: Int,
        toIndex: Int
    ): KeyboardLayout {
        if (fromRow !in layout.rows.indices || toRow !in layout.rows.indices) return layout
        val sourceRow = layout.rows[fromRow]
        if (fromIndex !in sourceRow.keys.indices) return layout

        val movedKey = sourceRow.keys[fromIndex]
        val updatedRows = layout.rows.toMutableList()

        if (fromRow == toRow) {
            val keys = sourceRow.keys.toMutableList()
            keys.removeAt(fromIndex)
            val adjustedIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
            keys.add(adjustedIndex.coerceIn(0, keys.size), movedKey)
            updatedRows[fromRow] = sourceRow.copy(keys = keys)
            return layout.copy(rows = updatedRows)
        }

        val sourceKeys = sourceRow.keys.toMutableList().apply { removeAt(fromIndex) }
        val targetKeys = updatedRows[toRow].keys.toMutableList()
        targetKeys.add(toIndex.coerceIn(0, targetKeys.size), movedKey)

        updatedRows[fromRow] = sourceRow.copy(keys = sourceKeys)
        updatedRows[toRow] = updatedRows[toRow].copy(keys = targetKeys)

        if (sourceKeys.isEmpty() && updatedRows.size > 1) {
            updatedRows.removeAt(fromRow)
        }
        return layout.copy(rows = updatedRows)
    }

    fun addRow(
        layout: KeyboardLayout,
        initialKey: KeyDefinition = KeyDefinition(label = "?", width = 1.0f)
    ): KeyboardLayout {
        val updatedRows = layout.rows.toMutableList().apply {
            add(KeyboardRow(keys = listOf(initialKey)))
        }
        return layout.copy(rows = updatedRows)
    }

    fun removeRow(layout: KeyboardLayout, rowIndex: Int): KeyboardLayout {
        if (layout.rows.size <= 1 || rowIndex !in layout.rows.indices) return layout
        val updatedRows = layout.rows.toMutableList().apply { removeAt(rowIndex) }
        return layout.copy(rows = updatedRows)
    }
}
