package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.psi.VcDefFunction
import org.vclang.lang.core.psi.ext.VcNamedElementImpl
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class FunctionDefinitionAdapter(node: ASTNode) : VcNamedElementImpl(node),
                                                          VcDefFunction {
    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    override fun getBody(): Abstract.FunctionBody = TODO()

    override fun getArguments(): List<Abstract.Argument> = TODO()

    override fun getResultType(): Abstract.Expression = TODO()

    override fun getGlobalDefinitions(): Collection<Abstract.Definition> = TODO()

    override fun getPrecedence(): Abstract.Precedence = TODO()

    override fun getParentDefinition(): Abstract.Definition = TODO()

    override fun isStatic(): Boolean = TODO()

    override fun <P, R> accept(
            visitor: AbstractDefinitionVisitor<in P, out R>, params: P
    ): R = TODO()
}
