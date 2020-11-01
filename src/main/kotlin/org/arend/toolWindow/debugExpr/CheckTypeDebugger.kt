package org.arend.toolWindow.debugExpr

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiElement
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.panel
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.term.concrete.Concrete
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class CheckTypeDebugger(
    errorReporter: ErrorReporter,
    extension: ArendExtension?,
    private val element: PsiElement,
    val toolWindow: ToolWindow,
) : CheckTypeVisitor(errorReporter, null, extension), Disposable {
    private var isResuming = true
    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult {
        if (expr.data == element) {
            isResuming = false
        }
        if (!isResuming) {
            fillLocalVariables()
            while (!isResuming) {
                Thread.onSpinWait()
            }
        }
        passList.add(expr)
        val result = super.checkExpr(expr, expectedType)
        passList.remove(passList.size - 1)
        return result
    }

    val splitter = JBSplitter(false, 0.25f)
    private val passList = CollectionListModel<Concrete.Expression>()
    private val varList = CollectionListModel<Binding>()

    init {
        val passJBList = JBList(passList)
        passJBList.installCellRenderer { expr ->
            panel {
            }
        }
        splitter.firstComponent = passJBList
        val varJBList = JBList(varList)
        varJBList.installCellRenderer { bind ->
            panel {
            }
        }
        splitter.secondComponent = varJBList
    }

    private fun fillLocalVariables() {
        varList.removeAll()
        context.forEach { (_, u) ->
            varList.add(u)
        }
    }

    override fun dispose() {
        splitter.dispose()
    }
}
