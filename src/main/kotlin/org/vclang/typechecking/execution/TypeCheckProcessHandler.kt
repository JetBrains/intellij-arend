package org.vclang.typechecking.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
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
    private val indicator: ProgressIndicator = ProgressIndicatorBase()

    override fun startNotify() {
        super.startNotify()

        ApplicationManager.getApplication().saveAll()

        ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, object : ReadTask() {
            override fun onCanceled(indicator: ProgressIndicator) {}

            override fun computeInReadAction(indicator: ProgressIndicator) {
                try {
                    typeChecker.typeCheck(ModulePath(command.modulePath.split('.')), command.definitionFullName)
                } catch (e: ProcessCanceledException) {}
                  catch (e: Exception) {
                      Logger.getInstance(TypeCheckingService::class.java).error(e)
                  }
                  finally {
                      ApplicationManager.getApplication().executeOnPooledThread {
                          this@TypeCheckProcessHandler.destroyProcess()
                      }
                  }
            }

        })
    }

    override fun detachProcessImpl() {
        //Since we have no separate process to detach from, we simply interrupt current typechecking computation
        indicator.cancel()
        //eventsProcessor?.onFinishTesting()
        //TODO: Line below is a temporary workaround for the problem that vclang typechecker currently does not monitor the state of "indicator"
        //We execute dummy write action which terminates the computation
        ApplicationManager.getApplication().runWriteAction {}
        notifyProcessDetached()
    }

    override fun destroyProcessImpl() = notifyProcessTerminated(0)

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput(): OutputStream? = null
}

