package org.arend.ui.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.ext.ui.ArendSession
import org.arend.ui.impl.session.ArendEditorSession

class ArendEditorUI(project: Project, private val editor: Editor) : ArendGeneralUI(project) {
    override fun newSession(): ArendSession = ArendEditorSession(project, editor)

    override fun showMessage(title: String?, message: String) {
        val editor = this.editor
        if (title == null && !editor.isDisposed) {
            invokeLater {
                HintManager.getInstance().showInformationHint(editor, message)
            }
        } else {
            super.showMessage(title, message)
        }
    }

    override fun showErrorMessage(title: String?, message: String) {
        val editor = this.editor
        if (title == null && !editor.isDisposed) {
            invokeLater {
                HintManager.getInstance().showErrorHint(editor, message)
            }
        } else {
            super.showErrorMessage(title, message)
        }
    }
}