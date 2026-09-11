package com.keyfold.terminal.posture

enum class DevicePosture(val description: String, val defaultLayoutId: String) {
    UNFOLDED_LANDSCAPE_HALF(
        "Tabletop / PDA Flex Mode (Unfolded Landscape Bent)",
        "unfolded_landscape_pda"
    ),
    UNFOLDED_LANDSCAPE_FLAT(
        "Tablet Landscape (Unfolded Flat)",
        "unfolded_landscape_flat"
    ),
    UNFOLDED_PORTRAIT_HALF(
        "Book Mode (Unfolded Portrait Bent)",
        "unfolded_portrait"
    ),
    UNFOLDED_PORTRAIT_FLAT(
        "Tablet Portrait (Unfolded Flat)",
        "unfolded_portrait"
    ),
    FOLDED_PORTRAIT(
        "Cover Screen Portrait",
        "folded_portrait"
    ),
    FOLDED_LANDSCAPE(
        "Cover Screen Landscape",
        "folded_landscape"
    )
}
