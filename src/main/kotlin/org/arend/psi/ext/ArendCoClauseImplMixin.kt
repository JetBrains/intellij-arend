package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendExpr
import org.arend.psi.ArendNameTele


abstract class ArendCoClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendCoClause {
    override fun getData() = this

    override fun getImplementedField() = longName?.referent

    fun getResolvedImplementedField() = longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation(): ArendExpr? = expr

    override fun getClassFieldImpls(): List<ArendCoClause> = coClauseList

    override fun getClassReference(): ClassReferable? {
        val resolved = getResolvedImplementedField()
        return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
    }

    override fun getArgumentsExplicitness() = emptyList<Boolean>()
}