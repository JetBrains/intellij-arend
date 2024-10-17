package org.arend.psi.arc

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.indexing.FileBasedIndex
import org.arend.psi.ArendFile
import org.arend.util.ArendBundle
import java.util.function.Function
import javax.swing.JComponent

class ArcNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val textEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile) as TextEditor?
        val editor = textEditor?.editor
        if (virtualFile.fileType !is ArcFileType) {
            return null
        } else if (editor?.document?.text?.isNotEmpty() == true) {
            return null
        }
        return Function<FileEditor, EditorNotificationPanel?> {
            createPanel(project, virtualFile, it)
        }
    }

    private fun createPanel(project: Project, virtualFile: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel {
        val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
        panel.text = ArendBundle.message("arend.arc.update")
        panel.createActionLabel(ArendBundle.message("arend.updateYamlConfiguration")) {
            FileBasedIndex.getInstance().invalidateCaches()
            FileDocumentManager.getInstance().getCachedDocument(virtualFile)?.let {
                val manager = FileDocumentManager.getInstance()
                manager.reloadFromDisk(it)
                manager.reloadFiles(virtualFile)

                val psiManager = PsiManager.getInstance(project)
                val file = psiManager.findFile(virtualFile) as ArendFile? ?: return@createActionLabel
                file.decLastModification()
                DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap.dispose()
            }
        }
        return panel
    }
}