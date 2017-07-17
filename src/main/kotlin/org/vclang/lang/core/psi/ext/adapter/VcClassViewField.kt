package org.vclang.lang.core.psi.ext.adapter

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcClassViewField
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class VcClassViewFieldImplMixin(node: ASTNode) : VcNamedElementImpl(node),
                                                          VcClassViewField {

    override fun getUnderlyingFieldName(): String = TODO()

    override fun getUnderlyingField(): Abstract.ClassField = TODO()

    override fun getOwnView(): Abstract.ClassView = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R = TODO()
}
