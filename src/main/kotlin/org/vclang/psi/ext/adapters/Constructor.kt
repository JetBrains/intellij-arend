package org.vclang.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.Surrogate
import org.vclang.VcIcons
import org.vclang.psi.VcConstructor
import org.vclang.psi.stubs.VcConstructorStub
import javax.swing.Icon

abstract class ConstructorAdapter : DefinitionAdapter<VcConstructorStub>,
                                    VcConstructor {
    private var dataType: DataDefinitionAdapter? = null
    private var parameters: List<Surrogate.TypeParameter>? = null
    private var eliminatedReferences: List<Surrogate.ReferenceExpression>? = null
    private var clauses: List<Surrogate.FunctionClause>? = null

    constructor(node: ASTNode) : super(node)

    constructor(stub: VcConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = VcIcons.CONSTRUCTOR

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            dataType: DataDefinitionAdapter?,
            parameters: List<Surrogate.TypeParameter>?,
            eliminatedReferences: List<Surrogate.ReferenceExpression>?,
            clauses: List<Surrogate.FunctionClause>?
    ): ConstructorAdapter {
        super.reconstruct(position, name, precedence)
        this.dataType = dataType
        this.parameters = parameters
        this.eliminatedReferences = eliminatedReferences
        this.clauses = clauses
        return this
    }

    override fun getParameters(): List<Surrogate.TypeParameter> =
            parameters ?: throw IllegalStateException()

    override fun getEliminatedReferences(): List<Surrogate.ReferenceExpression> =
            eliminatedReferences ?: throw IllegalStateException()

    override fun getClauses(): List<Surrogate.FunctionClause> =
            clauses ?: throw IllegalStateException()

    override fun getDataType(): DataDefinitionAdapter = dataType ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitConstructor(this, params)
}
