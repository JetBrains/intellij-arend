package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.refactoring.normalizeExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete

class ReplaceWithNormalFormIntention : ReplaceExpressionIntention("Replace with Weak Head Normal Form") {
    override fun doApply(project: Project, editor: Editor, subCore: Expression, subConcrete: Concrete.Expression, element: PsiElement?) {
        normalizeExpr(project, subCore, NormalizationMode.WHNF, element) {
            WriteCommandAction.runWriteCommandAction(project) {
                val range = rangeOfConcrete(subConcrete)
                val length = replaceExpr(editor.document, range, it)
                val start = range.startOffset
                editor.selectionModel.setSelection(start, start + length)
            }
        }
    }
}