package org.arend.toolWindow.errors.tree

import com.intellij.ui.AutoScrollToSourceHandler
import org.arend.editor.ArendOptions
import java.awt.Component


class ArendErrorTreeAutoScrollToSource(private val tree: ArendErrorTree) : AutoScrollToSourceHandler() {
    init {
        install(tree)
    }

    override fun isAutoScrollMode() = ArendOptions.instance.autoScrollToSource

    override fun setAutoScrollMode(state: Boolean) {
        ArendOptions.instance.autoScrollToSource = state
    }

    override fun scrollToSource(component: Component?) {
        tree.navigate(false)
    }
}