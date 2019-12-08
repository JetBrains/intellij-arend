package org.arend.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.Function
import org.arend.core.definition.Definition.TypeCheckingStatus.*
import org.arend.psi.*
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.fullName
import org.arend.typechecking.TypeCheckingService

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (!(element is LeafPsiElement && element.node.elementType == ArendElementTypes.ID)) {
            return null
        }

        val parent = when (val id = element.parent) {
            is ArendDefIdentifier -> id.parent as? TCDefinition
            is ArendRefIdentifier -> ((id.parent as? ArendLongName)?.parent as? ArendCoClause)?.coClauseDef
            else -> null
        } ?: return null

        val service = parent.project.service<TypeCheckingService>()
        val def = service.getTypechecked(parent)
        val icon = when (def?.status()) {
            NO_ERRORS, DEP_PROBLEMS -> AllIcons.RunConfigurations.TestState.Green2
            HAS_WARNINGS -> AllIcons.RunConfigurations.TestState.Yellow2
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
