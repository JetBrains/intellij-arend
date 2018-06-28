package org.vclang.typechecking

import com.jetbrains.jetpad.vclang.naming.reference.TCReferable
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.typechecking.instance.provider.InstanceProvider
import com.jetbrains.jetpad.vclang.typechecking.instance.provider.InstanceProviderSet
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.VcFile
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.moduleScopeProvider


class PsiInstanceProviderSet(private val concreteProvider: ConcreteProvider) : InstanceProviderSet() {
    override fun get(referable: TCReferable): InstanceProvider? {
        val result = super.get(referable)
        if (result != null) {
            return result
        }

        val file = (referable as? VcCompositeElement)?.containingFile as? VcFile ?: return null
        return if (collectInstances(file, CachingScope.make(ScopeFactory.parentScopeForGroup(file, file.moduleScopeProvider, true)), concreteProvider)) super.get(referable) else null
    }
}