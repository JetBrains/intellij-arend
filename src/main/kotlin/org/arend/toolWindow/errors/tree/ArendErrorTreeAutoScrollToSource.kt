package org.arend.toolWindow.errors.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.AutoScrollToSourceHandler
import org.arend.settings.ArendProjectSettings
import java.awt.Component


class ArendErrorTreeAutoScrollToSource(private val project: Project, private val tree: ArendErrorTree) : AutoScrollToSourceHandler() {
    init {
        install(tree)
    }

    override fun isAutoScrollMode() = project.service<ArendProjectSettings>().data.autoScrollToSource

    override fun setAutoScrollMode(state: Boolean) {
        project.service<ArendProjectSettings>().data.autoScrollToSource = state
    }

    override fun scrollToSource(component: Component) {
        tree.navigate(false)
    }
}