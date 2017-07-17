package org.vclang.lang.core.psi.ext.adapter

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefClass
import org.vclang.lang.core.psi.ext.VcNamedElementImpl
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcDefClassImplMixin(node: ASTNode) : VcNamedElementImpl(node),
                                                    VcDefClass {
    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    override fun getPolyParameters(): List<Abstract.TypeArgument> = TODO()

    override fun getSuperClasses(): Collection<Abstract.SuperClass> = TODO()

    override fun getFields(): Collection<Abstract.ClassField> = TODO()

    override fun getImplementations(): Collection<Abstract.Implementation> = TODO()

    override fun getInstanceDefinitions(): Collection<Abstract.Definition> = TODO()

    override fun getGlobalDefinitions(): Collection<Abstract.Definition> = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R = TODO()
}
