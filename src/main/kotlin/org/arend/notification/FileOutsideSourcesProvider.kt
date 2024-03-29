package org.arend.notification

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.arend.psi.ArendFile
import org.arend.util.ArendBundle
import java.util.function.Function
import javax.swing.JComponent

class FileOutsideSourcesProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val arendFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (arendFile !is ArendFile || ProjectFileIndex.getInstance(project).isInSource(virtualFile)) {
            return null
        }

        return Function<FileEditor, JComponent> { fileEditor: FileEditor? ->
            fileEditor?.let { createPanel(fileEditor) }
        }
    }

    private fun createPanel(editor: FileEditor): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        panel.text = ArendBundle.message("arend.message.fileOutsideSources")
        panel.createActionLabel(ArendBundle.message("arend.yaml.openDocumentation")) {
            BrowserUtil.browse("https://arend-lang.github.io/documentation/libraries")
        }
        return panel
    }
}
