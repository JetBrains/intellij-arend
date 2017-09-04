package org.vclang.typechecking

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.doc.HListDoc
import com.jetbrains.jetpad.vclang.error.doc.LineDocVisitor
import com.jetbrains.jetpad.vclang.error.doc.ReferenceDoc
import com.jetbrains.jetpad.vclang.error.doc.TermLineDoc
import com.jetbrains.jetpad.vclang.error.doc.TextDoc
import com.jetbrains.jetpad.vclang.term.SourceInfoProvider
import org.vclang.Surrogate

class TypeCheckConsoleLogger(
        private val sourceInfoProvider: SourceInfoProvider<VcSourceIdT>
) : ErrorReporter {
    var console: ConsoleView? = null

    override fun report(error: GeneralError) = DocConsolePrinter(error).print()

    fun reportError(message: String) =
        console?.print(message + '\n', ConsoleViewContentType.ERROR_OUTPUT)

    private fun levelToContentType(level: Error.Level): ConsoleViewContentType = when (level) {
        Error.Level.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
        Error.Level.GOAL -> ConsoleViewContentType.USER_INPUT
        Error.Level.WARNING -> ConsoleViewContentType.LOG_WARNING_OUTPUT
        Error.Level.INFO -> ConsoleViewContentType.NORMAL_OUTPUT
    }

    private inner class DocConsolePrinter(private val error: GeneralError) : LineDocVisitor() {
        private val contentType = levelToContentType(error.level)

        fun print() {
            error.getDoc(sourceInfoProvider).accept(this, true)
        }

        override fun visitHList(listDoc: HListDoc, newLine: Boolean): Void? {
            listDoc.docs.forEach { it.accept(this, false) }
            if (newLine) printNewLine()
            return null
        }

        override fun visitText(doc: TextDoc, newLine: Boolean): Void? {
            if (doc.text == "[ERROR]") {
                val sourceNode = error.cause as? Surrogate.SourceNode
                val info = sourceNode?.position?.element?.let { PsiHyperlinkInfo(it) }
                console?.printHyperlink(doc.text, info)
            } else {
                console?.print(doc.text, contentType)
            }
            if (newLine) printNewLine()
            return null
        }

        override fun visitTermLine(doc: TermLineDoc, newLine: Boolean): Void? {
            console?.print(doc.text, contentType)
            if (newLine) printNewLine()
            return null
        }

        override fun visitReference(doc: ReferenceDoc, newLine: Boolean): Void? {
            val info = (doc.reference as? PsiElement)?.let { PsiHyperlinkInfo(it) }
            console?.printHyperlink(doc.reference.name!!, info)
            if (newLine) printNewLine()
            return null
        }

        private fun printNewLine() = console?.print("\n", contentType)
    }

    private class PsiHyperlinkInfo(private val sourceElement: PsiElement) : HyperlinkInfo {
        override fun navigate(project: Project?) {
            val descriptor = EditSourceUtil.getDescriptor(sourceElement)
            if (descriptor != null && descriptor.canNavigate()) {
                descriptor.navigate(true)
            }
        }
    }
}
