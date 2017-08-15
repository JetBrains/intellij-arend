package org.vclang.ide.typecheck

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.error.Error
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.doc.DocStringBuilder
import com.jetbrains.jetpad.vclang.term.SourceInfoProvider
import org.vclang.lang.core.Surrogate

class TypecheckConsoleLogger(
        private val sourceInfoProvider: SourceInfoProvider<VcSourceIdT>
) : ErrorReporter {
    var console: ConsoleView? = null
    var hasErrors = false

    override fun report(error: GeneralError) {
        hasErrors = true
        val message = DocStringBuilder.build(error.getDoc(sourceInfoProvider))
        console?.print(message + '\n', levelToContentType(error.level))
        val sourceNode = error.cause as? Surrogate.SourceNode
        val info = sourceNode?.position?.element?.let { PsiHyperlinkInfo(it) }
        if (info != null) {
            console?.printHyperlink("Link\n", info)
        }
    }

    fun reportInfo(message: String) {
        console?.print(message + '\n', ConsoleViewContentType.NORMAL_OUTPUT)
    }

    fun reportWarning(message: String) {
        console?.print(message + '\n', ConsoleViewContentType.LOG_WARNING_OUTPUT)
    }

    fun reportError(message: String) {
        console?.print(message + '\n', ConsoleViewContentType.ERROR_OUTPUT)
    }

    private fun levelToContentType(level: Error.Level): ConsoleViewContentType = when (level) {
        Error.Level.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
        Error.Level.GOAL -> ConsoleViewContentType.USER_INPUT
        Error.Level.WARNING -> ConsoleViewContentType.LOG_WARNING_OUTPUT
        Error.Level.INFO -> ConsoleViewContentType.NORMAL_OUTPUT
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
