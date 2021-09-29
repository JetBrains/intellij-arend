package org.arend.injection

import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.application.runWriteAction
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
import org.arend.psi.ext.impl.MetaAdapter
import org.arend.resolving.ArendReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

abstract class InjectedArendEditor(val project: Project, name: String, var treeElement: ArendErrorTreeElement?) {
    protected val editor: Editor?
    private val panel: JPanel?
    protected val actionGroup: DefaultActionGroup = DefaultActionGroup()

    protected val printOptionKind: PrintOptionKind
        get() = when (treeElement?.highestError?.error?.level) {
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
        val treeElement = treeElement ?: return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, treeElement.sampleError.error)
        var fileScope: Scope = EmptyScope.INSTANCE
        runReadAction {
            var first = true
            for (arendError in treeElement.errors) {
                if (first) {
                    first = false
                } else {
                    builder.append("\n\n")
                }

                val error = arendError.error
                val causeSourceNode = error.causeSourceNode
                val data = (causeSourceNode?.data as? DataContainer)?.data ?: causeSourceNode?.data
                val unresolvedRef = (data as? Reference)?.referent
                val scope = if (unresolvedRef != null || error.hasExpressions()) (data as? PsiElement)?.ancestor<ArendCompositeElement>()?.scope?.let { CachingScope.make(it) } else null
                if (scope != null) {
                    fileScope = scope
                }
                val ref = if (unresolvedRef != null && scope != null) ExpressionResolveNameVisitor.resolve(unresolvedRef, scope) else null
                val ppConfig = ProjectPrintConfig(project, printOptionKind, scope?.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) })
                val doc = if ((ref as? MetaAdapter)?.metaRef?.definition != null && (causeSourceNode as? Concrete.ReferenceExpression)?.referent != ref)
                    error.getDoc(ppConfig)
                else
                    DocFactory.vHang(error.getHeaderDoc(ppConfig), error.getBodyDoc(ppConfig))
                doc.accept(visitor, false)
            }
        }

        val text = builder.toString()
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater
            runWriteAction {
                editor.document.setText(text)
                getInjectionFile()?.apply {
                    injectionRanges = visitor.textRanges
                    scope = fileScope
                    injectedExpressions = visitor.expressions
                }
            }
            val support = EditorHyperlinkSupport.get(editor)
            support.clearHyperlinks()
            for (hyperlink in visitor.hyperlinks) {
                support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
            }
        }
    }

    fun addDoc(doc: Doc, docScope: Scope) {
        if (editor == null) return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, treeElement?.sampleError?.error)
        doc.accept(visitor, false)
        builder.append('\n')
        val text = builder.toString()
        ApplicationManager.getApplication().invokeLater { runUndoTransparentWriteAction {
            if (editor.isDisposed) return@runUndoTransparentWriteAction
            val document = editor.document
            val length = document.textLength
            document.insertString(length, text)
            editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(length + text.length), ScrollType.MAKE_VISIBLE)

            getInjectionFile()?.apply {
                scope = docScope
                injectionRanges.addAll(visitor.textRanges.map { list -> list.map { it.shiftRight(length) } })
                injectedExpressions.addAll(visitor.expressions)
            }

            val support = EditorHyperlinkSupport.get(editor)
            for (hyperlink in visitor.hyperlinks) {
                support.createHyperlink(length + hyperlink.first.startOffset, length + hyperlink.first.endOffset, null, hyperlink.second)
            }
        } }
    }

    fun clearText() {
        editor ?: return
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater
            runWriteAction {
                getInjectionFile()?.apply {
                    injectionRanges.clear()
                    injectedExpressions.clear()
                }
                editor.document.setText("")
            }
        }
    }

    private fun getInjectionFile(): PsiInjectionTextFile? = editor?.document?.let {
        PsiDocumentManager.getInstance(project).getPsiFile(it) as? PsiInjectionTextFile
    }

    private class ProjectPrintConfig(project: Project, printOptionsKind: PrintOptionKind, scope: Scope?) : PrettyPrinterConfigWithRenamer(scope) {
        private val flags = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)

        override fun getExpressionFlags() = flags
    }
}