package org.arend.injection

import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import org.arend.InjectionTextLanguage
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.Reference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.resolving.ArendReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.typechecking.error.ArendError
import org.arend.ui.console.ArendClearConsoleAction
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class InjectedArendEditor(val project: Project, name: String, val arendError: ArendError?) {
    private val editor: Editor?
    private val panel: JPanel?

    val error: GeneralError?
        get() = arendError?.error

    private val printOptionKind: PrintOptionKind
        get() = when (error?.level) {
            null -> PrintOptionKind.CONSOLE_PRINT_OPTIONS
            GeneralError.Level.GOAL -> PrintOptionKind.GOAL_PRINT_OPTIONS
            else -> PrintOptionKind.ERROR_PRINT_OPTIONS
        }

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText(name, InjectionTextLanguage.INSTANCE, "")
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
            if (error != null) {
                actionGroup.add(ActionManager.getInstance().getAction("Arend.PinErrorMessage"))
            } else {
                actionGroup.add(ArendClearConsoleAction(project, editor.contentComponent))
            }
            actionGroup.add(ArendPrintOptionsActionGroup(project, printOptionKind, error?.hasExpressions() ?: true))
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
        val error = error ?: return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, error)
        var fileScope: Scope = EmptyScope.INSTANCE
        runReadAction {
            val causeSourceNode = error.causeSourceNode
            val data = (causeSourceNode?.data as? DataContainer)?.data ?: causeSourceNode?.data
            val unresolvedRef = (data as? Reference)?.referent
            val scope = if (unresolvedRef != null || error.hasExpressions()) (data as? PsiElement)?.ancestor<ArendCompositeElement>()?.scope?.let { CachingScope.make(it) } else null
            if (scope != null) {
                fileScope = scope
            }
            val ref = if (unresolvedRef != null && scope != null) ExpressionResolveNameVisitor.resolve(unresolvedRef, scope) else null
            val ppConfig = ProjectPrintConfig(project, printOptionKind, scope?.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) })
            val doc = if ((ref as? ModuleAdapter)?.metaReferable?.definition != null && (causeSourceNode as? Concrete.ReferenceExpression)?.referent != ref)
                error.getDoc(ppConfig)
            else
                DocFactory.vHang(error.getHeaderDoc(ppConfig), error.getBodyDoc(ppConfig))
            doc.accept(visitor, false)
        }

        val text = builder.toString()
        val document = editor.document
        runWriteAction {
            document.setText(text)
        }

        setEditorStructure(document, visitor, fileScope)
    }

    fun addDoc(doc: Doc) {
        if (editor == null) return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, error)
        doc.accept(visitor, false)
        builder.append('\n')

        val text = builder.toString()
        val document = editor.document
        ApplicationManager.getApplication().invokeLater { runUndoTransparentWriteAction {
            val len = document.textLength
            document.insertString(len, text)
            editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(len + text.length), ScrollType.MAKE_VISIBLE)
        } }

        setEditorStructure(document, visitor, EmptyScope.INSTANCE)
    }

    fun clearText() {
        editor?.document?.let {
            runWriteAction {
                it.setText("")
            }
        }
    }

    private fun setEditorStructure(document: Document, visitor: CollectingDocStringBuilder, fileScope: Scope) {
        val psi = PsiDocumentManager.getInstance(project).getPsiFile(document)
        (psi as? PsiInjectionTextFile)?.apply {
            injectionRanges = visitor.textRanges
            scope = fileScope
            injectedExpressions = visitor.expressions
        }

        if (editor == null) return
        val support = EditorHyperlinkSupport.get(editor)
        support.clearHyperlinks()
        for (hyperlink in visitor.hyperlinks) {
            support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
        }
    }

    private class ProjectPrintConfig(project: Project, printOptionsKind: PrintOptionKind, scope: Scope?) : PrettyPrinterConfigWithRenamer(scope) {
        private val flags = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)

        override fun getExpressionFlags() = flags
    }
}