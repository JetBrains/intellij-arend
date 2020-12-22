package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendClassStat
import org.arend.psi.ArendCoClause


abstract class ArendCoClauseImplMixin(node: ASTNode) : ArendLocalCoClauseImplMixin(node), ArendCoClause {
    override fun getFunctionReference() = coClauseDef

    override fun isDefault() = (parent as? ArendClassStat)?.defaultKw != null
}