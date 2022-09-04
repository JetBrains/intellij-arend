package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes
import org.arend.psi.firstRelevantChild
import org.arend.psi.getChild
import org.arend.psi.getChildOfType

class ArendStat(node: ASTNode) : ArendSourceNodeImpl(node), ArendStatement {
    val statCmd: ArendStatCmd?
        get() = getChildOfType()

    override fun getGroup() = firstRelevantChild as? ArendGroup

    override fun getNamespaceCommand() = firstRelevantChild as? ArendStatCmd

    override fun getPLevelsDefinition(): ArendLevelParamsSeq? = getChild { it.elementType == ArendElementTypes.P_LEVEL_PARAMS_SEQ }

    override fun getHLevelsDefinition(): ArendLevelParamsSeq? = getChild { it.elementType == ArendElementTypes.H_LEVEL_PARAMS_SEQ }
}