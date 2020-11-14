package org.arend.toolWindow.debugExpr

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiElement
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.render.LabelBasedRenderer
import org.arend.ArendIcons
import org.arend.core.context.binding.Binding
import org.arend.core.expr.*
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.psi.ArendArgumentAppExpr
import org.arend.term.concrete.Concrete
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor
import java.awt.Component
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode

typealias PassData = Pair<Concrete.Expression, Expression?>

class CheckTypeDebugger(
    errorReporter: ErrorReporter,
    extension: ArendExtension?,
    private val element: PsiElement,
    val toolWindow: ToolWindow,
) : CheckTypeVisitor(errorReporter, null, extension), Disposable {
    lateinit var thread: Thread
    private var isResuming = true

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult? {
        val matches = run {
            val exprElement = expr.data as? PsiElement ?: return@run false
            var range: TextRange? = null
            if (exprElement is ArendArgumentAppExpr) {
                exprElement.firstChild?.apply { range = textRange }
            }
            (range ?: exprElement.textRange) == element.textRange
        }
        if (matches) {
            isResuming = false
        }
        if (!isResuming) {
            fillLocalVariables()
            while (!isResuming) {
                Thread.onSpinWait()
            }
        }
        passList.add(0, expr to expectedType)
        val result = super.checkExpr(expr, expectedType)
        passList.remove(0)
        return result
    }

    val splitter = JBSplitter(false, 0.25f)
    private val passList = CollectionListModel<PassData>(ConcurrentLinkedDeque())
    private val varList = CollectionListModel<Binding>()

    private fun configureCell(expr: Any?, cell: JLabel, isExpectedType: Boolean = false): JComponent = when (expr) {
        is DefaultMutableTreeNode -> configureCell(expr.userObject, cell, isExpectedType)
        is Concrete.Expression -> cell.apply {
            icon = icon(expr)
            text = "[${expr.javaClass.simpleName}] $expr"
        }
        is Expression -> cell.apply {
            icon = icon(expr)
            text = buildString {
                if (isExpectedType) append("Expected type: ")
                else append("[${expr.javaClass.simpleName}] ")
                append(expr.toString())
            }
        }
        is Binding -> cell.apply {
            icon = icon(expr.typeExpr)
            text = buildString {
                append(expr.name)
                append(" : ")
                append(expr.typeExpr.toString())
            }
        }
        null -> cell
        else -> error("Bad argument: ${expr.javaClass}")
    }

    init {
        val passJBList = JBList(passList)
        passJBList.cellRenderer = object : LabelBasedRenderer.List<PassData>() {
            override fun getListCellRendererComponent(list: JList<out PassData>, value: PassData?, index: Int, selected: Boolean, focused: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, selected, focused)
                if (value == null) return this
                val root = DefaultMutableTreeNode(value.first, true)
                root.add(DefaultMutableTreeNode(value.second))
                val tree = JTree(root)
                tree.cellRenderer = object : LabelBasedRenderer.Tree() {
                    override fun getTreeCellRendererComponent(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, focused: Boolean): Component {
                        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused)
                        configureCell(value, this, isExpectedType = true)
                        return this
                    }
                }
                return tree
            }
        }
        splitter.firstComponent = JBScrollPane(passJBList)
        val varJBList = JBList(varList)
        varJBList.installCellRenderer { bind ->
            LabelBasedRenderer.List<Binding>().also { configureCell(bind, it) }
        }
        splitter.secondComponent = JBScrollPane(varJBList)
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
        context.forEach { (_, u) -> varList.add(u) }
    }

    override fun dispose() {
        splitter.dispose()
    }
}
