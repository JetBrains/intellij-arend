package org.arend.toolWindow.debugExpr

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiElement
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.render.LabelBasedRenderer
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.SmartList
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
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreePath

class CheckTypeDebugger(
    errorReporter: ErrorReporter,
    extension: ArendExtension?,
    private val element: PsiElement,
    val toolWindow: ToolWindow,
    private val editor: Editor,
) : CheckTypeVisitor(errorReporter, null, extension), Disposable {
    lateinit var thread: Thread
    private var isResuming = true
    private var isBP = false

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult? {
        val node = DefaultMutableTreeNode(expr, true)
        node.add(DefaultMutableTreeNode(expr.javaClass, false))
        node.add(DefaultMutableTreeNode(expectedType, false))
        passModel.insertNodeInto(node, passRoot, 0)
        val exprElement = expr.data as PsiElement
        var rangeTmp: TextRange? = null
        if (exprElement is ArendArgumentAppExpr) {
            exprElement.firstChild?.apply { rangeTmp = textRange }
        }
        val range = rangeTmp ?: exprElement.textRange
        if (range == element.textRange || isBP) {
            isResuming = false
            isBP = true
            focusPsi(range)
        }
        if (!isResuming) {
            fillLocalVariables()
            passTree.expandPath(TreePath(node.path))
            while (!isResuming) {
                Thread.onSpinWait()
            }
            clearLocalVariables()
        }
        val result = super.checkExpr(expr, expectedType)
        passModel.removeNodeFromParent(passRoot.firstChild as MutableTreeNode)
        return result
    }

    val splitter = JBSplitter(false, 0.25f)
    private val passRoot = DefaultMutableTreeNode("Stack trace", true)
    private val passTree = Tree(passRoot)
    private val passModel = passTree.model as DefaultTreeModel
    private val varList = CollectionListModel<Binding>()
    private var focusedRange: TextRange? = null
    private var lastRangeHighlighter: RangeHighlighter? = null
    private val highlightManager = HighlightManager.getInstance(element.project)

    private fun configureCell(expr: Any?, cell: JLabel, isExpectedType: Boolean = false): JComponent = when (expr) {
        is DefaultMutableTreeNode -> configureCell(expr.userObject, cell, isExpectedType)
        is Concrete.Expression -> cell.apply {
            icon = icon(expr)
            text = "$expr"
        }
        is Expression -> cell.apply {
            icon = icon(expr)
            text = buildString {
                if (isExpectedType) append("Expected type: ")
                append(expr)
            }
        }
        is Binding -> cell.apply {
            icon = icon(expr.typeExpr)
            text = buildString {
                append(expr.name)
                append(" : ")
                append(expr.typeExpr)
            }
        }
        is String -> cell.apply { text = expr }
        is Class<*> -> cell.apply { text = "Type: ${expr.simpleName}" }
        null -> cell
        else -> error("Bad argument: ${expr.javaClass}")
    }

    override fun finalize(result: TypecheckingResult?, sourceNode: Concrete.SourceNode?, propIfPossible: Boolean): TypecheckingResult {
        runInEdt {
            checkAndRemoveExpressionHighlight()
        }
        return super.finalize(result, sourceNode, propIfPossible)
    }

    init {
        passTree.cellRenderer = object : LabelBasedRenderer.Tree() {
            override fun getTreeCellRendererComponent(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, focused: Boolean): Component {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused)
                configureCell(value, this, isExpectedType = true)
                if (selected && focused) {
                    (((value
                        as? DefaultMutableTreeNode)?.userObject
                        as? Concrete.Expression)?.data
                        as? PsiElement)?.textRange
                        ?.let { focusPsi(it) }
                }
                return this
            }
        }
        splitter.firstComponent = JBScrollPane(passTree)
        val varJBList = JBList(varList)
        varJBList.installCellRenderer { bind ->
            LabelBasedRenderer.List<Binding>().also { configureCell(bind, it) }
        }
        splitter.secondComponent = JBScrollPane(varJBList)
    }

    private fun focusPsi(range: TextRange) {
        if (range == focusedRange) return
        focusedRange = range
        runInEdt {
            checkAndRemoveExpressionHighlight()
            val list = SmartList<RangeHighlighter>()
            highlightManager.addRangeHighlight(editor,
                range.startOffset, range.endOffset,
                ArendDebugService.DEBUGGED_EXPRESSION,
                false, false, list)
            lastRangeHighlighter = list[0]
        }
    }

    fun checkAndRemoveExpressionHighlight() {
        val rangeHighlighter = lastRangeHighlighter
        if (rangeHighlighter != null) {
            highlightManager.removeSegmentHighlighter(editor, rangeHighlighter)
        }
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

    private fun clearLocalVariables() {
        varList.removeAll()
    }

    private fun fillLocalVariables() {
        context.forEach { (_, u) -> varList.add(u) }
    }

    override fun dispose() {
        splitter.dispose()
    }

    abstract inner class BreakpointAction(
        text: String,
        icon: Icon,
    ) : DumbAwareAction(text, null, icon) {
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = isBP
        }
    }

    fun createActionGroup() = DefaultActionGroup(
        object : BreakpointAction("Resume", ArendIcons.DEBUGGER_RESUME) {
            override fun actionPerformed(e: AnActionEvent) = synchronized(this@CheckTypeDebugger) {
                isBP = false
                isResuming = true
            }
        },
        object : BreakpointAction("Step", ArendIcons.DEBUGGER_STEP) {
            override fun actionPerformed(e: AnActionEvent) = synchronized(this@CheckTypeDebugger) {
                isResuming = true
            }
        },
    )
}
