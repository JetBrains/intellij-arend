package org.arend.actions

import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ui.accessibility.ScreenReader
import org.arend.psi.ArendFile
import org.arend.refactoring.collectArendExprs
import org.arend.refactoring.selectedExpr
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent


/**
 * @see [com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction]
 */
abstract class ArendPopupAction : BaseCodeInsightAction() {
    companion object {
        @Nls const val NF = "(Normalized)"
    }

    /**
     * @see [com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction.myRequestFocus]
     */
    protected var requestFocus = false
        private set

    init {
        isEnabledInModalContext = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        // The tooltip gets the focus if using a screen reader and invocation through a keyboard shortcut.
        requestFocus = ScreenReader.isActive() && e.inputEvent is KeyEvent
    }

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is ArendFile) return false
        val range = EditorUtil.getSelectionInAnyMode(editor)
        val sExpr = selectedExpr(file, range)
        return sExpr != null && collectArendExprs(sExpr.parent, range) != null
    }
}