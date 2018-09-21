package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ScopeFactory
import org.arend.typechecking.instance.provider.InstanceProvider
import org.arend.typechecking.instance.provider.InstanceProviderSet
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.moduleScopeProvider


class PsiInstanceProviderSet(private val concreteProvider: ConcreteProvider, private val referableConverter: ReferableConverter) : InstanceProviderSet() {
    override fun get(referable: TCReferable): InstanceProvider? {
        val result = super.get(referable)
        if (result != null) {
            return result
        }

        val psiElement = PsiLocatedReferable.fromReferable(referable) as? ArendCompositeElement ?: return null
        return runReadAction {
            val file = psiElement.containingFile as? ArendFile ?: return@runReadAction null
            if (collectInstances(file, CachingScope.make(ScopeFactory.parentScopeForGroup(file, file.moduleScopeProvider, true)), concreteProvider, referableConverter)) super.get(referable) else null
        }
    }
}