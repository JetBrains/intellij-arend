package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ScopeFactory
import org.arend.typechecking.instance.provider.InstanceProvider
import org.arend.typechecking.instance.provider.InstanceProviderSet
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable


class PsiInstanceProviderSet : InstanceProviderSet() {
    override fun get(referable: TCReferable): InstanceProvider? {
        val result = super.get(referable)
        if (result != null) {
            return result
        }

        val psiElement = PsiLocatedReferable.fromReferable(referable) ?: return null
        return runReadAction {
            val file = psiElement.containingFile as? ArendFile ?: return@runReadAction null
            if (collectInstances(file, CachingScope.make(ScopeFactory.parentScopeForGroup(file, file.moduleScopeProvider, true)))) super.get(referable) else null
        }
    }
}