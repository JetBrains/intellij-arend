package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendExpr
import org.arend.psi.ArendNameTele
import org.arend.psi.CoClauseBase


abstract class ArendCoClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendCoClause, CoClauseBase {
    override fun getData() = this

    override fun getImplementedField() = longName

    override fun getClassFieldImpls(): List<ArendCoClause> = coClauseList

    override val resolvedImplementedField
        get() = longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation(): ArendExpr? = expr

    override fun getClassReference() = CoClauseBase.getClassReference(this)

    override fun getClassReferenceData(onlyClassRef: Boolean) = CoClauseBase.getClassReferenceData(this)
}