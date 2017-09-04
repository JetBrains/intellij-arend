package org.vclang.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.Surrogate
import org.vclang.VcIcons
import org.vclang.psi.VcDefData
import org.vclang.psi.stubs.VcDefDataStub
import org.vclang.resolve.Namespace
import org.vclang.resolve.NamespaceProvider
import javax.swing.Icon

abstract class DataDefinitionAdapter : DefinitionAdapter<VcDefDataStub>,
                                       VcDefData {
    private var parameters: List<Surrogate.TypeParameter>? = null
    private var eliminatedReferences: List<Surrogate.ReferenceExpression>? = null
    private var constructorClauses: MutableList<Surrogate.ConstructorClause>? = null
    private var universe: Surrogate.UniverseExpression? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = VcIcons.DATA_DEFINITION

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            parameters: List<Surrogate.TypeParameter>?,
            eliminatedReferences: List<Surrogate.ReferenceExpression>?,
            universe: Surrogate.UniverseExpression?,
            constructorClauses: MutableList<Surrogate.ConstructorClause>?
    ): DataDefinitionAdapter {
        super.reconstruct(position, name, precedence)
        this.parameters = parameters
        this.eliminatedReferences = eliminatedReferences
        this.constructorClauses = constructorClauses
        this.universe = universe
        return this
    }

    override fun getParameters(): List<Surrogate.TypeParameter> =
            parameters ?: throw IllegalStateException()

    override fun getEliminatedReferences(): List<Surrogate.ReferenceExpression>? =
            eliminatedReferences

    override fun getConstructorClauses(): MutableList<Surrogate.ConstructorClause> =
            constructorClauses ?: throw IllegalStateException()

    override fun isTruncated(): Boolean = truncatedKw != null

    override fun getUniverse(): Surrogate.UniverseExpression? = universe

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitData(this, params)
}
