package org.vclang.ide.typecheck

import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project

class TypecheckEventsProcessor(
        project: Project,
        testsRootNode: SMTestProxy.SMRootTestProxy,
        testFrameworkName: String
) : GeneralToSMTRunnerEventsConvertor(project, testsRootNode, testFrameworkName) {
    fun isStarted(testName: String): Boolean {
        val fullTestName = getFullTestName(testName)
        val testProxy = getProxyByFullTestName(fullTestName)
        return testProxy != null
    }
}
