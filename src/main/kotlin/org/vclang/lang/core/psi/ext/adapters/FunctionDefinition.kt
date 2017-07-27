package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefFunction
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class FunctionDefinitionAdapter(node: ASTNode) : Surrogate.SignatureDefinition(node),
                                                          VcDefFunction {
    private var body: Surrogate.FunctionBody? = null
    private var statements: List<Surrogate.Statement>? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            arguments: List<Surrogate.Argument>?,
            resultType: Surrogate.Expression?,
            body: Surrogate.FunctionBody?,
            statements: List<Surrogate.Statement>?
    ): FunctionDefinitionAdapter {
        super.reconstruct(position, name, precedence, arguments, resultType)
        this.body = body
        this.statements = statements
        return this
    }

    override fun getBody(): Surrogate.FunctionBody = body ?: throw IllegalStateException()

    override fun getGlobalStatements(): List<Surrogate.Statement> =
            statements ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitFunction(this, params)
}
