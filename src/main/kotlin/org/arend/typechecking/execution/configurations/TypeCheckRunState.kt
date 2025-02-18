package org.arend.typechecking.execution.configurations

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.RunProfileState
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
import com.intellij.openapi.components.service
import org.arend.ext.module.ModulePath
import org.arend.library.error.LibraryError
import org.arend.module.ModuleLocation
import org.arend.server.ArendServerService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.DefinitionNotFoundError
import org.arend.typechecking.execution.TypeCheckCommand
import org.arend.typechecking.execution.TypeCheckProcessHandler
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.typechecking.runner.RunnerService

class TypeCheckRunState(private val environment: ExecutionEnvironment, private val command: TypeCheckCommand) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        if (environment.runnerSettings !is DebuggingRunnerData) {
            val server = environment.project.service<ArendServerService>().server
            val modulePath = if (command.modulePath == "") null else ModulePath(command.modulePath.split('.'))
            if (command.definitionFullName != "" && modulePath == null) {
                NotificationErrorReporter(environment.project).report(DefinitionNotFoundError(command.definitionFullName))
                return null
            }

            val library = if (command.library == "") null else {
                if (server.getLibrary(command.library) != null) {
                    command.library
                } else {
                    NotificationErrorReporter(environment.project).report(LibraryError.notFound(command.library))
                    null
                }
            }

            environment.project.service<RunnerService>().runChecker(library, command.isTest, if (modulePath != null && library != null) ModuleLocation(library, if (command.isTest) ModuleLocation.LocationKind.TEST else ModuleLocation.LocationKind.SOURCE, modulePath) else null, command.definitionFullName.ifEmpty { null })
            return null
        } else {
            val processHandler = TypeCheckProcessHandler(environment.project, command)
            val console = createConsole(executor)
            console?.attachToProcess(processHandler)
            ProcessTerminatedListener.attach(processHandler)
            return DefaultExecutionResult(console, processHandler)
        }
    }

    private fun createConsole(executor: Executor): ConsoleView? {
        val runConfiguration = environment.runnerAndConfigurationSettings?.configuration ?: return null
        val testFrameworkName = "ArendTypeCheckRunner"
        val consoleProperties = SMTRunnerConsoleProperties(runConfiguration, testFrameworkName, executor)

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

        val eventsProcessor = TypecheckingEventsProcessor(
                consoleProperties.project,
                resultsViewer.testsRootNode,
                testFrameworkName
        )
        eventsProcessor.addEventsListener(resultsViewer)
        processHandler.eventsProcessor = eventsProcessor

        val uiActionsHandler = SMTRunnerUIActionsHandler(consoleProperties)
        resultsViewer.addEventsListener(uiActionsHandler)

        processHandler.addProcessListener(object : ProcessAdapter() {

            override fun processTerminated(event: ProcessEvent) {
                eventsProcessor.onFinishTesting()
            }

            override fun startNotified(event: ProcessEvent) = eventsProcessor.onStartTesting()
        })
    }
}
