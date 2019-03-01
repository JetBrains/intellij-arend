package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendAsPattern


abstract class ArendAsPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAsPattern {
    override fun getData() = this

    override fun getReferable() = defIdentifier

    override fun getType() = expr
}