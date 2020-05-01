package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.ext.core.ops.NormalizationMode
import org.arend.psi.ArendExpr
import org.arend.psi.ArendFile
import org.arend.psi.ArendLiteral
import org.arend.refactoring.normalizeExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.replaceExprSmart
import org.arend.refactoring.tryCorrespondedSubExpr

class ReplaceWithNormalFormIntention : SelectionIntention<ArendExpr>(ArendExpr::class.java, "Replace with Weak Head Normal Form") {
    override fun isAvailable(project: Project, editor: Editor, file: ArendFile, element: ArendExpr) =
        (element as? ArendLiteral)?.goal == null

    override fun invoke(project: Project, editor: Editor, file: ArendFile, element: ArendExpr, selected: TextRange) {
        val (subCore, subConcrete, _) = tryCorrespondedSubExpr(selected, file, project, editor) ?: return
        normalizeExpr(project, subCore, NormalizationMode.WHNF, element) {
            val text = it.toString()
            WriteCommandAction.writeCommandAction(project).run<Exception> {
                val range = rangeOfConcrete(subConcrete)
                val length = replaceExprSmart(editor.document, element, subConcrete, range, null, it, text).length
                val start = range.startOffset
                editor.selectionModel.setSelection(start, start + length)
            }
        }
    }
}