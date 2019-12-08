package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.stubs.ArendClassImplementStub

abstract class ClassFieldImplAdapter : ReferableAdapter<ArendClassImplementStub>, ArendClassImplement, CoClauseBase {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getPrec(): ArendPrec? = null

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override fun getImplementedField() = longName

    override fun getCoClauseElements(): List<ArendLocalCoClause> = localCoClauseList

    override val resolvedImplementedField
        get() = longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? Referable

    override fun getName() = longName.refIdentifierList.lastOrNull()?.referenceName

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation() = expr

    override fun hasImplementation() = fatArrow != null

    override fun getClassReference() = CoClauseBase.getClassReference(this)

    override fun getClassReferenceData(onlyClassRef: Boolean) = CoClauseBase.getClassReferenceData(this)

    override fun getIcon(flags: Int) = ArendIcons.IMPLEMENTATION
}