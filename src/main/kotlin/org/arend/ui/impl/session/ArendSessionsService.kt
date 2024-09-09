package org.arend.ui.impl.session

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class ArendSessionsService(private val project: Project) {
    private var myToolWindow: ToolWindow? = null

    private val toolWindow: ToolWindow
        get() = myToolWindow ?: synchronized(this) {
            myToolWindow?.let { return it }
            val result = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask("Arend UI", ToolWindowAnchor.RIGHT, canWorkInDumbMode = false))
            myToolWindow = result
            return result
        }

    fun addTab(component: JComponent, focused: JComponent?, okButton: JButton, name: String?, callback: Consumer<Boolean>?): Content {
        val toolWindow = toolWindow
        toolWindow.show()

        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(component, name, false)
        focused?.let {
            content.preferredFocusableComponent = it
        }
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content === content) {
                    callback?.accept(false)
                    contentManager.removeContentManagerListener(this)
                }
            }

            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.content === content) {
                    SwingUtilities.getRootPane(contentManager.component)?.defaultButton = okButton
                }
            }
        })
        contentManager.addContent(content)
        return content
    }

    fun removeTab(content: Content) {
        val toolWindow = toolWindow
        val contentManager = toolWindow.contentManager
        contentManager.removeContent(content, true)
        if (contentManager.contentCount == 0) {
            toolWindow.hide()
        }
    }
}