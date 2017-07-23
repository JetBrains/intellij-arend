package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcConstructor
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class ConstructorAdapter(node: ASTNode) : VcNamedElementImpl(node),
                                                   VcConstructor {

    override fun getDataType(): Abstract.DataDefinition = TODO()

    override fun getArguments(): List<Abstract.TypeArgument> = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun getEliminatedReferences(): List<Abstract.ReferenceExpression> = TODO()

    override fun getClauses(): List<Abstract.FunctionClause> = TODO()

    override fun <P, R> accept(
            visitor: AbstractDefinitionVisitor<in P, out R>, params: P
    ): R = TODO()
}
