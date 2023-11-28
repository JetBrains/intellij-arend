package org.arend.typechecking.error

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.CompositePrintable
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.*
import com.intellij.openapi.application.runReadAction
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.*
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.EmptyScope
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.ArendReferableConverter
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.execution.ProxyAction
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.util.ArendBundle

private fun levelToContentType(level: GeneralError.Level): ConsoleViewContentType = when (level) {
    GeneralError.Level.ERROR -> ERROR_OUTPUT
    GeneralError.Level.GOAL -> USER_INPUT
    GeneralError.Level.WARNING, GeneralError.Level.WARNING_UNUSED -> LOG_WARNING_OUTPUT
    GeneralError.Level.INFO -> NORMAL_OUTPUT
}

class TypecheckingErrorReporter(private val errorService: ErrorService, private val ppConfig: PrettyPrinterConfig, val eventsProcessor: TypecheckingEventsProcessor) : ErrorReporter {
    private val errorList = ArrayList<GeneralError>()

    override fun report(error: GeneralError) {
        errorList.add(error)
        errorService.report(error)
    }

    fun flush() {
        for (error in errorList) {
            var reported = false
            error.forAffectedDefinitions { def, refError ->
                val ref = (def as? GlobalReferable)?.underlyingReferable
                if (ref is PsiLocatedReferable || ref is ModuleReferable) {
                    reported = true
                    if (ref is PsiLocatedReferable) runReadAction {
                        eventsProcessor.executeProxyAction(ref.typecheckable, ErrorProxyAction(refError))
                    }
                    if (ref is ModuleReferable) runReadAction {
                        eventsProcessor.executeProxyAction(ref, ErrorProxyAction(refError))
                    }
                }
            }

            if (!reported) {
                eventsProcessor.executeProxyAction(ErrorProxyAction(error))
            }
        }
        errorList.clear()
    }

    private inner class ErrorProxyAction(private val error: GeneralError) : ProxyAction {
        override fun runAction(p: SMTestProxy) {
            DocConsolePrinter(p, error).print()
        }
    }

    private inner class DocConsolePrinter(
        private val proxy: SMTestProxy,
        private val error: GeneralError
    ) : LineDocVisitor() {
        private val contentType = levelToContentType(error.level)
        private val stringBuilder = StringBuilder()

        fun print() {
            runReadAction {
                val scope = if (error.hasExpressions()) {
                    (error.causeSourceNode.data as? ArendCompositeElement)?.let {
                        ConvertingScope(ArendReferableConverter, it.scope)
                    }
                } else null
                error.getDoc(PrettyPrinterConfigWithRenamer(ppConfig, scope ?: EmptyScope.INSTANCE)).accept(this, true)

                mapToTypeDiffInfo(error)?.let {
                    printHyperlink(ArendBundle.message("arend.click.to.see.diff.link"), DiffHyperlinkInfo(it))
                }
            }
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

        override fun visitPattern(doc: PatternDoc, newLine: Boolean): Void? {
            printText(doc.text)
            if (newLine) printNewLine()
            return null
        }

        override fun visitReference(doc: ReferenceDoc, newLine: Boolean): Void? {
            val (hyperlink, reference) = createHyperlinkInfo(doc.reference, error)
            if (hyperlink == null) {
                printText(reference.refName)
            } else {
                printHyperlink(reference.refName, hyperlink)
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
}
