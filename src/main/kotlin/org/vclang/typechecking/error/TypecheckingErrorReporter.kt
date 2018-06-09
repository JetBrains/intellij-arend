package org.vclang.typechecking.error

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.CompositePrintable
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.doc.*
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.typechecking.execution.ProxyAction
import org.vclang.typechecking.execution.TypecheckingEventsProcessor

private fun levelToContentType(level: Error.Level): ConsoleViewContentType = when (level) {
    Error.Level.ERROR -> ERROR_OUTPUT
    Error.Level.GOAL -> USER_INPUT
    Error.Level.WARNING -> LOG_WARNING_OUTPUT
    Error.Level.INFO -> NORMAL_OUTPUT
}

class TypecheckingErrorReporter(private val ppConfig: PrettyPrinterConfig, val eventsProcessor: TypecheckingEventsProcessor) : ErrorReporter {
    override fun report(error: GeneralError) {
        val proxyAction = object : ProxyAction {
            override fun runAction(p: SMTestProxy) {
                DocConsolePrinter(p, error).print()
            }
        }

        var reported = false
        error.affectedDefinitions.mapNotNull {
            val ref = PsiLocatedReferable.fromReferable(it)
            if (ref is PsiLocatedReferable || it is ModuleReferable) {
                reported = true
                if (ref is PsiLocatedReferable) eventsProcessor.executeProxyAction(ref.typecheckable, proxyAction)
                if (it is ModuleReferable) eventsProcessor.executeProxyAction(it, proxyAction)
            }
        }

        if (!reported) {
            eventsProcessor.executeProxyAction(proxyAction)
        }
    }

    private inner class DocConsolePrinter(
        private val proxy: SMTestProxy,
        private val error: GeneralError
    ) : LineDocVisitor() {
        private val contentType = levelToContentType(error.level)
        private val stringBuilder = StringBuilder()

        fun print() {
            error.getDoc(ppConfig).accept(this, true)
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
            val ref = (doc.reference as? DataContainer)?.data as? SmartPsiElementPointer<*> ?:
                (doc.reference as? PsiElement)?.let { SmartPointerManager.createPointer(it) }
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
                val text = stringBuilder.toString()
                proxy.addLast { it.print(text, contentType) }
                stringBuilder.setLength(0)
            }
        }

        private fun printText(text: String) {
            stringBuilder.append(text)
        }

        private fun printHyperlink(text: String, info: HyperlinkInfo?) {
            flushText()
            proxy.addLast { it.printHyperlink(text, info) }
        }

        private fun printNewLine() = printText(CompositePrintable.NEW_LINE)
    }

    private class PsiHyperlinkInfo(private val sourceElement: SmartPsiElementPointer<out PsiElement>) : HyperlinkInfo {
        override fun navigate(project: Project?) {
            val psi = runReadAction { sourceElement.element } ?: return
            val descriptor = EditSourceUtil.getDescriptor(psi)
            if (descriptor != null && descriptor.canNavigate()) {
                descriptor.navigate(true)
            }
        }
    }
}
