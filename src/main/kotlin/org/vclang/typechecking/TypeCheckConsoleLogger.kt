package org.vclang.typechecking

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.CompositePrintable
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.LOG_WARNING_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.USER_INPUT
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
import com.jetbrains.jetpad.vclang.term.provider.PrettyPrinterInfoProvider
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
import org.vclang.psi.parentOfType
import org.vclang.typechecking.execution.DefinitionProxy
import org.vclang.typechecking.execution.TypeCheckingEventsProcessor

class TypeCheckConsoleLogger(
        private val sourceInfoProvider: PrettyPrinterInfoProvider
) : ErrorReporter {
    var eventsProcessor: TypeCheckingEventsProcessor? = null

    override fun report(error: GeneralError) {
        val element = error.cause as? PsiElement
        val definition = element?.parentOfType<PsiGlobalReferable>(false) ?: element?.containingFile as? PsiGlobalReferable ?: return
        val proxy = eventsProcessor?.getProxyByFullName(definition.fullName) ?: return
        DocConsolePrinter(proxy, error).print()
    }

    companion object {
        private fun levelToContentType(level: Error.Level): ConsoleViewContentType = when (level) {
            Error.Level.ERROR -> ERROR_OUTPUT
            Error.Level.GOAL -> USER_INPUT
            Error.Level.WARNING -> LOG_WARNING_OUTPUT
            Error.Level.INFO -> NORMAL_OUTPUT
        }
    }

    private inner class DocConsolePrinter(
        private val proxy: DefinitionProxy,
        private val error: GeneralError
    ) : LineDocVisitor() {
        private val contentType = levelToContentType(error.level)

        fun print() {
            error.getDoc(sourceInfoProvider).accept(this, true)
            if (contentType in setOf(ERROR_OUTPUT, LOG_WARNING_OUTPUT)) {
                proxy.addError("", null, true)
            }
        }

        override fun visitHList(listDoc: HListDoc, newLine: Boolean): Void? {
            listDoc.docs.forEach { it.accept(this, false) }
            if (newLine) printNewLine()
            return null
        }

        override fun visitText(doc: TextDoc, newLine: Boolean): Void? {
            if (doc.text.startsWith('[') && doc.text.endsWith(']')) {
                val element = error.cause as? PsiElement
                val info = element?.let { PsiHyperlinkInfo(it) }
                printHyperlink(doc.text, info)
            } else {
                printText(doc.text)
            }
            if (newLine) printNewLine()
            return null
        }

        override fun visitTermLine(doc: TermLineDoc, newLine: Boolean): Void? {
            printText(doc.text)
            if (newLine) printNewLine()
            return null
        }

        override fun visitReference(doc: ReferenceDoc, newLine: Boolean): Void? {
            val info = (doc.reference as? PsiElement)?.let { PsiHyperlinkInfo(it) }
            printHyperlink(doc.reference.textRepresentation(), info)
            if (newLine) printNewLine()
            return null
        }

        private fun printText(text: String) {
            proxy.addText(text, contentType)
        }

        private fun printHyperlink(text: String, info: HyperlinkInfo?) {
            proxy.addHyperlink(text, info)
        }

        private fun printNewLine() = printText(CompositePrintable.NEW_LINE)
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
