package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.VcClassImplement
import org.vclang.psi.VcExpr
import org.vclang.psi.VcNameTele
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcClassImplementStub

abstract class ClassFieldImplAdapter : PsiStubbedReferableImpl<VcClassImplementStub>, VcClassImplement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData(): ClassFieldImplAdapter = this

    override fun getImplementedField(): Referable = NamedUnresolvedReference(this, textRepresentation())

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getImplementation(): VcExpr? = expr
}