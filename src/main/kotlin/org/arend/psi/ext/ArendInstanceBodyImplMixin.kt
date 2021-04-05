package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendClause
import org.arend.psi.ArendInstanceBody

abstract class ArendInstanceBodyImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendInstanceBody {
    override val clauseList: List<ArendClause>
        get() = functionClauses?.clauseList ?: emptyList()
}