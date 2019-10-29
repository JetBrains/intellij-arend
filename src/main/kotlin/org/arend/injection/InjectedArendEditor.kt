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
import org.arend.error.GeneralError
import org.arend.error.doc.DocFactory
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.typechecking.error.local.GoalError
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class InjectedArendEditor(val project: Project,
                          val error: GeneralError) {
    private val editor: Editor?
    private val panel: JPanel?

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
        val ppConfig = ProjectPrintConfig(project, printOptionsKind)
        runReadAction {
            DocFactory.vHang(error.getHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)).accept(visitor, false)
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

    private class ProjectPrintConfig(project: Project, printOptionsKind: PrintOptionKind): PrettyPrinterConfig {
        private val flags = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)

        override fun getExpressionFlags() = flags
    }
}