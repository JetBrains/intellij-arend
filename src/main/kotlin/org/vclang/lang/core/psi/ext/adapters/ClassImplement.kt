package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcClassImplement

abstract class ClassImplementAdapter(node: ASTNode) : DefinitionAdapter(node),
                                                      VcClassImplement {
    private var expression: Surrogate.Expression? = null
    private var implemented: Abstract.ClassField? = null

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
