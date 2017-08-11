package org.vclang.ide.typecheck.execution.configurations

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.vfs.LocalFileSystem
import org.vclang.ide.typecheck.TypecheckerFrontend
import org.vclang.ide.typecheck.execution.TypecheckCommand
import org.vclang.ide.typecheck.execution.process.TypecheckProcessHandler
import org.vclang.lang.core.getPsiFor
import org.vclang.lang.core.psi.contentRoot
import org.vclang.lang.core.psi.sourceRoot
import java.nio.file.Paths

class TypecheckRunState(
        environment: ExecutionEnvironment,
        private val command: TypecheckCommand
) : CommandLineState(environment) {

    override fun startProcess(): TypecheckProcessHandler {
        val project = environment.project
        val moduleFile = LocalFileSystem.getInstance().findFileByPath(command.modulePath)
        val modulePsi = project.getPsiFor(moduleFile)
        val sourceRootFile = modulePsi?.sourceRoot
                ?: modulePsi?.contentRoot
                ?: throw IllegalStateException()
        val sourceRoot = Paths.get(sourceRootFile.path)
        val frontend = TypecheckerFrontend(project, sourceRoot)
        return TypecheckProcessHandler(frontend, command)
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        processHandler.console = createConsole(executor)
        processHandler.console?.attachToProcess(processHandler)
        ProcessTerminatedListener.attach(processHandler)
        return DefaultExecutionResult(
                processHandler.console,
                processHandler,
                *createActions(processHandler.console, processHandler, executor)
        )
    }

    override fun createConsole(executor: Executor): ConsoleView? {
        val runConfiguration = environment.runnerAndConfigurationSettings?.configuration
        runConfiguration ?: return null
        val testFrameworkName = "Vclang Typechecker"
        val properties = SMTRunnerConsoleProperties(runConfiguration, testFrameworkName, executor)
        return SMTestRunnerConnectionUtil.createConsole(testFrameworkName, properties)
    }
}
