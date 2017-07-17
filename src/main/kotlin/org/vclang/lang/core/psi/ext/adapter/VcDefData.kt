package org.vclang.lang.core.psi.ext.adapter

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefData
import org.vclang.lang.core.psi.ext.VcNamedElementImpl
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcDefDataImplMixin(node: ASTNode) : VcNamedElementImpl(node),
                                                   VcDefData {
    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    override fun getParameters(): List<Abstract.TypeArgument> = TODO()

    override fun getEliminatedReferences(): List<Abstract.ReferenceExpression> = TODO()

    override fun getConstructorClauses(): List<Abstract.ConstructorClause> = TODO()

    override fun isTruncated(): Boolean = TODO()

    override fun getUniverse(): Abstract.Expression = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R = TODO()
}
