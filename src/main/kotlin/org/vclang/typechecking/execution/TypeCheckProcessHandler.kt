package org.vclang.typechecking.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.ReadTask
import com.jetbrains.jetpad.vclang.module.ModulePath
import org.vclang.typechecking.TypeCheckingService
import java.io.OutputStream


class TypeCheckProcessHandler(
        private val typeChecker: TypeCheckingService,
        private val command: TypeCheckCommand
) : ProcessHandler() {
    var eventsProcessor: TypecheckingEventsProcessor?
        get() = typeChecker.eventsProcessor
        set(value) {
            typeChecker.eventsProcessor = value
        }

    override fun startNotify() {
        super.startNotify()

        ApplicationManager.getApplication().saveAll()

        ProgressIndicatorUtils.scheduleWithWriteActionPriority(object : ReadTask(){
            override fun onCanceled(indicator: ProgressIndicator) {
                this@TypeCheckProcessHandler.destroyProcess()
            }

            override fun computeInReadAction(indicator: ProgressIndicator) {
                ApplicationManager.getApplication().runReadAction {
                    try {
                        typeChecker.typeCheck(ModulePath(command.modulePath.split('.')), command.definitionFullName)
                    } catch (e: ProcessCanceledException) {

                    } catch (e: Exception) {
                        Logger.getInstance(TypeCheckingService::class.java).error(e)
                    } finally {
                        this@TypeCheckProcessHandler.destroyProcess()
                    }
                }
            }

        })
    }

    override fun detachProcessImpl() = notifyProcessDetached()

    override fun destroyProcessImpl() = notifyProcessTerminated(0)

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput(): OutputStream? = null
}
