package org.vclang.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcDefinition
import org.vclang.psi.ext.fullName
import org.vclang.typechecking.TypeCheckingService

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val parent = element.parent
        if (element is VcDefIdentifier && parent is VcDefinition) {
            val project = element.project
            var icon = AllIcons.RunConfigurations.TestState.Run

            val tcs = TypeCheckingService.getInstance(project)
            val state = tcs.getState(parent)
            when (state) {
                true -> icon = AllIcons.RunConfigurations.TestState.Green2
                false -> icon = AllIcons.RunConfigurations.TestState.Red2
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
