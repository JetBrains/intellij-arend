package org.arend.typechecking

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendExpr
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.LocalExpressionPrettifier
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class ArendExpressionTypechecker(private val checkedExprData: ArendExpr, errorReporter: ErrorReporter, extension: ArendExtension?):
    CheckTypeVisitor(LinkedHashMap<Referable, Binding>(), LocalExpressionPrettifier(), errorReporter, null, extension, null) {

    var checkedExprResult: TypecheckingResult? = null
    var checkedExprRange: TextRange? = null
    private var diffDepthTop = -1
    private var diffDepthDown = -1

    override fun checkExpr(expr: Concrete.Expression?, expectedType: Expression?): TypecheckingResult? {
        val result = super.checkExpr(expr, expectedType) ?: return null
        val data = expr?.data as? PsiElement? ?: return result
        check(data, checkedExprData, result, true)
        check(checkedExprData, data, result, false)

        return result
    }

    private fun check(data1: PsiElement?, data2: PsiElement, result: TypecheckingResult, isParent: Boolean) {
        var diff = 0
        var data = data1
        while (data != null && data != data2) {
            data = data.parent
            diff++
        }
        if (data == data2) {
            if (diff == 0) {
                checkedExprResult = result
                diffDepthTop = 0
                checkedExprRange = data.textRange
            } else if (isParent) {
                if (diffDepthTop == -1 || diff < diffDepthTop) {
                    checkedExprResult = result
                    diffDepthTop = diff
                    checkedExprRange = data.textRange
                }
            } else if (diffDepthTop == -1) {
                if (diffDepthDown == -1 || diff < diffDepthDown) {
                    checkedExprResult = result
                    diffDepthDown = diff
                    checkedExprRange = data.textRange
                }
            }
        }
    }
}
