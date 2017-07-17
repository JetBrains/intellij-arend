package org.vclang.lang.core.psi.ext.adapter

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefAbstract
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class VcDefAbstractImplMixin(node: ASTNode) : VcNamedElementImpl(node),
                                                       VcDefAbstract {

    override fun getResultType(): Abstract.Expression = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.ClassDefinition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R = TODO()
}
