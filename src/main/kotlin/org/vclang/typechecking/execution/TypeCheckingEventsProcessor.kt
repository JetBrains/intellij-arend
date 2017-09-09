package org.vclang.typechecking.execution

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.TestProxyPrinterProvider
import com.intellij.execution.testframework.sm.runner.TestSuiteStack
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.LinkedHashSet

class TypeCheckingEventsProcessor(
    project: Project,
    private val typeCheckingRootNode: SMTestProxy.SMRootTestProxy,
    typeCheckingFrameworkName: String
) : GeneralTestEventsProcessor(project, typeCheckingFrameworkName) {
    private val definitionFullNameToProxy = mutableMapOf<String, DefinitionProxy>()
    private val suiteStack = TestSuiteStack(typeCheckingFrameworkName)
    private val currentChildren = LinkedHashSet<SMTestProxy>()
    private var getChildren = true
    private var isTypeCheckingFinished = false
    private val currentSuite: SMTestProxy
        get() {
            val currentSuite = suiteStack.currentSuite
            currentSuite?.let { return it }
            logProblem("Current suite is undefined. Root suite will be used.")
            getChildren = true
            return typeCheckingRootNode
        }

    override fun onStartTesting() {
        addToInvokeLater {
            suiteStack.pushSuite(typeCheckingRootNode)
            typeCheckingRootNode.setStarted()
            fireOnTestingStarted(typeCheckingRootNode)
        }
    }

    override fun onTestsCountInSuite(count: Int) =
        addToInvokeLater { fireOnTestsCountInSuite(count) }

    override fun onTestStarted(typeCheckingStartedEvent: TestStartedEvent) {
        addToInvokeLater {
            val fullName = checkNotNull(typeCheckingStartedEvent.name) { "Definition name is null" }
            val isConfig = typeCheckingStartedEvent.isConfig

            if (definitionFullNameToProxy.containsKey(fullName)) {
                logProblem("Type checking [$fullName] has been already started")
                if (SMTestRunnerConnectionUtil.isInDebugMode()) return@addToInvokeLater
            }

            val parentSuite = currentSuite
            val proxy = DefinitionProxy(fullName, false, null)
            proxy.isConfig = isConfig
            parentSuite.addChild(proxy)
            definitionFullNameToProxy.put(fullName, proxy)
            proxy.setStarted()
            fireOnTestStarted(proxy)
        }
    }

    override fun onTestFinished(typeCheckingFinishedEvent: TestFinishedEvent) {
        addToInvokeLater {
            val fullName = typeCheckingFinishedEvent.name
            val duration = typeCheckingFinishedEvent.duration
            val proxy = getProxyByFullName(fullName) ?: return@addToInvokeLater
            proxy.setDuration(duration ?: 0)
            proxy.setFrameworkOutputFile(typeCheckingFinishedEvent.outputFile)
            proxy.setFinished()
            definitionFullNameToProxy.remove(fullName)
            currentChildren.remove(proxy)
            fireOnTestFinished(proxy)
        }
    }

    override fun onSuiteFinished(suiteFinishedEvent: TestSuiteFinishedEvent) {
        addToInvokeLater {
            val suiteName = suiteFinishedEvent.name
            val suite = suiteStack.popSuite(suiteName)
            if (suite != null) {
                suite.setFinished()
                currentChildren.clear()
                getChildren = true
                fireOnSuiteFinished(suite)
            }
        }
    }

    override fun onTestFailure(typeCheckingFailedEvent: TestFailedEvent) {
        addToInvokeLater {
            val fullName = typeCheckingFailedEvent.name
            if (fullName == null) {
                logProblem("No definition name specified in $typeCheckingFailedEvent")
                return@addToInvokeLater
            }

            val localizedMessage = typeCheckingFailedEvent.localizedFailureMessage
            val stackTrace = typeCheckingFailedEvent.stacktrace
            val isTypeCheckingError = typeCheckingFailedEvent.isTestError
            val proxy = getProxyByFullName(fullName) ?: return@addToInvokeLater
            proxy.setTestFailed(localizedMessage, stackTrace, isTypeCheckingError)
            fireOnTestFailed(proxy)
        }
    }

    override fun onTestIgnored(typeCheckingIgnoredEvent: TestIgnoredEvent) =
        throw UnsupportedOperationException()

    override fun onTestOutput(typeCheckingOutputEvent: TestOutputEvent) =
        throw UnsupportedOperationException()

    override fun onSuiteStarted(suiteStartedEvent: TestSuiteStartedEvent) {
        addToInvokeLater {
            val suiteName = checkNotNull(suiteStartedEvent.name) { "Suite name is null" }
            val parentSuite = currentSuite
            val newSuite = DefinitionProxy(
                suiteName,
                true,
                null,
                parentSuite.isPreservePresentableName
            )
            parentSuite.addChild(newSuite)
            getChildren = true
            suiteStack.pushSuite(newSuite)
            newSuite.setSuiteStarted()
            fireOnSuiteStarted(newSuite)
        }
    }

    override fun onUncapturedOutput(text: String, outputType: Key<*>) {
    }

    override fun onError(localizedMessage: String, stackTrace: String?, isCritical: Boolean) {
    }

    override fun onFinishTesting() {
        addToInvokeLater {
            if (isTypeCheckingFinished) return@addToInvokeLater
            isTypeCheckingFinished = true

            val isTreeNotComplete = !GeneralTestEventsProcessor.isTreeComplete(
                definitionFullNameToProxy.keys,
                typeCheckingRootNode
            )
            if (isTreeNotComplete) {
                typeCheckingRootNode.setTerminated()
                definitionFullNameToProxy.clear()
            }

            suiteStack.clear()
            typeCheckingRootNode.setFinished()
            fireOnTestingFinished(typeCheckingRootNode)
        }
        stopEventProcessing()
    }

    override fun onTestsReporterAttached() {
        addToInvokeLater {
            GeneralTestEventsProcessor.fireOnTestsReporterAttached(typeCheckingRootNode)
        }
    }

    override fun setLocator(locator: SMTestLocator) = throw UnsupportedOperationException()

    override fun setPrinterProvider(printerProvider: TestProxyPrinterProvider)
        = throw UnsupportedOperationException()

    override fun dispose() {
        super.dispose()
        addToInvokeLater {
            disconnectListeners()
            if (!definitionFullNameToProxy.isEmpty()) {
                val application = ApplicationManager.getApplication()
                if (!application.isHeadlessEnvironment && !application.isUnitTestMode) {
                    logProblem("Not all events were processed!")
                }
            }
            definitionFullNameToProxy.clear()
            suiteStack.clear()
        }
    }

    fun getProxyByFullName(fullDefinitionName: String?): DefinitionProxy? =
        definitionFullNameToProxy[fullDefinitionName]
}
