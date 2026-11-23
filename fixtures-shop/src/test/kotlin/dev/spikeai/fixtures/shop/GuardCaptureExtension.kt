package dev.spikeai.fixtures.shop

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
import dev.spikeai.guard.CommerceGuard
import dev.spikeai.guard.Screen
import dev.spikeai.guard.UiNode
import java.io.File

/**
 * After the first frame is drawn, turns the merged Compose semantics tree into a guard [Screen],
 * runs the commerce rules, and writes per screen:
 *  - <name>.txt           compact text (what an LLM would get — ~tens of tokens)
 *  - <name>.findings.tsv  screen \t rule \t message
 */
class GuardCaptureExtension(private val screenName: String, private val outDir: File) : RenderExtension {

    override fun renderView(contentView: View): View {
        val wrapper = object : FrameLayout(contentView.context) {
            private var done = false
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (!done) {
                    done = true
                    capture(this)
                }
            }
        }
        wrapper.addView(
            contentView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        return wrapper
    }

    private fun capture(root: View) {
        val composeRoot = findComposeRoot(root) ?: error("No Compose root in $screenName")
        val nodes = mutableListOf<UiNode>()
        collect(composeRoot.semanticsOwner.rootSemanticsNode, nodes)
        val screen = Screen(screenName, nodes)
        val findings = CommerceGuard.check(screen)

        outDir.mkdirs()
        File(outDir, "$screenName.txt").writeText(screen.compactText() + "\n")
        File(outDir, "$screenName.nodes.tsv").writeText(
            nodes.joinToString("") { "${it.id}\t${it.role}\t${it.label ?: ""}\t${it.left}\t${it.top}\t${it.right}\t${it.bottom}\n" },
        )
        File(outDir, "$screenName.findings.tsv").writeText(
            findings.joinToString("") { "${it.screen}\t${it.ruleId}\t${it.message}\n" },
        )
    }

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeRoot(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun collect(node: SemanticsNode, out: MutableList<UiNode>) {
        val c = node.config
        val text = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val clickable = c.getOrNull(SemanticsActions.OnClick) != null
        val heading = c.getOrNull(SemanticsProperties.Heading) != null
        val edit = c.getOrNull(SemanticsProperties.EditableText)?.text?.takeIf { it.isNotBlank() }
        val label = desc ?: text ?: edit
        if (label != null || clickable) {
            val b = node.boundsInRoot
            out += UiNode(
                id = out.size + 1,
                role = when { clickable -> "button"; heading -> "heading"; else -> "text" },
                label = label,
                left = b.left.toInt(), top = b.top.toInt(), right = b.right.toInt(), bottom = b.bottom.toInt(),
            )
        }
        node.children.forEach { collect(it, out) }
    }
}
