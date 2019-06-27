package org.arend.typechecking.error

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
import org.arend.error.*
import org.arend.error.doc.*
import org.arend.highlight.BasePass
import org.arend.naming.reference.DataContainer
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.Referable
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.ProxyAction
import org.arend.typechecking.execution.TypecheckingEventsProcessor

private fun levelToContentType(level: Error.Level): ConsoleViewContentType = when (level) {
    Error.Level.ERROR -> ERROR_OUTPUT
    Error.Level.GOAL -> USER_INPUT
    Error.Level.WARNING -> LOG_WARNING_OUTPUT
    Error.Level.INFO -> NORMAL_OUTPUT
}

class TypecheckingErrorReporter(private val typeCheckingService: TypeCheckingService, private val ppConfig: PrettyPrinterConfig, val eventsProcessor: TypecheckingEventsProcessor) : ErrorReporter {
    private val errorList = ArrayList<GeneralError>()

    override fun report(error: GeneralError) {
        errorList.add(error)
        typeCheckingService.reportError(error)
    }

    fun flush() {
        for (error in errorList) {
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
                    if (ref is PsiLocatedReferable) runReadAction {
                        eventsProcessor.executeProxyAction(ref.typecheckable, proxyAction)
                    }
                    if (it is ModuleReferable) runReadAction {
                        eventsProcessor.executeProxyAction(it, proxyAction)
                    }
                }
            }

            if (!reported) {
                eventsProcessor.executeProxyAction(proxyAction)
            }
        }
        errorList.clear()
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

        private fun replaceReference(ref: Referable): Referable {
            if (ref is SourceInfoReference) {
                val newCause = BasePass.getImprovedCause(error)
                if (newCause != null) {
                    return SourceInfoReference(newCause as? SourceInfo ?: PsiSourceInfo(runReadAction { SmartPointerManager.createPointer(newCause) }))
                }
            }
            return ref
        }

        override fun visitReference(doc: ReferenceDoc, newLine: Boolean): Void? {
            val reference = replaceReference(doc.reference)
            val data = (reference as? DataContainer)?.data
            val ref = data as? SmartPsiElementPointer<*> ?:
                (data as? PsiElement ?: reference as? PsiElement)?.let { runReadAction { SmartPointerManager.createPointer(it) } }
            if (ref == null) {
                printText(reference.textRepresentation())
            } else {
                printHyperlink(reference.textRepresentation(), PsiHyperlinkInfo(ref))
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
