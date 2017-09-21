package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Concrete
import org.vclang.psi.VcClassField
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefinition
import org.vclang.psi.stubs.VcDefClassStub

abstract class ClassDefinitionAdapter : DefinitionAdapter<VcDefClassStub>, VcDefClass {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(): Concrete.ClassDefinition {
        TODO("not implemented")
    }

    override fun getDynamicSubgroups(): List<VcDefinition> =
        classStats?.classStatList?.mapNotNull { it.definition } ?: emptyList()

    override fun getFields(): List<VcClassField> =
        classStats?.classStatList?.mapNotNull { it.classField } ?: emptyList()

/* TODO[abstract]
    private var polyParameters: List<Surrogate.TypeParameter>? = null
    private var superClasses: List<Surrogate.SuperClass>? = null
    private var fields: List<ClassFieldAdapter>? = null
    private var classImplements: List<ClassImplementAdapter>? = null
    private var globalStatements: List<Surrogate.Statement>? = null
    private var instanceDefinitions: List<DefinitionAdapter<*>>? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_DEFINITION

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            polyParameters: List<Surrogate.TypeParameter>?,
            superClasses: List<Surrogate.SuperClass>?,
            fields: List<ClassFieldAdapter>?,
            classImplements: List<ClassImplementAdapter>?,
            globalStatements: List<Surrogate.Statement>?,
            instanceDefinitions: List<DefinitionAdapter<*>>?
    ): ClassDefinitionAdapter {
        super.reconstruct(position, name, Abstract.Precedence.DEFAULT)
        this.polyParameters = polyParameters
        this.superClasses = superClasses
        this.fields = fields
        this.classImplements = classImplements
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

    override fun getImplementations(): List<ClassImplementAdapter> =
            classImplements ?: throw IllegalStateException()

    override fun getInstanceDefinitions(): List<DefinitionAdapter<*>> =
            instanceDefinitions ?: throw IllegalStateException()

    override fun getGlobalStatements(): List<Surrogate.Statement> =
            globalStatements ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClass(this, params)
    */
}
