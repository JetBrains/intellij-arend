package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendStat

abstract class ArendStatImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendStat {
    override fun getGroup() = definition ?: defModule

    override fun getNamespaceCommand() = statCmd

    override fun getPLevelsDefinition() = defPLevels?.levelParams

    override fun getHLevelsDefinition() = defHLevels?.levelParams
}