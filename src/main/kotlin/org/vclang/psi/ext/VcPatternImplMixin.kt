package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcAtomPattern
import org.vclang.psi.VcPattern

abstract class VcPatternImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcPattern {
    override fun getData(): Any? = this

    override fun isEmpty(): Boolean = isEmpty(atomPattern)

    override fun isExplicit(): Boolean = isExplicit(atomPattern)

    override fun getHeadReference(): Referable? {
        val conName = defIdentifier ?: return atomPattern?.pattern?.headReference
        return if (atomPatternOrPrefixList.isEmpty()) conName else NamedUnresolvedReference(conName, conName.referenceName)
    }

    override fun getArguments(): List<Abstract.Pattern> = atomPattern?.pattern?.arguments ?: atomPatternOrPrefixList
}

fun isEmpty(atom: VcAtomPattern?): Boolean = when {
    atom == null -> false
    atom.rparen != null -> atom.pattern == null
    else -> atom.pattern?.isEmpty ?: false
}

fun isExplicit(atom: VcAtomPattern?): Boolean = atom == null || atom.lbrace == null
