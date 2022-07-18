package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.util.castSafelyTo
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

abstract class ArendAtomPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomPattern, Abstract.Pattern {
    override fun getData(): Any? = this

    override fun isUnnamed(): Boolean {
        return underscore != null
    }

    override fun isExplicit(): Boolean {
        return lbrace == null
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
        return null
    }

    override fun getSingleReferable(): Referable? {
        return longName?.unresolvedReference
    }

    override fun isTuplePattern(): Boolean {
        return lparen != null && rparen != null
    }

    override fun getSequence(): List<Abstract.Pattern> {
        return patternList
    }

    override fun getType(): ArendExpr? {
        return null
    }

    override fun getAsPatterns(): List<Abstract.TypedReferable> {
        return emptyList()
    }
}