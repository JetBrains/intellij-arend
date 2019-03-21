package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.Referable
import org.arend.psi.ArendClassImplement
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendNameTele
import org.arend.psi.CoClauseBase
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.stubs.ArendClassImplementStub

abstract class ClassFieldImplAdapter : PsiStubbedReferableImpl<ArendClassImplementStub>, ArendClassImplement, CoClauseBase {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getImplementedField() = getLongName().referent

    override fun getClassFieldImpls(): List<ArendCoClause> = getCoClauseList()

    override fun getResolvedImplementedField() = getLongName().refIdentifierList.lastOrNull()?.reference?.resolve() as? Referable

    override fun getName() = getLongName().refIdentifierList.lastOrNull()?.referenceName

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation() = expr

    override fun getClassReference() = CoClauseBase.getClassReference(this)

    override fun getClassReferenceData(onlyClassRef: Boolean) = CoClauseBase.getClassReferenceData(this)

    override fun getIcon(flags: Int) = ArendIcons.IMPLEMENTATION
}