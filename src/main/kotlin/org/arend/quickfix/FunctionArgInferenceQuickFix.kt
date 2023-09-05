package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.codeInsight.ArendParameterInfoHandler.Companion.getAllParametersForReferable
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.concrete.expr.ConcreteExpression
import org.arend.ext.error.GeneralError
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.TGOAL
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.performTextModification
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.inference.FunctionArgInferenceError
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import java.util.HashMap

class FunctionArgInferenceQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: FunctionArgInferenceError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.argument.inference.parameter")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element: PsiElement? = ((error.cause as? Concrete.AppExpression)?.function?.data as? PsiElement ?: (error.cause as? ConcreteExpression)?.data as? PsiElement) ?: return
        while (element != null && element !is ArendAtomFieldsAcc && element !is ArendAtomArgument && element !is ArendArgumentAppExpr) element = element.parent
        if (element !is ArendAtomArgument && element !is ArendAtomFieldsAcc) return

        val rootPsi = cause.element?.ancestor<ArendArgumentAppExpr>() ?: return
        val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
        val concreteExpr = appExprToConcrete(rootPsi, false, errorReporter) as? Concrete.AppExpression ?: return
        val rangeData = HashMap<Concrete.SourceNode, TextRange>(); getBounds(concreteExpr, rootPsi.node.getChildren(null).toList(), rangeData)

        fun findSubExpr(expr: Concrete.Expression): Concrete.Expression? {
            if (expr is Concrete.AppExpression) {
                val functionData = expr.function.data
                if (functionData is PsiElement && functionData.textRange == element.textRange) return expr
                for (arg in expr.arguments) {
                    val result = findSubExpr(arg.expression)
                    if (result != null) return result
                }
            } else if (expr is Concrete.ReferenceExpression && (expr.data as? PsiElement)?.let{ it.textRange == element.textRange} == true) {
                return expr
            }
            return null
        }

        val subExpr = findSubExpr(concreteExpr)
        val underlyingReferable = error.definition.referable.underlyingReferable
        val parameters = getAllParametersForReferable(underlyingReferable).map { it.referableList.map { r -> Pair(it.isExplicit, r) } }.flatten()
        var textPieceToReplace: TextRange? = null
        var replacementText = ""
        var caretShift = 0

        if (subExpr is Concrete.AppExpression) {
            var i = 0
            var j = 0
            textPieceToReplace = (rangeData[subExpr.function]?.endOffset ?: -1).let{ startPosition -> TextRange(startPosition, startPosition) }
            var param: Pair<Boolean, Referable>? = null
            var argument: Concrete.Argument? = null

            assert (error.index <= parameters.size)

            while (i < error.index) {
                textPieceToReplace = textPieceToReplace?.let { TextRange(it.endOffset, it.endOffset) }
                param = parameters[i]
                argument = if (j < subExpr.arguments.size) subExpr.arguments[j] else null

                if (argument?.isExplicit == param.first) {
                    textPieceToReplace = rangeData[argument.expression]
                    replacementText = ""
                    i++
                    j++
                } else if (!param.first) {
                    i++; if (i == error.index) break
                    replacementText += " {_}"
                }
            }

            replacementText += if (parameters[error.index - 1].first) " $TGOAL" else " {$TGOAL}"
        } else if (subExpr is Concrete.ReferenceExpression) {
            var i = 0
            replacementText += (subExpr.data as? PsiElement)?.text ?: ""
            while (i < error.index) {
                val param = parameters[i]
                replacementText += if (error.index == i+1) if (param.first) " $TGOAL" else " {$TGOAL}" else  if (param.first) " _" else " {_}"
                i++
            }
            replacementText = "(${replacementText.trim()})"
            textPieceToReplace = rangeData[subExpr]
            caretShift = -1
        }

        if (textPieceToReplace!!.startOffset != textPieceToReplace.endOffset) replacementText = replacementText.trim()
        performTextModification(rootPsi, replacementText, textPieceToReplace.startOffset, textPieceToReplace.endOffset)
        editor?.caretModel?.moveToOffset(textPieceToReplace.startOffset + replacementText.length + caretShift)
    }
}
