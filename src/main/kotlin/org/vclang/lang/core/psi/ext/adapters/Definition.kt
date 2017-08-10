package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.ext.VcNamedElementImpl

abstract class DefinitionAdapter(node: ASTNode) : ReferableSourceNodeAdapter(node),
                                                  VcDefinition {
    private var precedence: Abstract.Precedence? = null
    private var parentDefinition: Abstract.Definition? = null
    private var isStatic: Boolean = true

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?
    ): DefinitionAdapter {
        this.isStatic = true
        this.precedence = precedence
        return this
    }

    override fun getPrecedence(): Abstract.Precedence? = precedence

    override fun getParentDefinition(): Abstract.Definition? = parentDefinition

    fun setParent(parentDefinition: Abstract.Definition?) {
        this.parentDefinition = parentDefinition
    }

    override fun isStatic(): Boolean = isStatic

    fun setIsStatic(isStatic: Boolean) {
        this.isStatic = isStatic
    }

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            throw NotImplementedError()
}

abstract class ReferableSourceNodeAdapter(node: ASTNode) : VcNamedElementImpl(node),
                                                           Abstract.ReferableSourceNode {
    override fun toString(): String = name ?: text
}
