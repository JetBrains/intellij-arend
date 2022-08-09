package org.arend.psi.parser.api

import com.intellij.psi.PsiElement
import org.arend.naming.reference.UnresolvedReference
import org.arend.psi.ArendAsPattern
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLamParam
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.term.abs.Abstract

interface ArendPattern : ArendCompositeElement, Abstract.Pattern, ArendLamParam {
    override fun getSequence(): List<ArendPattern>

    override fun getType(): ArendExpr?

    override fun getAsPatterns(): List<ArendAsPattern>

    override fun getSingleReferable(): UnresolvedReference?

    fun getUnderscore() : PsiElement?

    fun getReferenceElement() : ArendReferenceContainer?
}
