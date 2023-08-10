package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.*

class ArendStat(node: ASTNode) : ArendSourceNodeImpl(node), ArendStatement {
    val statCmd: ArendStatCmd?
        get() = childOfType()

    val statAccessMod: ArendStatAccessMod?
        get() = childOfType()

    override fun getGroup(): ArendGroup? = childOfType()

    override fun getNamespaceCommand(): ArendStatCmd? = childOfType()

    override fun getPLevelsDefinition(): ArendLevelParamsSeq? = getChild { it.elementType == ArendElementTypes.P_LEVEL_PARAMS_SEQ }

    override fun getHLevelsDefinition(): ArendLevelParamsSeq? = getChild { it.elementType == ArendElementTypes.H_LEVEL_PARAMS_SEQ }

    companion object {
        fun flatStatements(l: List<ArendStat>?): List<ArendStat> = l?.flatMap {
            val accessMod = it.statAccessMod
            if (accessMod == null) listOf(it) else flatStatements(accessMod.statList)
        } ?: emptyList()
    }
}