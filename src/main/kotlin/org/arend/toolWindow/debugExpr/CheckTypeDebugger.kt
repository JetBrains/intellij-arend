package org.arend.toolWindow.debugExpr

import com.intellij.psi.PsiElement
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.term.concrete.Concrete
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class CheckTypeDebugger(
    errorReporter: ErrorReporter,
    instancePool: GlobalInstancePool,
    extension: ArendExtension,
    private val element: PsiElement,
) : CheckTypeVisitor(errorReporter, instancePool, extension) {
    private var isResuming = true
    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult {
        if (expr.data == element) {
            isResuming = false
        }
        if (!isResuming) {
            fillLocalVariables(expr)
            while (!isResuming) {
                Thread.onSpinWait()
            }
        }
        return super.checkExpr(expr, expectedType)
    }

    private fun fillLocalVariables(expr: Concrete.Expression) {
    }
}
