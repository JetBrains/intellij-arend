package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.term.Precedence
import org.vclang.psi.VcDefinition
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcNamedStub

abstract class DefinitionAdapter<StubT> : PsiStubbedReferableImpl<StubT>, VcDefinition
where StubT : VcNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT // TODO[abstract]

    /* TODO[abstract]
    private var precedence: Abstract.Precedence? = null
    private var parentDefinition: Abstract.Definition? = null
    private var isStatic: Boolean = true
    private var currentName: String? = null

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
    */
}
