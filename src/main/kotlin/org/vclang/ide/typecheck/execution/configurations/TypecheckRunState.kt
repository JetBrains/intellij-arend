package org.vclang.ide.typecheck.execution.configurations

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import org.vclang.ide.typecheck.TypecheckEventsProcessor
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
        val runConfiguration = environment.runnerAndConfigurationSettings?.configuration ?: return null
        val testFrameworkName = "VclangTypecheckRunner"
        val consoleProperties = SMTRunnerConsoleProperties(runConfiguration, testFrameworkName, executor)

        val splitterPropertyName = "$testFrameworkName.Splitter.Proportion"
        val consoleView = SMTRunnerConsoleView(consoleProperties, splitterPropertyName)
        initConsoleView(consoleView, testFrameworkName)

        return consoleView
    }

    fun initConsoleView(consoleView: SMTRunnerConsoleView, testFrameworkName: String) {
        consoleView.addAttachToProcessListener { processHandler ->
            attachEventsProcessors(
                consoleView.properties,
                consoleView.resultsViewer,
                processHandler,
                testFrameworkName
            )
        }
        consoleView.setHelpId("reference.runToolWindow.testResultsTab")
        consoleView.initUI()
    }

    private fun attachEventsProcessors(
        consoleProperties: TestConsoleProperties,
        resultsViewer: SMTestRunnerResultsForm,
        processHandler: ProcessHandler,
        testFrameworkName: String
    ) {
        val eventsProcessor = TypecheckEventsProcessor(
            consoleProperties.project,
            resultsViewer.testsRootNode,
            testFrameworkName
        )
        eventsProcessor.addEventsListener(resultsViewer)
        if (processHandler !is TypecheckProcessHandler) throw IllegalStateException()
        processHandler.eventsProcessor = eventsProcessor

        val uiActionsHandler = SMTRunnerUIActionsHandler(consoleProperties)
        resultsViewer.addEventsListener(uiActionsHandler)

        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun processTerminated(event: ProcessEvent?) {
                eventsProcessor.onFinishTesting()
                Disposer.dispose(eventsProcessor)
            }

            override fun startNotified(event: ProcessEvent?) = eventsProcessor.onStartTesting()
        })
    }
}
