package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendWithBody

abstract class ArendWithBodyImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendWithBody {
    override fun getData() = this
}