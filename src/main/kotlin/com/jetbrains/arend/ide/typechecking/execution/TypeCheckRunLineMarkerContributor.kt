package com.jetbrains.arend.ide.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.Function
import com.jetbrains.arend.ide.psi.ArdDefClass
import com.jetbrains.arend.ide.psi.ArdDefIdentifier
import com.jetbrains.arend.ide.psi.ArdDefinition
import com.jetbrains.arend.ide.psi.ArdElementTypes
import com.jetbrains.arend.ide.psi.ext.fullName
import com.jetbrains.arend.ide.typechecking.TypeCheckingService
import com.jetbrains.jetpad.vclang.core.definition.Definition

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (!(element is LeafPsiElement && element.node.elementType == ArdElementTypes.ID)) {
            return null
        }

        val parent = (element.parent as? ArdDefIdentifier)?.parent as? ArdDefinition ?: return null
        if (parent is ArdDefClass && parent.fatArrow != null) {
            return null
        }

        val service = TypeCheckingService.getInstance(parent.project)
        val state = service.getTypechecked(parent)?.status()
        val icon = when (state) {
            Definition.TypeCheckingStatus.NO_ERRORS -> AllIcons.RunConfigurations.TestState.Green2
            Definition.TypeCheckingStatus.HAS_WARNINGS, Definition.TypeCheckingStatus.MAY_BE_TYPE_CHECKED_WITH_WARNINGS -> AllIcons.RunConfigurations.TestState.Yellow2
            null -> AllIcons.RunConfigurations.TestState.Run
            else -> AllIcons.RunConfigurations.TestState.Red2
        }

        return Info(
                icon,
                Function { "Typecheck ${parent.fullName}" },
                *ExecutorAction.getActions(1)
        )
    }
}
