package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefClassView
import org.vclang.lang.core.psi.ext.VcNamedElementImpl
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class ClassViewAdapter(node: ASTNode) : VcNamedElementImpl(node),
                                                 VcDefClassView {
    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    override fun getUnderlyingClassReference(): Abstract.ReferenceExpression = TODO()

    override fun getClassifyingFieldName(): String = TODO()

    override fun getClassifyingField(): Abstract.ClassField = TODO()

    override fun getFields(): List<Abstract.ClassViewField> = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(
            visitor: AbstractDefinitionVisitor<in P, out R>, params: P
    ): R = TODO()
}
