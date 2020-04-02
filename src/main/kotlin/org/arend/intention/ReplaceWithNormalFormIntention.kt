package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.refactoring.normalizeExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete

class ReplaceWithNormalFormIntention : ReplaceExpressionIntention("Replace with Weak Head Normal Form") {
    override fun doApply(project: Project, editor: Editor, range: TextRange, subCore: Expression, subConcrete: Concrete.Expression) {
        normalizeExpr(project, subCore, NormalizationMode.WHNF) {
            WriteCommandAction.runWriteCommandAction(project) {
                val length = replaceExpr(editor.document, rangeOfConcrete(subConcrete), it)
                val startOffset = range.startOffset
                editor.selectionModel
                    .setSelection(startOffset, startOffset + length)
            }
        }
    }
}