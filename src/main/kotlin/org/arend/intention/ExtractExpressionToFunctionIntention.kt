package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import org.arend.ext.core.ops.NormalizationMode
import org.arend.psi.ext.ArendExpr
import org.arend.psi.getSelectionWithoutErrors
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.util.ArendBundle

open class ExtractExpressionToFunctionIntention : AbstractGenerateFunctionIntention() {

    companion object {
        internal fun doExtractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult? {
            val range = editor.getSelectionWithoutErrors() ?: return null

            val subexprResult = tryCorrespondedSubExpr(range, file, project, editor) ?: return null
            val enclosingRange = rangeOfConcrete(subexprResult.subConcrete)
            val enclosingPsi =
                    subexprResult
                            .subPsi
                            .parents(true)
                            .filterIsInstance<ArendExpr>()
                            .lastOrNull { enclosingRange.contains(it.textRange) }
                            ?: subexprResult.subPsi
            return SelectionResult(
                subexprResult.subCore.type?.normalize(NormalizationMode.RNF),
                enclosingPsi,
                enclosingRange,
                subexprResult.subConcrete,
                null,
                editor.document.getText(range),
                subexprResult.subCore)
        }
    }

    override fun getText(): String = ArendBundle.message("arend.generate.function.from.expression")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!canModify(file) || !BaseArendIntention.canModify(file)) {
            return false
        }
        val selection = editor.getSelectionWithoutErrors() ?: return false
        return !selection.isEmpty
    }

    override fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult? = doExtractSelectionData(file, editor, project)
}