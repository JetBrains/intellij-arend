package org.vclang.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import com.jetbrains.jetpad.vclang.core.definition.Definition
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcDefinition
import org.vclang.psi.ext.fullName
import org.vclang.typechecking.TypeCheckingService

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val parent = element.parent
        if (element is VcDefIdentifier && parent is VcDefinition) {
            val state = TypeCheckingService.getInstance(element.project).typecheckerState.getTypechecked(parent)?.let { it.status() == Definition.TypeCheckingStatus.NO_ERRORS }
            val icon = when (state) {
                true -> AllIcons.RunConfigurations.TestState.Green2
                false -> AllIcons.RunConfigurations.TestState.Red2
                null -> AllIcons.RunConfigurations.TestState.Run
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
