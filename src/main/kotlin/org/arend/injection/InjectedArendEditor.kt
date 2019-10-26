package org.arend.injection

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import org.arend.InjectionTextLanguage
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.error.GeneralError
import org.arend.error.doc.DocFactory
import org.arend.settings.ArendProjectSettings
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.typechecking.error.local.GoalError
import java.util.*
import javax.swing.JComponent

class InjectedArendEditor(val project: Project,
                          val error: GeneralError) {
    private val editor: Editor?

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText("Error Message", InjectionTextLanguage.INSTANCE, "")
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let { document ->
                EditorFactory.getInstance().createEditor(document, project, virtualFile, true)
            }
        } else null

        updateErrorText()
    }

    fun release() {
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    val component: JComponent?
        get() = editor?.component

    fun updateErrorText() {
        if (editor != null) {
            val builder = StringBuilder()
            val visitor = CollectingDocStringBuilder(builder, error)
            val printOptionsKind = if (error is GoalError) PrintOptionKind.GOAL_PRINT_OPTIONS else PrintOptionKind.ERROR_PRINT_OPTIONS
            val ppConfig = ProjectPrintConfig(project, printOptionsKind)
            DocFactory.vHang(error.getHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)).accept(visitor, false)

            val text: CharSequence = builder.toString()
            val injectedTextRanges: List<List<TextRange>> = visitor.textRanges
            val hyperlinks: List<Pair<TextRange, HyperlinkInfo>> = visitor.hyperlinks

            val support = EditorHyperlinkSupport.get(editor)
            val document = editor.document
            val psi = PsiDocumentManager.getInstance(project).getPsiFile(document)
            runWriteAction { document.setText(text) }

            (psi as? PsiInjectionTextFile)?.injectionRanges = injectedTextRanges
            support.clearHyperlinks()
            if (hyperlinks.isNotEmpty()) {
                for (hyperlink in hyperlinks) {
                    support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
                }
            }
        }
    }

    private class ProjectPrintConfig(val project: Project, val printOptionsKind: PrintOptionKind): PrettyPrinterConfig {
        override fun getExpressionFlags(): EnumSet<ToAbstractVisitor.Flag> = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)
    }
}