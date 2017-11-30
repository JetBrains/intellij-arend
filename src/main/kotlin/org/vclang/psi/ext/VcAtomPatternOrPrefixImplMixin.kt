package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcAtomPatternOrPrefix


abstract class VcAtomPatternOrPrefixImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcAtomPatternOrPrefix {
    override fun getData(): Any? = this

    override fun isEmpty(): Boolean = isEmpty(atomPattern)

    override fun isExplicit(): Boolean = isExplicit(atomPattern)

    override fun getHeadReference(): Referable? = defRefIdentifier ?: atomPattern?.pattern?.headReference

    override fun getArguments(): List<Abstract.Pattern> = atomPattern?.pattern?.arguments ?: emptyList()
}
