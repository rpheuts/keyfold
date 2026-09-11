package com.keyfold.terminal.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyType {
    character,
    action,
    modifier,
    layer
}

@Serializable
data class HeightConfig(
    val mode: String = "percentage", // "fold_hinge", "percentage", "dp"
    val fallbackPercent: Int = 45,
    val fixedDp: Int = 300
)

@Serializable
data class KeyDefinition(
    val label: String,
    val shift: String? = null,
    val output: String? = null,
    val shiftOutput: String? = null,
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
