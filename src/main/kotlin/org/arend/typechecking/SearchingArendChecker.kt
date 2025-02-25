package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class SearchingArendChecker(private val factory: SearchingArendCheckerFactory, errorReporter: ErrorReporter?, pool: GlobalInstancePool?, extension: ArendExtension?) : CheckTypeVisitor(errorReporter, pool, extension) {
    override fun checkExpr(expr: Concrete.Expression?, expectedType: Expression?): TypecheckingResult? {
        val result = super.checkExpr(expr, expectedType) ?: return null
        expr ?: return result
        val rangeOfConcrete = runReadAction { rangeOfConcrete(expr) }
        if (factory.exprRange in rangeOfConcrete) {
            if (factory.checkedExprRange == null || factory.checkedExprRange?.contains(rangeOfConcrete) == true) {
                setCheckedExprData(result, rangeOfConcrete)
            }
        }
        return result
    }

    private fun setCheckedExprData(result: TypecheckingResult, range: TextRange) {
        factory.checkedExprResult = result
        factory.checkedExprRange = range
    }
}
