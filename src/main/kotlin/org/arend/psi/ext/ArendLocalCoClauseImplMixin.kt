package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.*

abstract class ArendLocalCoClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), CoClauseBase {
    override fun getData() = this

    override fun getPrec(): ArendPrec? = null

    override fun getImplementedField() = longName

    override fun getCoClauseElements(): List<ArendLocalCoClause> = localCoClauseList

    override val resolvedImplementedField
        get() = longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation(): ArendExpr? = expr

    override fun hasImplementation() = fatArrow != null

    override fun getClassReference() = CoClauseBase.getClassReference(this)

    override fun getClassReferenceData(onlyClassRef: Boolean) = CoClauseBase.getClassReferenceData(this)
}