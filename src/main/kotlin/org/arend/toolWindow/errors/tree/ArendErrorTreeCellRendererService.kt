package org.arend.toolWindow.errors.tree

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.arend.psi.ArendFile
import org.arend.toolWindow.errors.ArendMessagesService

@Service(Service.Level.PROJECT)
class ArendErrorTreeCellRendererService(private val project: Project) {
    private val fromFileToFullName = ContainerUtil.createConcurrentWeakMap<Pair<ArendFile, String>, String>()

    fun renderCellArendFile(arendFile: ArendFile, cellRenderer: ArendErrorTreeCellRenderer, action: () -> Unit) {
        val key = Pair(arendFile, arendFile.name)
        if (!fromFileToFullName.containsKey(key)) {
            ApplicationManager.getApplication().executeOnPooledThread {
                action()
                fromFileToFullName[key] = arendFile.fullName
                cellRenderer.text = fromFileToFullName[key]
                project.service<ArendMessagesService>().view?.update()
            }
        } else {
            cellRenderer.text = fromFileToFullName[key]
        }
    }
}
