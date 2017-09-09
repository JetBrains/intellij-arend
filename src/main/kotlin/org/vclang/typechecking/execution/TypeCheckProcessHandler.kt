package org.vclang.typechecking.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import org.vclang.typechecking.TypeCheckingService
import java.io.OutputStream
import java.nio.file.Paths


class TypeCheckProcessHandler(
        private val typeChecker: TypeCheckingService,
        private val command: TypeCheckCommand
) : ProcessHandler() {
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
                e.printStackTrace()
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
