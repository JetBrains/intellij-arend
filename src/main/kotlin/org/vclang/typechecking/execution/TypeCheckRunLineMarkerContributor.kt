package org.vclang.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.ext.fullName
import org.vclang.typechecking.TypeCheckingService

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        val parent = element.parent
        if (element is VcDefIdentifier && parent is VcDefinition) {
            val service = TypeCheckingService.getInstance(parent.project)
            val ref = (parent.containingFile as? VcFile)?.libraryName
                ?.let { service.libraryManager.getRegisteredLibrary(it) }
                ?.let { LocatedReferable.Helper.resolveReferable(parent, it.moduleScopeProvider) as? GlobalReferable }
                ?: parent
            val state = service.typecheckerState.getTypechecked(ref)?.let { it.status() == Definition.TypeCheckingStatus.NO_ERRORS }
            val icon = when (state) {
                true -> AllIcons.RunConfigurations.TestState.Green2
                false -> AllIcons.RunConfigurations.TestState.Red2
                null -> AllIcons.RunConfigurations.TestState.Run
            }
            return Info(
                    icon,
                    Function { "Type check ${parent.fullName}" },
                    *ExecutorAction.getActions(1)
            )
        }
        return null
    }
}
