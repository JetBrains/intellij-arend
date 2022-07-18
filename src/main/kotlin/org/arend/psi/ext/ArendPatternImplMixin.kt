package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.util.castSafelyTo
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract

abstract class ArendPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Pattern {
    override fun getData(): Any? = this

    open val asPattern: ArendAsPattern? = null

    open val expr: ArendExpr? = null

    val singlePattern get() = if (this is ArendPattern) atomPatternList.singleOrNull() else null

    override fun getSingleReferable(): Referable? {
        return singlePattern?.singleReferable
    }

    override fun getInteger(): Int? {
        return singlePattern?.integer
    }

    override fun isTuplePattern(): Boolean {
        return singlePattern?.isTuplePattern == true
    }

    override fun isUnnamed() = singlePattern?.isUnnamed == true

    override fun isExplicit() = singlePattern?.isExplicit != false

    override fun getSequence(): List<Abstract.Pattern> {
        return if (this is ArendPattern) atomPatternList else emptyList()
    }

    override fun getType() = expr ?: singlePattern?.type

    override fun getAsPatterns(): List<Abstract.TypedReferable> =
        (singlePattern?.asPatterns ?: emptyList()) + listOfNotNull(asPattern)
}
