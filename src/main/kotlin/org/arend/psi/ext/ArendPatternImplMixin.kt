package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

abstract class ArendPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Pattern {
    override fun getData(): Any? = this

    abstract val atomPattern: ArendAtomPattern?

    abstract val defIdentifier: ArendDefIdentifier?

    abstract val longName: ArendLongName?

    open val asPattern: ArendAsPattern? = null

    open val expr: ArendExpr? = null

    open val atomPatternOrPrefixList: List<ArendAtomPatternOrPrefix> = emptyList()

    override fun isUnnamed(): Boolean {
        val atom = atomPattern ?: return false
        if (atom.underscore != null) {
            return true
        }
        val patterns = atom.patternList
        return if (patterns.size == 1) patterns.first().isUnnamed else false
    }

    override fun isExplicit(): Boolean {
        val atom = atomPattern ?: return true
        if (atom.lbrace != null) {
            return false
        }
        val patterns = atom.patternList
        return if (patterns.size == 1) patterns.first().isExplicit else true
    }

    override fun getNumber(): Int? {
        val atom = atomPattern ?: return null
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
        defIdentifier?.let {
            return if (atomPatternOrPrefixList.isEmpty()) it else NamedUnresolvedReference(it, it.referenceName)
        }

        longName?.let {
            return it.referent
        }

        val patterns = atomPattern?.patternList ?: return null
        return if (patterns.size == 1) patterns.first().headReference else null
    }

    override fun getArguments(): List<Abstract.Pattern> {
        if (defIdentifier != null || longName != null) {
            return atomPatternOrPrefixList
        }

        val patterns = atomPattern?.patternList ?: return emptyList()
        return if (patterns.size == 1) patterns.first().arguments else patterns
    }

    override fun getType(): ArendExpr? {
        expr?.let {
            return it
        }

        val patterns = atomPattern?.patternList ?: return null
        return if (patterns.size == 1) patterns.first().expr else null
    }

    override fun getAsPatterns(): List<Abstract.TypedReferable> {
        val patterns = atomPattern?.patternList
        return (if (patterns == null || patterns.size != 1) emptyList() else patterns[0].asPatterns) + listOfNotNull(asPattern)
    }
}
