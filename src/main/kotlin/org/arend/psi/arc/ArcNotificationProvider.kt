package org.arend.psi.arc

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.indexing.FileBasedIndex
import org.arend.util.ArendBundle
import java.util.function.Function
import javax.swing.JComponent

class ArcNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (virtualFile.fileType !is ArcFileType) {
            return null
        }
        return Function<FileEditor, EditorNotificationPanel?> {
            createPanel(project, virtualFile, it)
        }
    }

    private fun createPanel(project: Project, virtualFile: VirtualFile, editor: FileEditor): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        panel.text = ArendBundle.message("arend.arc.update")
        panel.createActionLabel(ArendBundle.message("arend.updateYamlConfiguration")) {
            val editorManager = FileEditorManager.getInstance(project)
            editorManager.closeFile(virtualFile)

            FileBasedIndex.getInstance().invalidateCaches()
            FileDocumentManager.getInstance()
                .reloadFromDisk(FileDocumentManager.getInstance().getCachedDocument(virtualFile)!!)

            editorManager.openFile(virtualFile, true)
        }
        return panel
    }
}