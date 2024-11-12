package org.arend.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile
import org.arend.psi.ancestors
import org.arend.util.checkArcFile

abstract class SelectionIntention<T : PsiElement>(private val elementType: Class<T>, text: String) : BaseIntentionAction() {
    init {
        this.text = text
    }

    open fun allowEmptySelection() = false

    open fun isAvailable(project: Project, editor: Editor, file: ArendFile, element: T): Boolean {
        return !checkArcFile(file.virtualFile)
    }

    abstract fun invoke(project: Project, editor: Editor, file: ArendFile, element: T, selected: TextRange)

    private fun selectedExpr(editor: Editor, file: ArendFile, selected: TextRange): T? {
        val element = file.findElementAt(editor.caretModel.offset)
            ?: return null
        return element.ancestors.filterIsInstance(elementType)
            .firstOrNull { selected in it.textRange }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        if (file !is ArendFile || !BaseArendIntention.canModify(file))
            return false
        val selected = EditorUtil.getSelectionInAnyMode(editor)
        if (!allowEmptySelection() && selected.isEmpty)
            return false
        val element = selectedExpr(editor, file, selected) ?: return false
        return isAvailable(project, editor, file, element)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        if (file !is ArendFile) return
        val selected = EditorUtil.getSelectionInAnyMode(editor)
        if (!allowEmptySelection() && selected.isEmpty) return
        val expr = selectedExpr(editor, file, selected) ?: return
        invoke(project, editor, file, expr, selected)
    }

    override fun getFamilyName() = text

    override fun startInWriteAction() = true
}