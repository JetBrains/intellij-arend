package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdAtomPattern
import com.jetbrains.arend.ide.psi.ArdAtomPatternOrPrefix
import com.jetbrains.arend.ide.psi.ArdDefIdentifier
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.concrete.Concrete

abstract class ArdPatternImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), Abstract.Pattern {
    override fun getData(): Any? = this

    abstract fun getAtomPattern(): ArdAtomPattern?

    abstract fun getDefIdentifier(): ArdDefIdentifier?

    open fun getAtomPatternOrPrefixList(): List<ArdAtomPatternOrPrefix> = emptyList()

    override fun isUnnamed() = getAtomPattern()?.underscore != null

    override fun isExplicit(): Boolean {
        val atom = getAtomPattern() ?: return true
        if (atom.lbrace != null) {
            return false
        }
        val patterns = atom.patternList
        return if (patterns.size == 1) patterns.first().isExplicit else true
    }

    override fun getNumber(): Int? {
        val atom = getAtomPattern() ?: return null
        val number = atom.number ?: atom.negativeNumber
        if (number != null) {
            val text = number.text
            val len = text.length
            if (len >= 9) {
                return if (text[0] == '-') -Concrete.NumberPattern.MAX_VALUE else Concrete.NumberPattern.MAX_VALUE
            }
            val value = text.toInt()
            return when {
                value > Concrete.NumberPattern.MAX_VALUE -> Concrete.NumberPattern.MAX_VALUE
                value < -Concrete.NumberPattern.MAX_VALUE -> -Concrete.NumberPattern.MAX_VALUE
                else -> value
            }
        }
        val patterns = atom.patternList
        return if (patterns.size == 1) patterns.first().number else null
    }

    override fun getHeadReference(): Referable? {
        val conName = getDefIdentifier()
        if (conName != null) {
            return if (getAtomPatternOrPrefixList().isEmpty()) conName else NamedUnresolvedReference(conName, conName.referenceName)
        }

        val patterns = getAtomPattern()?.patternList ?: return null
        return if (patterns.size == 1) patterns.first().headReference else null
    }

    override fun getArguments(): List<Abstract.Pattern> {
        if (getDefIdentifier() != null) return getAtomPatternOrPrefixList()

        val patterns = getAtomPattern()?.patternList ?: return emptyList()
        return if (patterns.size == 1) patterns.first().arguments else patterns
    }
}
