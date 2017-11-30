package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcAtomPattern
import org.vclang.psi.VcPattern

abstract class VcPatternImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcPattern {
    override fun getData(): Any? = this

    override fun isEmpty(): Boolean = isEmpty(atomPattern)

    override fun isExplicit(): Boolean = isExplicit(atomPattern)

    override fun getHeadReference(): Referable? {
        val conPattern = patternConstructor ?: return atomPattern?.pattern?.headReference
        val conName = conPattern.defRefIdentifier
        return if (conPattern.atomPatternOrPrefixList.isEmpty()) conName else NamedUnresolvedReference(conName, conName.referenceName)
    }

    override fun getArguments(): List<Abstract.Pattern> = patternConstructor?.atomPatternOrPrefixList ?: atomPattern?.pattern?.arguments ?: emptyList()
}

fun isEmpty(atom: VcAtomPattern?): Boolean = when {
    atom == null -> false
    atom.rparen != null -> atom.pattern == null
    else -> atom.pattern?.isEmpty ?: false
}

fun isExplicit(atom: VcAtomPattern?): Boolean = atom == null || atom.lbrace == null
