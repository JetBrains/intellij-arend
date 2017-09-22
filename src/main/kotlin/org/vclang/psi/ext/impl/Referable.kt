package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.term.Precedence
import org.vclang.psi.ext.PsiConcreteReferable
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcNamedStub

abstract class ReferableAdapter<StubT> : PsiStubbedReferableImpl<StubT>, PsiConcreteReferable
where StubT : VcNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT // TODO[abstract]
}
