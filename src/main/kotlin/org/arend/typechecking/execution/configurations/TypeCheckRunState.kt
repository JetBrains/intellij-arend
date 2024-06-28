package org.arend.typechecking.execution.configurations

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
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
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.library.BaseLibrary
import org.arend.library.error.LibraryError
import org.arend.module.error.ModuleNotFoundError
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.scope.Scope
import org.arend.settings.ArendSettings
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.DefinitionNotFoundError
import org.arend.typechecking.execution.TypeCheckCommand
import org.arend.typechecking.execution.TypeCheckProcessHandler
import org.arend.typechecking.execution.TypecheckingEventsProcessor

class TypeCheckRunState(private val environment: ExecutionEnvironment, private val command: TypeCheckCommand) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        if (environment.runnerSettings !is DebuggingRunnerData && service<ArendSettings>().typecheckingMode == ArendSettings.TypecheckingMode.SMART) {
            val tcService = environment.project.service<TypeCheckingService>()
            val modulePath = if (command.modulePath == "") null else ModulePath(command.modulePath.split('.'))
            if (command.definitionFullName != "" && modulePath == null) {
                NotificationErrorReporter(environment.project).report(DefinitionNotFoundError(command.definitionFullName))
                return null
            }

            val library = if (command.library == "") null else {
                val library = tcService.libraryManager.getRegisteredLibrary(command.library)
                if (library == null) {
                    NotificationErrorReporter(environment.project).report(LibraryError.notFound(command.library))
                    return null
                }
                if (library.isExternal || library !is BaseLibrary) {
                    NotificationErrorReporter(environment.project).report(LibraryError.incorrectLibrary(command.library))
                    return null
                }
                library
            }

            if (modulePath == null) {
                if (library == null) {
                    for (lib in tcService.libraryManager.registeredLibraries) {
                        if (!lib.isExternal) {
                            lib.reset()
                        }
                    }
                } else {
                    library.reset()
                }
            } else {
                if (command.definitionFullName == "") {
                    val group = library?.getModuleGroup(modulePath, false) ?: library?.getModuleGroup(modulePath, true)
                    if (library == null || group == null) {
                        NotificationErrorReporter(environment.project).report(ModuleNotFoundError(modulePath))
                        return null
                    }
                    library.resetGroup(group)
                } else {
                    val scope = library?.moduleScopeProvider?.forModule(modulePath) ?: library?.testsModuleScopeProvider?.forModule(modulePath)
                    if (library == null || scope == null) {
                        NotificationErrorReporter(environment.project).report(ModuleNotFoundError(modulePath))
                        return null
                    }
                    val ref = Scope.resolveName(scope, command.definitionFullName.split('.')) as? LocatedReferable
                    if (ref == null) {
                        NotificationErrorReporter(environment.project).report(DefinitionNotFoundError(command.definitionFullName, modulePath))
                        return null
                    }
                    library.resetDefinition(ref)
                }
            }

            PsiManager.getInstance(environment.project).dropPsiCaches()
            DaemonCodeAnalyzer.getInstance(environment.project).restart()
            return null
        }

        val processHandler = TypeCheckProcessHandler(environment.project.service(), command)
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        ProcessTerminatedListener.attach(processHandler)
        return DefaultExecutionResult(console, processHandler)
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
