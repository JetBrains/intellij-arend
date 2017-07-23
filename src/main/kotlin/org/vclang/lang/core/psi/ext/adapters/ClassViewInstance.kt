package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefInstance
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class ClassViewInstanceAdapter(node: ASTNode) : VcNamedElementImpl(node),
                                                         VcDefInstance {

    override fun isDefault(): Boolean = TODO()

    override fun getArguments(): List<Abstract.Argument> = TODO()

    override fun getClassView(): Abstract.ReferenceExpression = TODO()

    override fun getClassifyingDefinition(): Abstract.Definition = TODO()

    override fun getClassFieldImpls(): Collection<Abstract.ClassFieldImpl> = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(
            visitor: AbstractDefinitionVisitor<in P, out R>, params: P
    ): R = TODO()}
