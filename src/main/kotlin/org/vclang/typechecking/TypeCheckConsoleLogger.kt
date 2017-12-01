package org.vclang.typechecking

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.CompositePrintable
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.SourceInfoReference
import com.jetbrains.jetpad.vclang.error.doc.*
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.typechecking.execution.DefinitionProxy
import org.vclang.typechecking.execution.TypecheckingEventsProcessor

class TypeCheckConsoleLogger(
        private val sourceInfoProvider: PrettyPrinterConfig
) : ErrorReporter {
    var eventsProcessor: TypecheckingEventsProcessor? = null

    override fun report(error: GeneralError) {
        error.affectedDefinitions
            .mapNotNull { if (it is PsiGlobalReferable) eventsProcessor?.getProxyByFullName(it) else null }
            .forEach { DocConsolePrinter(it, error).print() }
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
        private val stringBuilder = StringBuilder()

        fun print() {
            error.getDoc(sourceInfoProvider).accept(this, true)
            printNewLine()
            flushText()
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
            printText(doc.text)
            if (newLine) printNewLine()
            return null
        }

        override fun visitTermLine(doc: TermLineDoc, newLine: Boolean): Void? {
            printText(doc.text)
            if (newLine) printNewLine()
            return null
        }

        override fun visitReference(doc: ReferenceDoc, newLine: Boolean): Void? {
            val ref = ((doc.reference as? SourceInfoReference)?.sourceInfo ?: doc.reference) as? PsiElement
            if (ref == null) {
                printText(doc.reference.textRepresentation())
            } else {
                printHyperlink(doc.reference.textRepresentation(), PsiHyperlinkInfo(ref))
            }
            if (newLine) printNewLine()
            return null
        }

        private fun flushText() {
            if (stringBuilder.isNotEmpty()) {
                proxy.addText(stringBuilder.toString(), contentType)
                stringBuilder.setLength(0)
            }
        }

        private fun printText(text: String) {
            stringBuilder.append(text)
        }

        private fun printHyperlink(text: String, info: HyperlinkInfo?) {
            flushText()
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
