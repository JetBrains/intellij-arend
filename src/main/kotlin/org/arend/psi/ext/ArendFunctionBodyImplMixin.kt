package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendClause
import org.arend.psi.ArendFunctionBody

abstract class ArendFunctionBodyImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendFunctionBody {
    override val clauseList: List<ArendClause>
        get() = functionClauses?.clauseList ?: emptyList()
}