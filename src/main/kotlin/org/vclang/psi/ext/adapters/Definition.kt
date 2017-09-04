package org.vclang.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.Surrogate
import org.vclang.psi.VcDefinition
import org.vclang.psi.ext.VcStubbedNamedElementImpl
import org.vclang.psi.stubs.VcNamedStub

abstract class DefinitionAdapter<StubT> : SourceNodeAdapter<StubT>,
                                          Abstract.ReferableSourceNode,
                                          VcDefinition
where StubT : VcNamedStub, StubT : StubElement<*> {
    private var precedence: Abstract.Precedence? = null
    private var parentDefinition: Abstract.Definition? = null
    private var isStatic: Boolean = true
    private var currentName: String? = null

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    fun reconstruct(
            position: Surrogate.Position?,
            currentName: String?,
            precedence: Abstract.Precedence?
    ): DefinitionAdapter<StubT> {
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

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            throw NotImplementedError()
}

abstract class SourceNodeAdapter<StubT> : VcStubbedNamedElementImpl<StubT>,
                                          Abstract.SourceNode
where StubT : VcNamedStub, StubT : StubElement<*> {
    private var position: Surrogate.Position? = null

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    fun reconstruct(position: Surrogate.Position?): SourceNodeAdapter<StubT> {
        this.position = position
        return this
    }

    fun getPosition(): Surrogate.Position = position ?: throw IllegalStateException()
}
