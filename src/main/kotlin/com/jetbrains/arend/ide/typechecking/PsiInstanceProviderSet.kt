package com.jetbrains.arend.ide.typechecking

import com.intellij.openapi.application.runReadAction
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.arend.ide.psi.ext.ArdCompositeElement
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable
import com.jetbrains.arend.ide.psi.moduleScopeProvider
import com.jetbrains.jetpad.vclang.naming.reference.TCReferable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.typechecking.instance.provider.InstanceProvider
import com.jetbrains.jetpad.vclang.typechecking.instance.provider.InstanceProviderSet
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider


class PsiInstanceProviderSet(private val concreteProvider: ConcreteProvider, private val referableConverter: ReferableConverter) : InstanceProviderSet() {
    override fun get(referable: TCReferable): InstanceProvider? {
        val result = super.get(referable)
        if (result != null) {
            return result
        }

        val psiElement = PsiLocatedReferable.fromReferable(referable) as? ArdCompositeElement ?: return null
        return runReadAction {
            val file = psiElement.containingFile as? ArdFile ?: return@runReadAction null
            if (collectInstances(file, CachingScope.make(ScopeFactory.parentScopeForGroup(file, file.moduleScopeProvider, true)), concreteProvider, referableConverter)) super.get(referable) else null
        }
    }
}