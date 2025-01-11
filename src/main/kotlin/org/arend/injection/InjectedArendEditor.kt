package org.arend.injection

import com.intellij.diff.contents.DocumentContent
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.Expression
import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.ext.core.context.CoreParameter
import org.arend.ext.core.expr.CoreExpression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.reference.DataContainer
import org.arend.injection.actions.RevealingInformationCaretListener
import org.arend.injection.actions.UnblockingDocumentAction
import org.arend.injection.actions.withNormalizedTerms
import org.arend.naming.reference.Referable
import org.arend.naming.reference.Reference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendDefMeta
import org.arend.resolving.ArendReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.term.prettyprint.TermWithSubtermDoc
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import org.arend.typechecking.error.DiffHyperlinkInfo
import org.arend.typechecking.error.local.TypeMismatchWithSubexprError
import org.arend.typechecking.error.mapToTypeDiffInfo
import org.arend.util.ArendBundle
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel

abstract class InjectedArendEditor(
    val project: Project,
    name: String,
    var treeElement: ArendErrorTreeElement?,
) {
    protected val editor: Editor?
    private val panel: JPanel?
    protected val actionGroup: DefaultActionGroup = DefaultActionGroup()

    fun isEmptyActionGroup() = actionGroup.childrenCount == 0

    protected abstract val printOptionKind: PrintOptionKind

    var currentDoc: Doc? = null

    val verboseLevelMap: MutableMap<Expression, Int> = mutableMapOf()
    val verboseLevelParameterMap: MutableMap<DependentLink, Int> = mutableMapOf()

    init {
        val psi = ArendPsiFactory(project, name).injected("")
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let { document ->
                document.setReadOnly(true)
                EditorFactory.getInstance().createEditor(document, project, virtualFile, false).apply {
                    settings.setGutterIconsShown(false)
                    settings.isRightMarginShown = false
                    putUserData(AREND_GOAL_EDITOR, this@InjectedArendEditor)
                    caretModel.addCaretListener(RevealingInformationCaretListener(this@InjectedArendEditor))
                }
            }
        } else null

        if (editor != null) {
            panel = JPanel(BorderLayout())
            panel.add(editor.component, BorderLayout.CENTER)

            val toolbar = ActionManager.getInstance().createActionToolbar("ArendEditor.toolbar", actionGroup, false)
            toolbar.targetComponent = panel
            panel.add(toolbar.component, BorderLayout.WEST)
        } else {
            panel = null
        }

        updateErrorText()
    }

    fun release() {
        if (editor != null) {
            editor.putUserData(AREND_GOAL_EDITOR, null)
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    val component: JComponent?
        get() = panel


    fun addEditorComponent() {
        if (editor != null) {
            panel?.add(editor.component, BorderLayout.CENTER)
        }
    }
    fun removeUnnecessaryComponents(collection: Collection<Component>) {
        collection.forEach { panel?.remove(it) }
    }

    private data class DiffInfo(
        val firstDocumentContent: DocumentContent,
        val secondDocumentContent: DocumentContent,
        val startOffset: Int,
        val endOffset: Int
    )

    fun updateErrorText(id: String? = null, postWriteCallback: () -> Unit = {}) {
        if (editor == null) return
        val treeElement = treeElement ?: return

        invokeLater {
            val builder = StringBuilder()
            val visitor = CollectingDocStringBuilder(builder, treeElement.sampleError)
            var fileScope: Scope = EmptyScope.INSTANCE
            val newErrorRanges = ArrayList<TextRange>()
            val diffInfos = mutableListOf<DiffInfo>()

            ApplicationManager.getApplication().executeOnPooledThread {
                runReadAction {
                    var first = true
                    for (error in treeElement.errors) {
                        if (first) {
                            first = false
                        } else {
                            builder.append("\n\n")
                        }

                        val (resolve, scope) = resolveCauseReference(error)
                        if (scope != null) {
                            fileScope = scope
                        }
                        val doc = getDoc(treeElement, error, resolve, scope)
                        currentDoc = doc
                        doc.accept(visitor, false)

                        val injectionRanges = visitor.textRanges
                        if (error is TypeMismatchWithSubexprError && injectionRanges.size >= 2) {
                            val ppConfig = getCurrentConfig(scope)
                            val expectedDoc = TermWithSubtermDoc(error.result.wholeExpr2, error.result.subExpr2, error.result.levels2, null, ppConfig)
                            expectedDoc.init()
                            if (expectedDoc.end > expectedDoc.begin) {
                                addErrorRange(TextRange(expectedDoc.begin, expectedDoc.end), injectionRanges[injectionRanges.size - 2], newErrorRanges)
                            }
                            val actualDoc = TermWithSubtermDoc(error.result.wholeExpr1, error.result.subExpr1, error.result.levels1, null, ppConfig)
                            actualDoc.init()
                            if (actualDoc.end > actualDoc.begin) {
                                addErrorRange(TextRange(actualDoc.begin, actualDoc.end), injectionRanges[injectionRanges.size - 1], newErrorRanges)
                            }
                        }

                        mapToTypeDiffInfo(error)?.let {
                            builder.appendLine()
                            val start = builder.lastIndex + 1
                            builder.append(ArendBundle.message("arend.click.to.see.diff.link"))
                            val finish = builder.lastIndex + 1
                            diffInfos.add(DiffInfo(it.first, it.second, start, finish))
                        }
                    }
                }

                val text = builder.toString()
                if (editor.isDisposed) return@executeOnPooledThread
                val action: () -> Unit = {
                    UndoManager.getInstance(project)
                        .undoableActionPerformed(UnblockingDocumentAction(editor.document, id, false))
                    modifyDocument { setText(text) }
                    getInjectionFile()?.apply {
                        injectionRanges = visitor.textRanges
                        scope = fileScope
                        injectedExpressions = visitor.expressions
                        errorRanges = newErrorRanges
                    }
                    postWriteCallback()
                    UndoManager.getInstance(project)
                        .undoableActionPerformed(UnblockingDocumentAction(editor.document, id, true))
                }
                WriteCommandAction.runWriteCommandAction(project, null, id, action)
                val support = EditorHyperlinkSupport.get(editor)
                support.clearHyperlinks()
                for (hyperlink in visitor.hyperlinks) {
                    support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
                }

                for (diffInfo in diffInfos) {
                    support.createHyperlink(
                        diffInfo.startOffset,
                        diffInfo.endOffset,
                        null,
                        DiffHyperlinkInfo(Pair(diffInfo.firstDocumentContent, diffInfo.secondDocumentContent)))
                }
            }
        }
    }

    private fun insertPosition(pos: Int, injectionRanges: List<TextRange>, inclusive: Boolean): Int {
        var skipped = 0
        for (injectionRange in injectionRanges) {
            val newSkipped = skipped + injectionRange.length + 1
            if (if (inclusive) pos <= newSkipped else pos < newSkipped) {
                return injectionRange.startOffset + pos - skipped
            }
            skipped = newSkipped
        }
        return -1
    }

    private fun addErrorRange(range: TextRange, injectionRanges: List<TextRange>, errorRanges: MutableList<TextRange>) {
        val start = insertPosition(range.startOffset, injectionRanges, false)
        val end = insertPosition(range.endOffset, injectionRanges, true)
        if (start >= 0 && end >= 0) {
            errorRanges.add(TextRange(start, end))
        }
    }

    fun getCurrentConfig(scope: Scope?): PrettyPrinterConfig {
        return ProjectPrintConfig(
            project,
            printOptionKind,
            scope?.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) },
            verboseLevelMap,
            verboseLevelParameterMap
        )
    }

    fun getDoc(treeElement: ArendErrorTreeElement, error: GeneralError, resolve: Referable?, scope: Scope?): Doc {
        val ppConfig = getCurrentConfig(scope)
        return if (causeIsMetaExpression(error.causeSourceNode, resolve)) {
            error.getDoc(ppConfig).withNormalizedTerms(treeElement.normalizationCache, ppConfig, error)
        } else {
            DocFactory.vHang(
                error.getHeaderDoc(ppConfig).withNormalizedTerms(treeElement.normalizationCache, ppConfig, error),
                error.getBodyDoc(ppConfig).withNormalizedTerms(treeElement.normalizationCache, ppConfig, error)
            )
        }
    }

    fun addDoc(doc: Doc, docScope: Scope) {
        if (editor == null) return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, treeElement?.sampleError)
        doc.accept(visitor, false)
        builder.append('\n')
        val text = builder.toString()
        ApplicationManager.getApplication().invokeLater {
            runUndoTransparentWriteAction {
                if (editor.isDisposed) return@runUndoTransparentWriteAction
                val document = editor.document
                val length = document.textLength
                modifyDocument { insertString(textLength, text) }
                editor.scrollingModel.scrollTo(
                    editor.offsetToLogicalPosition(length + text.length),
                    ScrollType.MAKE_VISIBLE
                )

                getInjectionFile()?.apply {
                    scope = docScope
                    injectionRanges.addAll(visitor.textRanges.map { list -> list.map { it.shiftRight(length) } })
                    injectedExpressions.addAll(visitor.expressions)
                }

                val support = EditorHyperlinkSupport.get(editor)
                for (hyperlink in visitor.hyperlinks) {
                    support.createHyperlink(
                        length + hyperlink.first.startOffset,
                        length + hyperlink.first.endOffset,
                        null,
                        hyperlink.second
                    )
                }
            }
        }
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
                modifyDocument { setText("") }
                currentDoc = null
            }
        }
    }

    fun getInjectionFile(): PsiInjectionTextFile? = editor?.document?.let {
        PsiDocumentManager.getInstance(project).getPsiFile(it) as? PsiInjectionTextFile
    }

    class ProjectPrintConfig(
        project: Project,
        printOptionsKind: PrintOptionKind,
        scope: Scope?,
        private val verboseLevelMap: Map<Expression, Int>,
        private val verboseLevelParameterMap: Map<DependentLink, Int>
    ) :
        PrettyPrinterConfigWithRenamer(scope) {
        private val flags = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)

        override fun getExpressionFlags() = flags

        override fun getVerboseLevel(expression: CoreExpression): Int {
            return verboseLevelMap[expression] ?: 0
        }

        override fun getVerboseLevel(parameter: CoreParameter): Int {
            return verboseLevelParameterMap[parameter] ?: 0
        }

        override fun getNormalizationMode(): NormalizationMode? {
            return null
        }
    }

    fun getOffsetInEditor(relativeOffsetInInjection: Int, indexOfInjection: Int): Int? {
        if (indexOfInjection == -1) {
            return null
        }
        val ranges = getInjectionFile()?.injectionRanges?.getOrNull(indexOfInjection) ?: return null
        var mutableRelativeOffset = relativeOffsetInInjection
        val text = editor?.document?.text ?: return null
        for (range in ranges) {
            val substring = text.subSequence(range.startOffset, range.endOffset)
            val initialSpaces = substring.takeWhile { it.isWhitespace() }.length
            val actualRangeLength = range.length - initialSpaces
            if (mutableRelativeOffset >= actualRangeLength) {
                mutableRelativeOffset -= actualRangeLength + 1 // for space
            } else {
                return range.startOffset + mutableRelativeOffset + initialSpaces
            }
        }
        return null
    }

    protected fun modifyDocument(modifier: Document.() -> Unit) {
        val thisEditor = editor ?: return
        thisEditor.document.setReadOnly(false)
        try {
            thisEditor.document.modifier()
        } finally {
            thisEditor.document.setReadOnly(true)
        }
    }

    companion object {
        val AREND_GOAL_EDITOR: Key<InjectedArendEditor> = Key.create("Arend goal editor")

        // Don't call this method on EDT
        fun resolveCauseReference(error: GeneralError): Pair<Referable?, Scope?> {
            val causeSourceNode = error.causeSourceNode
            val data = (causeSourceNode?.data as? DataContainer)?.data ?: causeSourceNode?.data
            val unresolvedRef = (data as? Reference)?.referent

            var scope: Scope? = null
            var ref: Referable? = null
            if (unresolvedRef != null || error.hasExpressions()) {
                val compositeElement = (data as? PsiElement)?.ancestor<ArendCompositeElement>()
                if (compositeElement?.isValid == true) scope = compositeElement.scope.let { CachingScope.make(it) }
            }
            if (unresolvedRef != null && scope != null) {
                ref = ExpressionResolveNameVisitor.resolve(unresolvedRef, scope)
            }
            return Pair(ref, scope)
        }

        fun causeIsMetaExpression(cause: ConcreteSourceNode?, resolve: Referable?) =
            (resolve as? ArendDefMeta)?.metaRef?.definition != null &&
                    (cause as? Concrete.ReferenceExpression)?.referent != resolve
    }
}