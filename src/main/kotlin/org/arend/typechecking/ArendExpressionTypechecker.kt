package org.arend.typechecking

import com.intellij.openapi.util.TextRange
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.LocalExpressionPrettifier
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class ArendExpressionTypechecker(checkedExprData: ArendExpr, errorReporter: ErrorReporter, extension: ArendExtension?):
    CheckTypeVisitor(LinkedHashMap<Referable, Binding>(), LocalExpressionPrettifier(), errorReporter, null, extension, null) {

    private val exprRange = checkedExprData.textRange
    var checkedExprResult: TypecheckingResult? = null
    var checkedExprRange: TextRange? = null

    override fun checkExpr(expr: Concrete.Expression?, expectedType: Expression?): TypecheckingResult? {
        val result = super.checkExpr(expr, expectedType) ?: return null
        expr ?: return result
        val rangeOfConcrete = rangeOfConcrete(expr)
        if (exprRange in rangeOfConcrete) {
            if (checkedExprRange == null || checkedExprRange?.contains(rangeOfConcrete) == true) {
                setCheckedExprData(result, rangeOfConcrete)
            }
        }
        return result
    }

    private fun setCheckedExprData(result: TypecheckingResult, range: TextRange) {
        checkedExprResult = result
        checkedExprRange = range
    }
}
