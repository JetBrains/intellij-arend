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
import org.vclang.ide.typecheck.TypeCheckingEventsProcessor
import org.vclang.ide.typecheck.TypeCheckingService
import org.vclang.ide.typecheck.execution.TypeCheckCommand
import org.vclang.ide.typecheck.execution.process.TypeCheckProcessHandler

class TypeCheckRunState(
        environment: ExecutionEnvironment,
        private val command: TypeCheckCommand
) : CommandLineState(environment) {

    override fun startProcess(): TypeCheckProcessHandler {
        val service = TypeCheckingService.getInstance(environment.project)
        return TypeCheckProcessHandler(service, command)
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
            ?: return null
        val testFrameworkName = "VclangTypeCheckRunner"
        val consoleProperties = SMTRunnerConsoleProperties(
            runConfiguration,
            testFrameworkName,
            executor
        )

        val splitterPropertyName = "$testFrameworkName.Splitter.Proportion"
        val consoleView = SMTRunnerConsoleView(consoleProperties, splitterPropertyName)
        initConsoleView(consoleView, testFrameworkName)

        return consoleView
    }

    private fun initConsoleView(consoleView: SMTRunnerConsoleView, testFrameworkName: String) {
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
        if (processHandler !is TypeCheckProcessHandler) error("Invalid process handler")

        val eventsProcessor = TypeCheckingEventsProcessor(
                consoleProperties.project,
                resultsViewer.testsRootNode,
                testFrameworkName
        )
        eventsProcessor.addEventsListener(resultsViewer)
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
