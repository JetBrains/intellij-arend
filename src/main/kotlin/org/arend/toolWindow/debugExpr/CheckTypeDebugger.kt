package org.arend.toolWindow.debugExpr

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiElement
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import org.arend.ArendIcons
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.*
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
            SimpleColoredComponent().apply {
                icon = icon(expr)
                append(expr.toString())
            }
        }
        splitter.firstComponent = passJBList
        val varJBList = JBList(varList)
        varJBList.installCellRenderer { bind ->
            SimpleColoredComponent().apply {
                icon = icon(bind.typeExpr)
                val attributes = if (bind is DependentLink && !bind.isExplicit) SimpleTextAttributes.GRAY_ATTRIBUTES
                else SimpleTextAttributes.REGULAR_ATTRIBUTES
                append(bind.name, attributes)
                append(" : ", attributes)
                append(bind.typeExpr.toString(), attributes)
            }
        }
        splitter.secondComponent = varJBList
    }

    private fun icon(expr: Expression?) = when (expr) {
        is ConCallExpression -> ArendIcons.CONSTRUCTOR
        is DataCallExpression -> ArendIcons.DATA_DEFINITION
        is FunCallExpression -> ArendIcons.FUNCTION_DEFINITION
        is ClassCallExpression -> if (expr.definition.isRecord)
            ArendIcons.RECORD_DEFINITION else ArendIcons.CLASS_DEFINITION
        is FieldCallExpression -> ArendIcons.CLASS_FIELD
        is LamExpression -> ArendIcons.LAMBDA_EXPRESSION
        else -> ArendIcons.EXPRESSION
    }

    private fun icon(expr: Concrete.Expression?) = when (expr) {
        is Concrete.LamExpression -> ArendIcons.LAMBDA_EXPRESSION
        is Concrete.ClassExtExpression -> ArendIcons.CLASS_DEFINITION
        else -> ArendIcons.EXPRESSION
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
