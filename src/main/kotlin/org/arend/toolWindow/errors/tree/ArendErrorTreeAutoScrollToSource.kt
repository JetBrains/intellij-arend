package org.arend.toolWindow.errors.tree

import com.intellij.openapi.components.service
import com.intellij.ui.AutoScrollToSourceHandler
import org.arend.editor.ArendOptions
import java.awt.Component


class ArendErrorTreeAutoScrollToSource(private val tree: ArendErrorTree) : AutoScrollToSourceHandler() {
    init {
        install(tree)
    }

    override fun isAutoScrollMode() = service<ArendOptions>().autoScrollToSource

    override fun setAutoScrollMode(state: Boolean) {
        service<ArendOptions>().autoScrollToSource = state
    }

    override fun scrollToSource(component: Component?) {
        tree.navigate(false)
    }
}