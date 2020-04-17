package org.arend.injection

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import org.arend.InjectionTextLanguage
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.naming.reference.Reference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.typechecking.error.ArendError
import org.arend.typechecking.error.local.GoalError
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class InjectedArendEditor(val project: Project,
                          val arendError: ArendError) {
    private val editor: Editor?
    private val panel: JPanel?

    val error: GeneralError
        get() = arendError.error

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText("Error Message", InjectionTextLanguage.INSTANCE, "")
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let { document ->
                EditorFactory.getInstance().createEditor(document, project, virtualFile, true)
            }
        } else null

        if (editor != null) {
            panel = JPanel(BorderLayout())
            panel.add(editor.component, BorderLayout.CENTER)

            val actionGroup = DefaultActionGroup()
            actionGroup.add(ActionManager.getInstance().getAction("Arend.PinErrorMessage"))
            actionGroup.add(ArendPrintOptionsActionGroup(project, if (error.level == GeneralError.Level.GOAL) PrintOptionKind.GOAL_PRINT_OPTIONS else PrintOptionKind.ERROR_PRINT_OPTIONS, error.hasExpressions()))
            val toolbar = ActionManager.getInstance().createActionToolbar("ArendEditor.toolbar", actionGroup, false)
            toolbar.setTargetComponent(panel)
            panel.add(toolbar.component, BorderLayout.WEST)
        } else {
            panel = null
        }

        updateErrorText()
    }

    fun release() {
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    val component: JComponent?
        get() = panel

    fun updateErrorText() {
        if (editor == null) return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, error)
        val printOptionsKind = if (error is GoalError) PrintOptionKind.GOAL_PRINT_OPTIONS else PrintOptionKind.ERROR_PRINT_OPTIONS
        runReadAction {
            val causeSourceNode = error.causeSourceNode
            val unresolvedRef = (causeSourceNode?.data as? Reference)?.referent
            val scope = if (unresolvedRef != null || error.hasExpressions()) (causeSourceNode.data as? ArendCompositeElement)?.scope else null
            val ref = if (unresolvedRef != null && scope != null) ExpressionResolveNameVisitor.resolve(unresolvedRef, scope) else null
            val ppConfig = ProjectPrintConfig(project, printOptionsKind, scope ?: EmptyScope.INSTANCE)
            val doc = if ((ref as? ModuleAdapter)?.metaReferable?.definition != null && (causeSourceNode as? Concrete.ReferenceExpression)?.referent != ref)
                error.getDoc(ppConfig)
            else
                DocFactory.vHang(error.getHeaderDoc(ppConfig), error.getBodyDoc(ppConfig))
            doc.accept(visitor, false)
        }

        val text: CharSequence = builder.toString()
        val injectedTextRanges: List<List<TextRange>> = visitor.textRanges
        val hyperlinks: List<Pair<TextRange, HyperlinkInfo>> = visitor.hyperlinks

        val support = EditorHyperlinkSupport.get(editor)
        val document = editor.document
        val psi = PsiDocumentManager.getInstance(project).getPsiFile(document)
        runWriteAction {
            document.setText(text)
        }

        (psi as? PsiInjectionTextFile)?.injectionRanges = injectedTextRanges
        support.clearHyperlinks()
        for (hyperlink in hyperlinks) {
            support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
        }
    }

    private class ProjectPrintConfig(project: Project, printOptionsKind: PrintOptionKind, scope: Scope) : PrettyPrinterConfigWithRenamer(scope) {
        private val flags = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)

        override fun getExpressionFlags() = flags
    }
}