package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.term.abs.Abstract


class ArendCoClause(node: ASTNode) : ArendLocalCoClause(node), Abstract.CoClauseFunctionReference, ArendStatement {
    val defIdentifier: ArendDefIdentifier?
        get() = childOfType()

    override val longName: ArendLongName
        get() = childOfTypeStrict()

    override fun getFunctionReference(): ArendCoClauseDef? =
        if (parent !is ArendClassStat || defIdentifier != null || group?.returnExpr != null) group else null

    override fun isDefault() = (parent as? ArendClassStat)?.isDefault == true

    override fun getGroup(): ArendCoClauseDef? = childOfType()

    override fun getNamespaceCommand() = null

    override fun getPLevelsDefinition() = null

    override fun getHLevelsDefinition() = null
}