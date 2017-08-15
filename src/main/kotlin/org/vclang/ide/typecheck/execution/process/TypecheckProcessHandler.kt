package org.vclang.ide.typecheck.execution.process

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import org.vclang.ide.typecheck.TypecheckEventsProcessor
import org.vclang.ide.typecheck.TypecheckerFrontend
import org.vclang.ide.typecheck.execution.TypecheckCommand
import java.io.OutputStream
import java.nio.file.Paths

class TypecheckProcessHandler(
        private val typechecker: TypecheckerFrontend,
        private val command: TypecheckCommand
) : ProcessHandler() {
    var console: ConsoleView?
        get() = typechecker.console
        set(value) { typechecker.console = value }
    var eventsProcessor: TypecheckEventsProcessor?
        get() = typechecker.eventsProcessor
        set(value) { typechecker.eventsProcessor = value }

    override fun startNotify() {
        super.startNotify()
        ApplicationManager.getApplication().runReadAction {
            val modulePath = typechecker.sourceRootPath.relativize(Paths.get(command.modulePath))
            var exitCode = 0
            try {
                typechecker.typecheck(modulePath, command.definitionName)
                exitCode = if (typechecker.hasErrors) 1 else 0
            } catch (e: Exception) {
                e.message?.let { console?.print(it, ConsoleViewContentType.ERROR_OUTPUT) }
                exitCode = 1
            } finally {
                notifyProcessTerminated(exitCode)
            }
        }
    }

    override fun detachProcessImpl() = notifyProcessDetached()

    override fun destroyProcessImpl() = notifyProcessTerminated(0)

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput(): OutputStream? = null
}
