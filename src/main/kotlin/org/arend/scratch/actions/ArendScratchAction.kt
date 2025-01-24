package org.arend.scratch.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.TextEditor
import org.arend.scratch.ArendScratchFileEditorWithPreview
import org.arend.scratch.findArendScratchFileEditorWithPreview
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import javax.swing.Icon

abstract class ArendScratchAction(@Nls message: String, icon: Icon) : AnAction(message, message, icon) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = e.currentScratchFile != null
    }

    protected val AnActionEvent.currentScratchFile: ScratchFile?
        get() = currentScratchEditor?.scratchFile

    protected val AnActionEvent.currentScratchEditor: ArendScratchFileEditorWithPreview?
        get() {
            val textEditor = getData(PlatformCoreDataKeys.FILE_EDITOR) as? TextEditor
            return textEditor?.findArendScratchFileEditorWithPreview()
        }
}
