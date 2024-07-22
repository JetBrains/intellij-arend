package org.arend.typechecking

import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.Referable
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.LocalExpressionPrettifier
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class ArendExpressionTypechecker(private val checkedExpr: Concrete.Expression, errorReporter: ErrorReporter, extension: ArendExtension?) :
    CheckTypeVisitor(LinkedHashMap<Referable, Binding>(), LocalExpressionPrettifier(), errorReporter, null, extension, null) {

    var checkedExprResult: TypecheckingResult? = null

    override fun checkExpr(expr: Concrete.Expression?, expectedType: Expression?): TypecheckingResult {
        val result = super.checkExpr(expr, expectedType)
        if (checkedExpr.data == expr?.data) {
            checkedExprResult = result
        }
        return result
    }
}
