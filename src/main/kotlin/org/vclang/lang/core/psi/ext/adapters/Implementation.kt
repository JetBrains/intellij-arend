package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefImplement
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class ImplementationDefinition(node: ASTNode) : VcNamedElementImpl(node),
                                                         VcDefImplement {

    override fun getImplementedField(): Abstract.ClassField = TODO()

    override fun getImplementation(): Abstract.Expression = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.ClassDefinition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(
            visitor: AbstractDefinitionVisitor<in P, out R>, params: P
    ): R = TODO()
}
