package org.arend.toolWindow.tracer

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.JBSplitter
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
import org.arend.psi.ArendPsiFactory
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.typechecking.computation.CancellationIndicator
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor
import java.awt.Component
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreePath
import kotlin.concurrent.withLock

class TracingTypechecker(
    errorReporter: ErrorReporter,
    extension: ArendExtension?,
    private val element: PsiElement,
    private val editor: Editor,
    private val cancellationIndicator: CancellationIndicator
) : CheckTypeVisitor(errorReporter, null, extension), Disposable {
    private var isResuming = true
    private val lock = ReentrantLock()
    private var condition = lock.newCondition()
    private var isBP = false

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult? {
        val node = DefaultMutableTreeNode(expr, true)
        node.add(DefaultMutableTreeNode(expr.javaClass, false))
        node.add(DefaultMutableTreeNode(expectedType, false))
        passModel.insertNodeInto(node, passRoot, 0)
        if (expr.data == element || isBP) {
            isResuming = false
            isBP = true
            focusConcrete(expr)
        }
        if (!isResuming) {
            ComputationRunner.unlock()
            passTree.expandPath(TreePath(node.path))
            lock.withLock {
                while (!isResuming) {
                    condition.await()
                    ComputationRunner.checkCanceled()
                }
            }
            ComputationRunner.lock(cancellationIndicator)
        }
        val result = super.checkExpr(expr, expectedType)
        passModel.removeNodeFromParent(passRoot.firstChild as MutableTreeNode)
        return result
    }

    private val project get() = element.project
    val splitter = JBSplitter(false, 0.25f)
    private val passRoot = DefaultMutableTreeNode("Stack trace", true)
    private val passTree = Tree(passRoot)
    private val passModel = passTree.model as DefaultTreeModel
    private val editorFactory = EditorFactory.getInstance()
    private val consoleEditor = run {
        val psi = ArendPsiFactory(project, ArendTracerService.TITLE).injected()
        // editorFactory.createEditor()
        Unit
    }

    private var focusedConcrete: Concrete.Expression? = null
    private var lastRangeHighlighter: RangeHighlighter? = null
    private val highlightManager = HighlightManager.getInstance(project)

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
            configureCell(expr.typeExpr, cell, isExpectedType)
            text = "${expr.name} : $text"
        }
        is String -> cell.apply { text = expr }
        is Class<*> -> cell.apply { text = "Type: ${expr.simpleName}" }
        null -> cell
        else -> error("Bad argument: ${expr.javaClass}")
    }

    override fun finalize(result: TypecheckingResult?, sourceNode: Concrete.SourceNode?, propIfPossible: Boolean): TypecheckingResult {
        isBP = false
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
                    ((value
                        as? DefaultMutableTreeNode)?.userObject
                        as? Concrete.Expression)
                        ?.let { focusConcrete(it) }
                }
                return this
            }
        }
        splitter.firstComponent = JBScrollPane(passTree)
        // splitter.secondComponent = JBScrollPane(varJBList)
    }

    private fun focusConcrete(concrete: Concrete.Expression) {
        if (concrete === focusedConcrete) return
        focusedConcrete = concrete
        runInEdt {
            checkAndRemoveExpressionHighlight()
            val list = SmartList<RangeHighlighter>()
            val range = rangeOfConcrete(concrete)
            highlightManager.addRangeHighlight(editor,
                range.startOffset, range.endOffset,
                ArendTracerService.TRACED_EXPRESSION,
                false, false, list)
            lastRangeHighlighter = list[0]
        }
    }

    fun checkAndRemoveExpressionHighlight() {
        val rangeHighlighter = lastRangeHighlighter
        if (rangeHighlighter != null) {
            highlightManager.removeSegmentHighlighter(editor, rangeHighlighter)
            lastRangeHighlighter = null
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

    override fun dispose() {
        splitter.dispose()
        checkAndRemoveExpressionHighlight()
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
        object : BreakpointAction("Resume", ArendIcons.TRACER_RESUME) {
            override fun actionPerformed(e: AnActionEvent) = lock.withLock {
                isBP = false
                isResuming = true
                condition.signal()
            }
        },
        object : BreakpointAction("Step", ArendIcons.TRACER_STEP) {
            override fun actionPerformed(e: AnActionEvent) = lock.withLock {
                isResuming = true
                condition.signal()
            }
        },
        object : BreakpointAction("Stop", ArendIcons.TRACER_STOP) {
            override fun actionPerformed(e: AnActionEvent) = lock.withLock {
                isBP = false
                isResuming = true
                checkAndRemoveExpressionHighlight()
                ComputationRunner.getCancellationIndicator().cancel()
                condition.signal()
            }
        },
    )
}
