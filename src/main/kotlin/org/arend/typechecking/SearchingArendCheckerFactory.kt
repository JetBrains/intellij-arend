package org.arend.typechecking

import com.intellij.openapi.util.TextRange
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.psi.ext.ArendExpr
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.ArendCheckerFactory

class SearchingArendCheckerFactory(expr: ArendExpr) : ArendCheckerFactory {
    val exprRange = expr.textRange
    var checkedExprResult: TypecheckingResult? = null
    var checkedExprRange: TextRange? = null

    override fun create(errorReporter: ErrorReporter?, pool: GlobalInstancePool?, extension: ArendExtension?) =
        SearchingArendChecker(this, errorReporter, pool, extension)
}