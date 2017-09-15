package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Concrete
import org.vclang.psi.VcClassField
import org.vclang.psi.stubs.VcClassFieldStub

abstract class ClassFieldAdapter : DefinitionAdapter<VcClassFieldStub>, VcClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(): Concrete.ClassField {
        TODO("not implemented")
    }

/* TODO[abstract]
    private var resultType: Surrogate.Expression? = null

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            resultType: Surrogate.Expression?
    ): ClassFieldAdapter {
        super.reconstruct(position, name, precedence)
        setNotStatic()
        this.resultType = resultType
        return this
    }

    override fun getParentDefinition(): ClassDefinitionAdapter? =
            super.getParentDefinition() as? ClassDefinitionAdapter

    override fun getResultType(): Surrogate.Expression =
            resultType ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClassField(this, params)
    */
}
