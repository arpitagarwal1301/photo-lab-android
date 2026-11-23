package dev.spikeai.spike.paparazzi

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.cash.paparazzi.RenderExtension
import java.io.File
import java.security.MessageDigest

/**
 * Spike D: a Paparazzi RenderExtension that, after the first frame is drawn, walks the
 * merged Compose semantics tree and writes:
 *  - <out>.json : every labelled or tappable node with role, label, bounds
 *  - <out>.txt  : the compact "Tier 2" text an agent would send to the model
 * and prints size + a screen fingerprint (for the App Map).
 */
class SemanticsDumpExtension(private val outBase: File) : RenderExtension {

    override fun renderView(contentView: View): View {
        val wrapper = object : FrameLayout(contentView.context) {
            private var dumped = false
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (!dumped) {
                    dumped = true
                    dump(this)
                }
            }
        }
        wrapper.addView(
            contentView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        return wrapper
    }

    private fun dump(root: View) {
        val composeRoot = findComposeRoot(root)
        if (composeRoot == null) {
            println("[spike-d] no Compose root found")
            return
        }
        val nodes = mutableListOf<UiNode>()
        collect(composeRoot.semanticsOwner.rootSemanticsNode, nodes)

        val compact = nodes.mapIndexed { i, n -> n.compactLine(i + 1) }.joinToString("\n")
        val fingerprint = sha256(nodes.joinToString("|") { "${it.role}:${it.label ?: ""}" }).take(12)

        outBase.parentFile?.mkdirs()
        File(outBase.path + ".json").writeText(toJson(nodes, fingerprint))
        File(outBase.path + ".txt").writeText(compact + "\n")
        println(
            "[spike-d] nodes=${nodes.size} compact_chars=${compact.length} " +
                "approx_tokens=${compact.length / 4} fingerprint=$fingerprint\n$compact",
        )
    }

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findComposeRoot(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun collect(node: SemanticsNode, out: MutableList<UiNode>) {
        val config = node.config
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val clickable = config.getOrNull(SemanticsActions.OnClick) != null
        val heading = config.getOrNull(SemanticsProperties.Heading) != null
        val label = description ?: text
        if (label != null || clickable) {
            val b = node.boundsInRoot
            out += UiNode(
                role = when {
                    clickable -> "button"
                    heading -> "heading"
                    else -> "text"
                },
                label = label,
                left = b.left.toInt(), top = b.top.toInt(), right = b.right.toInt(), bottom = b.bottom.toInt(),
            )
        }
        node.children.forEach { collect(it, out) }
    }

    private data class UiNode(
        val role: String,
        val label: String?,
        val left: Int, val top: Int, val right: Int, val bottom: Int,
    ) {
        fun compactLine(id: Int): String =
            "[$id] $role " + (label?.let { "\"$it\"" } ?: "(no label!)")
    }

    private fun toJson(nodes: List<UiNode>, fingerprint: String): String = buildString {
        append("{\n  \"fingerprint\": \"").append(fingerprint).append("\",\n  \"nodes\": [\n")
        nodes.forEachIndexed { i, n ->
            append("    {\"id\": ").append(i + 1)
            append(", \"role\": \"").append(n.role).append('"')
            append(", \"label\": ").append(n.label?.let { "\"" + esc(it) + "\"" } ?: "null")
            append(", \"bounds\": [${n.left}, ${n.top}, ${n.right}, ${n.bottom}]}")
            append(if (i < nodes.lastIndex) ",\n" else "\n")
        }
        append("  ]\n}\n")
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
