package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcConstructor

abstract class ConstructorAdapter(node: ASTNode) : DefinitionAdapter(node),
                                                   VcConstructor {
    private var dataType: DataDefinitionAdapter? = null
    private var arguments: List<Surrogate.TypeArgument>? = null
    private var eliminatedReferences: List<Surrogate.ReferenceExpression>? = null
    private var clauses: List<Surrogate.FunctionClause>? = null

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            dataType: DataDefinitionAdapter?,
            arguments: List<Surrogate.TypeArgument>?,
            eliminatedReferences: List<Surrogate.ReferenceExpression>?,
            clauses: List<Surrogate.FunctionClause>?
    ): ConstructorAdapter {
        super.reconstruct(position, name, precedence)
        this.dataType = dataType
        this.arguments = arguments
        this.eliminatedReferences = eliminatedReferences
        this.clauses = clauses
        return this
    }

    override fun getArguments(): List<Surrogate.TypeArgument> =
            arguments ?: throw IllegalStateException()

    override fun getEliminatedReferences(): List<Surrogate.ReferenceExpression>? =
            eliminatedReferences

    override fun getClauses(): List<Surrogate.FunctionClause>? = clauses

    override fun getDataType(): DataDefinitionAdapter = dataType ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitConstructor(this, params)
}
