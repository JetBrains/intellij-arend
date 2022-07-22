package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendClassStat
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendCoClauseDef
import org.arend.psi.ArendStatCmd


abstract class ArendCoClauseImplMixin(node: ASTNode) : ArendLocalCoClauseImplMixin(node), ArendCoClause {
    override fun getFunctionReference(): ArendCoClauseDef? =
        if (parent !is ArendClassStat || defIdentifier != null || coClauseDef?.returnExpr != null) coClauseDef else null

    override fun isDefault() = (parent as? ArendClassStat)?.defaultKw != null

    override fun getGroup() = coClauseDef

    override fun getNamespaceCommand(): ArendStatCmd? = null
}