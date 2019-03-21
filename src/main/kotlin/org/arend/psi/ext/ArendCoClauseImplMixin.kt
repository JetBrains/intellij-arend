package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.*


abstract class ArendCoClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendCoClause, CoClauseBase {
    override fun getData() = this

    override fun getImplementedField() = getLongName()?.referent

    override fun getClassFieldImpls(): List<ArendCoClause> = getCoClauseList()

    override fun getResolvedImplementedField() = getLongName()?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation(): ArendExpr? = expr

    override fun getClassReference() = CoClauseBase.getClassReference(this)

    override fun getClassReferenceData(onlyClassRef: Boolean) = CoClauseBase.getClassReferenceData(this)
}