package org.vclang.typechecking.execution

import com.intellij.execution.TestStateStorage
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcDefinition
import org.vclang.psi.ext.fullName
import org.vclang.psi.ext.getUrl

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val parent = element.parent
        if (element is VcDefIdentifier && parent is VcDefinition) {
            val url = parent.getUrl()
            val project = element.project
            val record = TestStateStorage.getInstance(project).getState(url)
            var icon = AllIcons.RunConfigurations.TestState.Run
            if (record != null) {
                when (TestIconMapper.getMagnitude(record.magnitude)) {
                    TestStateInfo.Magnitude.ERROR_INDEX, TestStateInfo.Magnitude.FAILED_INDEX -> icon = AllIcons.RunConfigurations.TestState.Red2
                    TestStateInfo.Magnitude.PASSED_INDEX, TestStateInfo.Magnitude.COMPLETE_INDEX -> icon = AllIcons.RunConfigurations.TestState.Green2
                    else -> {}
                }
            }

            return Info(
                    icon,
                    Function<PsiElement, String> { "Type check ${parent.fullName}" },
                    *ExecutorAction.getActions(1)
            )
        }
        return null
    }
}
