package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.vclang.psi.VcClassImplement
import org.vclang.psi.VcCoClause
import org.vclang.psi.VcNameTele
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcClassImplementStub

abstract class ClassFieldImplAdapter : PsiStubbedReferableImpl<VcClassImplementStub>, VcClassImplement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getName() = longName.refIdentifierList.lastOrNull()?.referenceName

    override fun getImplementedField() = longName.referent

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getImplementation() = expr

    override fun getRecursiveClassFieldImpls(): List<VcCoClause> = coClauseList
}