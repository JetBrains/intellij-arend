package org.vclang.typechecking.execution

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.*
import com.intellij.execution.testframework.sm.runner.events.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.vclang.psi.VcFile
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName

class TypecheckingEventsProcessor(
    project: Project,
    private val typeCheckingRootNode: SMTestProxy.SMRootTestProxy,
    typeCheckingFrameworkName: String
) : GeneralTestEventsProcessor(project, typeCheckingFrameworkName) {
    private val definitionToProxy = mutableMapOf<PsiGlobalReferable, DefinitionProxy>()
    private val fileToProxy = mutableMapOf<VcFile, SMTestProxy>()
    private var isTypeCheckingFinished = false

    override fun onStartTesting() {
        addToInvokeLater {
            typeCheckingRootNode.setStarted()
            fireOnTestingStarted(typeCheckingRootNode)
        }
    }

    override fun onFinishTesting() {
        addToInvokeLater {
            if (isTypeCheckingFinished) return@addToInvokeLater
            isTypeCheckingFinished = true

            val isTreeNotComplete = !GeneralTestEventsProcessor.isTreeComplete(
                definitionToProxy.keys,
                typeCheckingRootNode
            )
            if (isTreeNotComplete) {
                typeCheckingRootNode.setTerminated()
                definitionToProxy.clear()
            }

            fileToProxy.clear()
            typeCheckingRootNode.setFinished()
            fireOnTestingFinished(typeCheckingRootNode)
        }
        stopEventProcessing()
    }

    fun onSuiteStarted(file: VcFile) {
        addToInvokeLater {
            if (!fileToProxy.containsKey(file)) {
                val parentSuite = typeCheckingRootNode
                val newSuite = DefinitionProxy(
                        file.textRepresentation(),
                        true,
                        null,
                        parentSuite.isPreservePresentableName
                )
                parentSuite.addChild(newSuite)
                fileToProxy.put(file, newSuite)
                newSuite.setSuiteStarted()
                fireOnSuiteStarted(newSuite)
            }
        }
    }

    fun onSuitesFinished() {
        addToInvokeLater {
            for (suite in fileToProxy.values) {
                suite.setFinished()
                fireOnSuiteFinished(suite)
            }
            fileToProxy.clear()
        }
    }

    fun onTestStarted(ref: PsiGlobalReferable) {
        addToInvokeLater {
            val file = ref.containingFile as? VcFile
            if (file != null) onSuiteStarted(file)

            val fullName = ref.fullName
            if (definitionToProxy.containsKey(ref)) {
                logProblem("Type checking [$fullName] has been already started")
                if (SMTestRunnerConnectionUtil.isInDebugMode()) return@addToInvokeLater
            }

            val parentSuite = file?.let { fileToProxy[it] } ?: typeCheckingRootNode
            val proxy = DefinitionProxy(fullName, false, null)
            parentSuite.addChild(proxy)
            definitionToProxy.put(ref, proxy)
            proxy.setStarted()
            fireOnTestStarted(proxy)
        }
    }

    fun onTestFailure(ref: PsiGlobalReferable) {
        addToInvokeLater {
            val proxy = definitionToProxy[ref] ?: return@addToInvokeLater
            proxy.setTestFailed("", null, proxy.hasErrors())
            fireOnTestFailed(proxy)
        }
    }

    fun onTestFinished(ref: PsiGlobalReferable) {
        addToInvokeLater {
            val proxy = definitionToProxy[ref] ?: return@addToInvokeLater
            proxy.setFinished()
            definitionToProxy.remove(ref)
            fireOnTestFinished(proxy)
        }
    }

    override fun onUncapturedOutput(text: String, outputType: Key<*>) {
    }

    override fun onError(localizedMessage: String, stackTrace: String?, isCritical: Boolean) {
    }

    override fun onTestsCountInSuite(count: Int) =
        addToInvokeLater { fireOnTestsCountInSuite(count) }

    override fun onTestsReporterAttached() {
        addToInvokeLater {
            GeneralTestEventsProcessor.fireOnTestsReporterAttached(typeCheckingRootNode)
        }
    }

    override fun dispose() {
        super.dispose()
        addToInvokeLater {
            disconnectListeners()
            if (!definitionToProxy.isEmpty()) {
                val application = ApplicationManager.getApplication()
                if (!application.isHeadlessEnvironment && !application.isUnitTestMode) {
                    logProblem("Not all events were processed!")
                }
            }
            definitionToProxy.clear()
            fileToProxy.clear()
        }
    }

    fun getProxyByFullName(ref: PsiGlobalReferable): DefinitionProxy? =
        definitionToProxy[ref]

    override fun onSuiteStarted(suiteStartedEvent: TestSuiteStartedEvent) =
        throw UnsupportedOperationException()

    override fun onSuiteFinished(suiteFinishedEvent: TestSuiteFinishedEvent) =
        throw UnsupportedOperationException()

    override fun onTestStarted(typeCheckingStartedEvent: TestStartedEvent) =
        throw UnsupportedOperationException()

    override fun onTestFailure(typeCheckingFailedEvent: TestFailedEvent) =
        throw UnsupportedOperationException()

    override fun onTestFinished(typeCheckingFinishedEvent: TestFinishedEvent) =
        throw UnsupportedOperationException()

    override fun onTestIgnored(typeCheckingIgnoredEvent: TestIgnoredEvent) =
        throw UnsupportedOperationException()

    override fun onTestOutput(typeCheckingOutputEvent: TestOutputEvent) =
        throw UnsupportedOperationException()

    override fun setLocator(locator: SMTestLocator) =
        throw UnsupportedOperationException()

    override fun setPrinterProvider(printerProvider: TestProxyPrinterProvider) =
        throw UnsupportedOperationException()
}
