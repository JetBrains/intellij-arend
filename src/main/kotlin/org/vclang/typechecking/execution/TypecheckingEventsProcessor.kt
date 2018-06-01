package org.vclang.typechecking.execution

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.TestProxyPrinterProvider
import com.intellij.execution.testframework.sm.runner.events.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import org.vclang.psi.VcFile
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.fullName

interface ProxyAction {
    fun runAction(p : SMTestProxy)
}

class TypecheckingEventsProcessor(project: Project, typeCheckingRootNode: SMTestProxy.SMRootTestProxy, typeCheckingFrameworkName: String)
    : GeneralTestEventsProcessor(project, typeCheckingFrameworkName, typeCheckingRootNode) {
    private val definitionToProxy = mutableMapOf<PsiLocatedReferable, DefinitionProxy>()
    private val fileToProxy = mutableMapOf<ModulePath, DefinitionProxy>()
    private val deferredActions = mutableMapOf<LocatedReferable, MutableList<ProxyAction>>()
    private var isTypeCheckingFinished = false
    private val testsDuration = mutableMapOf<PsiLocatedReferable, Long>()

    fun startTimer(ref: PsiLocatedReferable) {
        testsDuration.compute(ref, { _, time -> System.currentTimeMillis() + (time ?: 0) })
    }

    fun stopTimer(ref: PsiLocatedReferable) {
        testsDuration.compute(ref, { _, time -> time?.let { System.currentTimeMillis() - it } })
    }

    override fun onStartTesting() {
        addToInvokeLater {
            myTestsRootProxy.setStarted()
            fireOnTestingStarted(myTestsRootProxy)
        }
    }

    override fun onFinishTesting() {
        addToInvokeLater {
            if (isTypeCheckingFinished) return@addToInvokeLater
            isTypeCheckingFinished = true

            val isTreeNotComplete = !GeneralTestEventsProcessor.isTreeComplete(
                definitionToProxy.keys,
                myTestsRootProxy
            )
            if (isTreeNotComplete) {
                myTestsRootProxy.setTerminated()
                //definitionToProxy.clear()
            }

            //fileToProxy.clear()
            myTestsRootProxy.setFinished()
            fireOnTestingFinished(myTestsRootProxy)
        }
        stopEventProcessing()
    }

    fun onSuiteStarted(modulePath: ModulePath) {
        addToInvokeLater {
            if (!fileToProxy.containsKey(modulePath)) {
                val parentSuite = myTestsRootProxy
                val newSuite = DefinitionProxy(
                        modulePath.toString(),
                        true,
                        null,
                        parentSuite.isPreservePresentableName,
                        null
                )
                parentSuite.addChild(newSuite)
                fileToProxy[modulePath] = newSuite

                if (!isTypeCheckingFinished) {
                    newSuite.setSuiteStarted()
                } else {
                    newSuite.setTerminated()
                }

                fireOnSuiteStarted(newSuite)

                val actions = deferredActions.remove(ModuleReferable(modulePath))
                if (actions != null) {
                    for (action in actions) {
                        action.runAction(newSuite)
                    }
                }
            }
        }
    }

    fun onSuiteFailure(modulePath: ModulePath) {
        addToInvokeLater {
            val proxy = fileToProxy[modulePath] ?: return@addToInvokeLater
            proxy.setTestFailed("", null, proxy.hasErrors())
            fireOnTestFailed(proxy)
        }
    }

    fun onSuitesFinished() {
        addToInvokeLater {
            for (ref in ArrayList(deferredActions.keys)) {
                if (ref is ModuleReferable) {
                    onSuiteStarted(ref.path)
                } else if (ref is PsiLocatedReferable) {
                    onTestStarted(ref)
                    onTestFinished(ref)
                }
            }
            for (suite in fileToProxy.values) {
                suite.setFinished()
                fireOnSuiteFinished(suite)
            }

            deferredActions.clear()
            testsDuration.clear()
            fileToProxy.clear()
            definitionToProxy.clear()
        }
    }

    fun onTestStarted(ref: PsiLocatedReferable) {
        addToInvokeLater {
            synchronized(this@TypecheckingEventsProcessor, {
                val modulePath = (ref.containingFile as? VcFile)?.modulePath
                if (modulePath != null) onSuiteStarted(modulePath)

                val fullName = ref.fullName
                if (definitionToProxy.containsKey(ref)) {
                    logProblem("Type checking [$fullName] has been already started")
                    if (SMTestRunnerConnectionUtil.isInDebugMode()) return@addToInvokeLater
                }

                val parentSuite = modulePath?.let { fileToProxy[it] } ?: myTestsRootProxy
                val proxy = DefinitionProxy(fullName, false, null, true, ref)
                parentSuite.addChild(proxy)
                definitionToProxy[ref] = proxy

                val da = deferredActions.remove(ref)
                if (da != null) for (a in da) a.runAction(proxy)

                if (!isTypeCheckingFinished) {
                    proxy.setStarted()
                } else {
                    proxy.setTerminated()
                }

                fireOnTestStarted(proxy)
            })
        }
    }

    fun onTestFailure(ref: PsiLocatedReferable) {
        addToInvokeLater {
            val proxy = definitionToProxy[ref] ?: return@addToInvokeLater
            proxy.setTestFailed("", null, proxy.hasErrors())
            fireOnTestFailed(proxy)
        }
    }

    fun onTestFinished(ref: PsiLocatedReferable) {
        addToInvokeLater {
            val proxy = definitionToProxy[ref] ?: return@addToInvokeLater
            testsDuration[ref]?.let { proxy.setDuration(it) }
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
            GeneralTestEventsProcessor.fireOnTestsReporterAttached(myTestsRootProxy)
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

    // Allows executing/scheduling actions for proxies which need not even exist at the time this routine is invoked
    fun executeProxyAction(ref: PsiLocatedReferable, action: ProxyAction) {
        synchronized(this, {
            val p = definitionToProxy[ref]
            if (p != null) {
                action.runAction(p)
            } else {
                var actions = deferredActions[ref]
                if (actions == null) actions = mutableListOf()
                actions.add(action)
                deferredActions[ref] = actions
            }
        })
    }

    fun executeProxyAction(ref: ModuleReferable, action: ProxyAction) {
        synchronized(this, {
            val p = fileToProxy[ref.path]
            if (p != null) {
                action.runAction(p)
            } else {
                var actions = deferredActions[ref]
                if (actions == null) actions = mutableListOf()
                actions.add(action)
                deferredActions[ref] = actions
            }
        })
    }

    fun executeProxyAction(action: ProxyAction) {
        synchronized(this, {
            action.runAction(myTestsRootProxy)
        })
    }


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
