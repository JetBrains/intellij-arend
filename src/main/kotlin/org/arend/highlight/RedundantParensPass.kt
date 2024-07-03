package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInspection.HintAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.inspection.*
import org.arend.inspection.isBinOpApp
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.psi.parentArgumentAppExpr
import org.arend.refactoring.psiOfConcrete
import org.arend.refactoring.unwrapParens
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete

class RedundantParensPass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor):
    BasePass(file, editor, "Arend redundant parens annotator", TextRange(0, editor.document.textLength), highlightInfoProcessor){
    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        super.applyInformationWithProgress()
        val tuples = file.descendantsOfType<ArendTuple>().toList()
        for (element in tuples) {
            progress.checkCanceled()
            val expression = unwrapParens(element) ?: continue
            if (isApplicationUsedAsBinOpArgument(element, expression)) {
                val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.WEAK_WARNING)
                    .range(element.textRange)
                    .severity(HighlightSeverity.WEAK_WARNING)
                    .description(ArendBundle.message("arend.inspection.redundant.parentheses.message"))
                registerFix(builder, UnwrapParensHintAction(element))
                addHighlightInfo(builder)
            }
        }
    }

    fun isApplicationUsedAsBinOpArgument(tuple: ArendTuple, tupleExpression: ArendExpr): Boolean {
        val parentAtomFieldsAcc = getParentAtomFieldsAcc(tuple) ?: return false
        val parentAppExprPsi = parentArgumentAppExpr(parentAtomFieldsAcc) ?: return false
        val parentAppExpr = appExprToConcrete(parentAppExprPsi, true) ?: return false
        var result = false
        parentAppExpr.accept(object : ArendInspectionConcreteVisitor() {
            override fun visitHole(expr: Concrete.HoleExpression?, params: Void?): Void? {
                super.visitHole(expr, params)
                if (expr != null && psiOfConcrete(expr) == tuple) {
                    result = isApplicationUsedAsBinOpArgument(parent, tupleExpression)
                }
                return null
            }
        }, null)
        return result
    }

    fun isApplicationUsedAsBinOpArgument(tupleParent: ConcreteSourceNode?, tupleExpression: ArendExpr): Boolean {
       if (tupleParent is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(tupleParent)) {
            val childAppExpr =
                if (tupleExpression is ArendNewExpr && isAtomic(tupleExpression)) tupleExpression.argumentAppExpr
                else null
            return childAppExpr != null && hasNoLevelArguments(childAppExpr) && !isBinOpApp(childAppExpr)
        }
        return false
    }

    companion object {

        class UnwrapParensHintAction(val tuple: ArendTuple): HintAction {
            override fun startInWriteAction(): Boolean = true

            override fun getText(): String = ArendBundle.message("arend.inspection.redundant.parentheses.name")

            override fun getFamilyName(): String = ""

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                doUnwrapParens(tuple)
            }

            override fun showHint(editor: Editor): Boolean = false

        }
    }
}
