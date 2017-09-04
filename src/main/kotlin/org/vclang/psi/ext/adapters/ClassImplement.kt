package org.vclang.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.Surrogate
import org.vclang.VcIcons
import org.vclang.psi.VcClassImplement
import org.vclang.psi.stubs.VcClassImplementStub
import javax.swing.Icon

abstract class ClassImplementAdapter : DefinitionAdapter<VcClassImplementStub>,
                                       VcClassImplement {
    private var expression: Surrogate.Expression? = null
    private var implemented: Abstract.ClassField? = null

    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassImplementStub, nodeType: IStubElementType<*, *>)
            : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = VcIcons.IMPLEMENTATION

    fun reconstruct(
            position: Surrogate.Position,
            name: String?,
            expression: Surrogate.Expression
    ): ClassImplementAdapter {
        super.reconstruct(position, name, Abstract.Precedence.DEFAULT)
        this.expression = expression
        setNotStatic()
        return this
    }

    override fun getImplementedField(): Abstract.ClassField =
            implemented ?: throw IllegalStateException()

    fun setImplemented(implemented: Abstract.ClassField) {
        this.implemented = implemented
    }

    override fun getImplementation(): Surrogate.Expression =
            expression ?: throw IllegalStateException()

    override fun getParentDefinition(): ClassDefinitionAdapter =
            super.getParentDefinition() as ClassDefinitionAdapter

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitImplement(this, params)
}
