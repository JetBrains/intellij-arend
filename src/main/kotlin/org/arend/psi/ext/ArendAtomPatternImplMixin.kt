package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

abstract class ArendAtomPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomPattern, Abstract.Pattern {
    override fun getData(): Any? = this

    override fun isUnnamed(): Boolean {
        if (underscore != null) {
            return true
        }
        val patterns = patternList
        return if (patterns.size == 1) patterns.first().isUnnamed else false
    }

    override fun isExplicit(): Boolean {
        if (lbrace != null) {
            return false
        }
        val patterns = patternList
        return if (patterns.size == 1) patterns.first().isExplicit else true
    }

    override fun getInteger(): Int? {
        val number = number ?: negativeNumber
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
        val patterns = patternList
        return if (patterns.size == 1) patterns[0].integer else null
    }

    override fun getHeadReference(): Referable? {
        val patterns = patternList
        return if (patterns.size == 1) patterns[0].headReference else null
    }

    override fun getArguments(): List<Abstract.Pattern> {
        val patterns = patternList
        return if (patterns.size == 1) patterns[0].arguments else patterns
    }

    override fun getType(): ArendExpr? {
        val patterns = patternList
        return if (patterns.size == 1) patterns[0].expr else null
    }

    override fun getAsPatterns(): List<Abstract.TypedReferable> {
        val patterns = patternList
        return if (patterns.size != 1) emptyList() else patterns[0].asPatterns
    }
}