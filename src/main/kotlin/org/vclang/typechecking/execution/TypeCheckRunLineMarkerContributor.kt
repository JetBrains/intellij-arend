package org.vclang.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.Function
import com.jetbrains.jetpad.vclang.core.definition.Definition
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcElementTypes
import org.vclang.psi.ext.fullName
import org.vclang.typechecking.TypeCheckingService

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (!(element is LeafPsiElement && element.node.elementType == VcElementTypes.ID)) {
            return null
        }

        val parent = (element.parent as? VcDefIdentifier)?.parent as? VcDefinition ?: return null
        if (parent is VcDefClass && parent.fatArrow != null) {
            return null
        }

        val service = TypeCheckingService.getInstance(parent.project)
        val state = service.getTypechecked(parent)?.let { it.status() == Definition.TypeCheckingStatus.NO_ERRORS }
        val icon = when (state) {
            true -> AllIcons.RunConfigurations.TestState.Green2
            false -> AllIcons.RunConfigurations.TestState.Red2
            null -> AllIcons.RunConfigurations.TestState.Run
        }

        return Info(
                icon,
                Function { "Typecheck ${parent.fullName}" },
                *ExecutorAction.getActions(1)
        )
    }
}
