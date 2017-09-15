package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcClassImplementStub

abstract class ClassImplementationAdapter : PsiStubbedReferableImpl<VcClassImplementStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}