package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType
import org.arend.psi.getChildOfTypeStrict
import org.arend.term.abs.Abstract


class ArendCoClause(node: ASTNode) : ArendLocalCoClause(node), Abstract.CoClauseFunctionReference, ArendStatement {
    val defIdentifier: ArendDefIdentifier?
        get() = getChildOfType()

    override val longName: ArendLongName
        get() = getChildOfTypeStrict()

    override fun getFunctionReference(): ArendCoClauseDef? =
        if (parent !is ArendClassStat || defIdentifier != null || group?.returnExpr != null) group else null

    override fun isDefault() = (parent as? ArendClassStat)?.isDefault == true

    override fun getGroup(): ArendCoClauseDef? = getChildOfType()

    override fun getNamespaceCommand(): ArendStatCmd? = null
}