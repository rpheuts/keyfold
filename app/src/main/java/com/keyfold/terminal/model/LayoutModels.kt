package com.keyfold.terminal.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyType {
    character,
    action,
    modifier,
    layer,
    spacer
}

@Serializable
data class HeightConfig(
    val mode: String = "percentage", // "percentage", "fold_hinge", "dp"
    val percent: Int = 45,
    val fallbackPercent: Int = 45,
    val fixedDp: Int = 300,
    val offsetDp: Int = 0
)

@Serializable
data class KeyDefinition(
    val label: String,
    val shift: String? = null,
    val output: String? = null,
    val shiftOutput: String? = null,
    val hint: String? = null,
    val code: String? = null,
    val type: KeyType = KeyType.character,
    val width: Float = 1.0f,
    val sticky: Boolean = false,
    val repeat: Boolean = false
)

@Serializable
data class KeyboardRow(
    val keys: List<KeyDefinition>
)

@Serializable
data class KeyboardLayout(
    val id: String,
    val name: String,
    val height: HeightConfig = HeightConfig(),
    val rows: List<KeyboardRow>
)
