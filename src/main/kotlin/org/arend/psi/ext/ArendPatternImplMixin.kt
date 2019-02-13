package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

abstract class ArendPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Pattern {
    override fun getData(): Any? = this

    abstract fun getAtomPattern(): ArendAtomPattern?

    abstract fun getDefIdentifier(): ArendDefIdentifier?

    abstract fun getLongName(): ArendLongName?

    open fun getExpr(): ArendExpr? = null

    open fun getAtomPatternOrPrefixList(): List<ArendAtomPatternOrPrefix> = emptyList()

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

        val longName = getLongName()
        if (longName != null) {
            return longName.referent
        }

        val patterns = getAtomPattern()?.patternList ?: return null
        return if (patterns.size == 1) patterns.first().headReference else null
    }

    override fun getArguments(): List<Abstract.Pattern> {
        if (getDefIdentifier() != null || getLongName() != null) return getAtomPatternOrPrefixList()

        val patterns = getAtomPattern()?.patternList ?: return emptyList()
        return if (patterns.size == 1) patterns.first().arguments else patterns
    }

    override fun getType(): ArendExpr? {
        val type = getExpr()
        if (type != null) {
            return type
        }

        val patterns = getAtomPattern()?.patternList ?: return null
        return if (patterns.size == 1) patterns.first().expr else null
    }
}
