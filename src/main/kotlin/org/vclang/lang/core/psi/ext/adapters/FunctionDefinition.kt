package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefFunction
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class FunctionDefinitionAdapter(node: ASTNode) : DefinitionAdapter(node),
        Surrogate.StatementCollection,
        VcDefFunction {
    private var parameters: List<Surrogate.Parameter>? = null
    private var resultType: Surrogate.Expression? = null
    private var body: Surrogate.FunctionBody? = null
    private var statements: List<Surrogate.Statement>? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            parameters: List<Surrogate.Parameter>?,
            resultType: Surrogate.Expression?,
            body: Surrogate.FunctionBody?,
            statements: List<Surrogate.Statement>?
    ): FunctionDefinitionAdapter {
        super.reconstruct(position, name, precedence)
        this.parameters = parameters
        this.resultType = resultType
        this.body = body
        this.statements = statements
        return this
    }

    override fun getParameters(): List<Surrogate.Parameter> =
            parameters ?: throw IllegalStateException()

    override fun getResultType(): Surrogate.Expression? = resultType

    override fun getBody(): Surrogate.FunctionBody = body ?: throw IllegalStateException()

    override fun getGlobalStatements(): List<Surrogate.Statement> =
            statements ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitFunction(this, params)
}
