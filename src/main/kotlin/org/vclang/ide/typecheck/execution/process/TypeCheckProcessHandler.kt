package org.vclang.ide.typecheck.execution.process

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import org.vclang.ide.typecheck.TypeCheckingEventsProcessor
import org.vclang.ide.typecheck.TypeCheckingService
import org.vclang.ide.typecheck.execution.TypeCheckCommand
import java.io.OutputStream
import java.nio.file.Paths

class TypeCheckProcessHandler(
        private val typeChecker: TypeCheckingService,
        private val command: TypeCheckCommand
) : ProcessHandler() {
    var console: ConsoleView?
        get() = typeChecker.console
        set(value) {
            typeChecker.console = value
        }
    var eventsProcessor: TypeCheckingEventsProcessor?
        get() = typeChecker.eventsProcessor
        set(value) {
            typeChecker.eventsProcessor = value
        }

    override fun startNotify() {
        super.startNotify()
        ApplicationManager.getApplication().runReadAction {
            try {
                typeChecker.typeCheck(Paths.get(command.modulePath), command.definitionFullName)
            } catch (e: Exception) {
                e.message?.let { console?.print(it, ConsoleViewContentType.ERROR_OUTPUT) }
            } finally {
                notifyProcessTerminated(0)
            }
        }
    }

    override fun detachProcessImpl() = notifyProcessDetached()

    override fun destroyProcessImpl() = notifyProcessTerminated(0)

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput(): OutputStream? = null
}
