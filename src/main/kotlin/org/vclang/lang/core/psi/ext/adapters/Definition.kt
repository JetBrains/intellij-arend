package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class DefinitionAdapter(node: ASTNode) : SourceNodeAdapter(node),
                                                  Abstract.ReferableSourceNode,
                                                  VcDefinition {
    private var precedence: Abstract.Precedence? = null
    private var parentDefinition: Abstract.Definition? = null
    private var isStatic: Boolean = true
    private var currentName: String? = null

    fun reconstruct(
            position: Surrogate.Position?,
            currentName: String?,
            precedence: Abstract.Precedence?
    ): DefinitionAdapter {
        super.reconstruct(position)
        this.currentName = currentName
        this.isStatic = true
        this.precedence = precedence
        return this
    }

    override fun getPrecedence(): Abstract.Precedence = precedence ?: throw IllegalStateException()

    override fun getParentDefinition(): Abstract.Definition? = parentDefinition

    fun setParent(parentDefinition: Abstract.Definition?) {
        this.parentDefinition = parentDefinition
    }

    override fun isStatic(): Boolean = isStatic

    fun setNotStatic() {
        this.isStatic = false
    }

    override fun getName(): String? = currentName ?: super.getName()

    override fun toString(): String = currentName ?: super.toString()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            throw NotImplementedError()
}

abstract class SourceNodeAdapter(node: ASTNode) : VcNamedElementImpl(node), Abstract.SourceNode {
    private var position: Surrogate.Position? = null

    fun reconstruct(position: Surrogate.Position?): SourceNodeAdapter {
        this.position = position
        return this
    }

    fun getPosition(): Surrogate.Position = position ?: throw IllegalStateException()
}
