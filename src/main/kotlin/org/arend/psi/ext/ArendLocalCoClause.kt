package org.arend.psi.ext

import com.intellij.lang.ASTNode

open class ArendLocalCoClause(node: ASTNode) : ArendSourceNodeImpl(node), CoClauseBase {
    override fun getData() = this

    override fun getPrec(): ArendPrec? = null

    override fun getImplementedField() = longName

    override fun getCoClauseElements(): List<ArendLocalCoClause> = localCoClauseList

    override fun getParameters(): List<ArendNameTele> = lamParamList.filterIsInstance<ArendNameTele>()

    override fun getLamParameters(): List<ArendLamParam> = lamParamList

    override fun getImplementation(): ArendExpr? = expr

    override fun hasImplementation() = fatArrow != null

    override fun getCoClauseData() = lbrace

    override fun getClassReference() = null

    override fun isDefault() = false
}