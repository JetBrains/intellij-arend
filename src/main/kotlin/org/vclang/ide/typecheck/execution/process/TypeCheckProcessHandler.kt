package org.vclang.ide.typecheck.execution.process

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.vclang.ide.typecheck.TypeCheckingEventsProcessor
import org.vclang.ide.typecheck.TypeCheckingService
import org.vclang.ide.typecheck.execution.TypeCheckCommand
import org.vclang.ide.typecheck.relativeToSource
import java.io.OutputStream
import java.nio.file.Paths

class TypeCheckProcessHandler(
        private val project: Project,
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
            val modulePath = Paths.get(command.modulePath).relativeToSource(project)
            var exitCode = 0
            try {
                typeChecker.typeCheck(modulePath, command.definitionFullName)
                exitCode = if (typeChecker.hasErrors) 1 else 0
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
