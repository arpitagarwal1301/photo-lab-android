package dev.spikeai.guard

/** One element of a rendered screen, taken from the Compose semantics tree. */
data class UiNode(
    val id: Int,
    val role: String, // "button" | "heading" | "text"
    val label: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Int get() = (top + bottom) / 2
    val isClickable: Boolean get() = role == "button"

    /** Compact line an agent/LLM would see for this node. */
    fun compactLine(): String = "[$id] $role " + (label?.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "(no label!)")
}

data class Screen(val name: String, val nodes: List<UiNode>) {
    fun compactText(): String = nodes.joinToString("\n") { it.compactLine() }
}

data class Finding(
    val screen: String,
    val ruleId: String,
    val message: String,
    val nodeIds: List<Int> = emptyList(),
)
