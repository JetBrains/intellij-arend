package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract

open class ArendLamParam(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.LamParameter {
    override fun getData() = this
}
