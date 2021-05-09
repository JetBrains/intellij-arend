package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.term.abs.Abstract

abstract class ArendPatternImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Pattern {
    override fun getData(): Any? = this

    abstract val atomPattern: ArendAtomPattern?

    abstract val defIdentifier: ArendDefIdentifier?

    abstract val longName: ArendLongName?

    open val asPattern: ArendAsPattern? = null

    open val expr: ArendExpr? = null

    open val atomPatternOrPrefixList: List<ArendAtomPatternOrPrefix> = emptyList()

    override fun isUnnamed() = atomPattern?.isUnnamed == true

    override fun isExplicit() = atomPattern?.isExplicit != false

    override fun getInteger() = atomPattern?.integer

    override fun getHeadReference(): Referable? =
        defIdentifier?.let {
            if (atomPatternOrPrefixList.isEmpty()) it else NamedUnresolvedReference(it, it.referenceName)
        } ?: longName?.referent ?: atomPattern?.headReference

    override fun getArguments(): List<Abstract.Pattern> =
        if (defIdentifier != null || longName != null) atomPatternOrPrefixList else atomPattern?.arguments ?: emptyList()

    override fun getType() = expr ?: atomPattern?.type

    override fun getAsPatterns(): List<Abstract.TypedReferable> =
        (atomPattern?.asPatterns ?: emptyList()) + listOfNotNull(asPattern)
}
