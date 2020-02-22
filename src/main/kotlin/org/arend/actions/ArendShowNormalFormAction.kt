package org.arend.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.ext.core.ops.NormalizationMode
import org.arend.refactoring.SubExprError
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.prettyPopupExpr
import org.arend.refactoring.rangeOfConcrete

class ArendShowNormalFormAction : ArendPopupAction() {
    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
            val visited = correspondedSubExpr(editor, file, project)
            val subCore = visited.proj1
            val textRange = rangeOfConcrete(visited.proj2)
            editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
            displayHint { showInformationHint(editor, prettyPopupExpr(project, subCore.normalize(NormalizationMode.NF))) }
        } catch (t: SubExprError) {
            displayHint { showErrorHint(editor, "Failed to obtain type because ${t.message}") }
        }
    }
}