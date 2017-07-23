package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.ext.VcCompositeElementImpl

abstract class DefinitionAdapter(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcDefinition {

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(
            visitor: AbstractDefinitionVisitor<in P, out R>, params: P
    ): R = TODO()
}
