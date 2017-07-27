package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefClass
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class ClassDefinitionAdapter(node: ASTNode) : DefinitionAdapter(node),
                                                       VcDefClass {
    private var polyParameters: List<Surrogate.TypeParameter>? = null
    private var superClasses: List<Surrogate.SuperClass>? = null
    private var fields: List<ClassFieldAdapter>? = null
    private var implementations: List<ImplementationAdapter>? = null
    private var globalStatements: List<Surrogate.Statement>? = null
    private var instanceDefinitions: List<DefinitionAdapter>? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            polyParameters: List<Surrogate.TypeParameter>?,
            superClasses: List<Surrogate.SuperClass>?,
            fields: List<ClassFieldAdapter>?,
            implementations: List<ImplementationAdapter>?,
            globalStatements: List<Surrogate.Statement>?,
            instanceDefinitions: List<DefinitionAdapter>?
    ): ClassDefinitionAdapter {
        super.reconstruct(position, name, Abstract.Precedence.DEFAULT)
        this.polyParameters = polyParameters
        this.superClasses = superClasses
        this.fields = fields
        this.implementations = implementations
        this.globalStatements = globalStatements
        this.instanceDefinitions = instanceDefinitions
        return this
    }

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            globalStatements: List<Surrogate.Statement>?
    ): ClassDefinitionAdapter {
        return reconstruct(
                position,
                name,
                listOf(),
                listOf(),
                listOf(),
                listOf(),
                globalStatements,
                listOf()
        )
    }

    override fun getPolyParameters(): List<Surrogate.TypeParameter> =
            polyParameters ?: throw IllegalStateException()

    override fun getSuperClasses(): List<Surrogate.SuperClass> =
            superClasses ?: throw IllegalStateException()

    override fun getFields(): List<ClassFieldAdapter> =
            fields ?: throw IllegalStateException()

    override fun getImplementations(): List<ImplementationAdapter> =
            implementations ?: throw IllegalStateException()

    override fun getInstanceDefinitions(): List<DefinitionAdapter> =
            instanceDefinitions ?: throw IllegalStateException()

    override fun getGlobalStatements(): List<Surrogate.Statement> =
            globalStatements ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClass(this, params)
}
