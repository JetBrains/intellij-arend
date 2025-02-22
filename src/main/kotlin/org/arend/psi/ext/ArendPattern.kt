package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

class ArendPattern(node: ASTNode) : ArendLamParam(node), Abstract.Pattern {
    val underscore: PsiElement?
        get() = findChildByType(UNDERSCORE)

    override fun getSequence(): List<ArendPattern> = getChildrenOfType()

    override fun getType(): ArendExpr? = childOfType()

    override fun getAsPattern(): ArendAsPattern? = childOfType()

    override fun getData() = this

    override fun isUnnamed() = underscore != null

    override fun isExplicit() = !hasChildOfType(LBRACE)

    override fun isTuplePattern() = hasChildOfType(LPAREN) && hasChildOfType(RPAREN)

    override fun getInteger(): Int? {
        val numberChild = findChildByType<PsiElement>(NUMBER) ?: findChildByType(NEGATIVE_NUMBER) ?: return null
        val text = numberChild.text
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

    override fun getFixity() = if (singleReferable == null) null else childOfType<ArendIPName>()?.fixity ?: Fixity.NONFIX

    override fun getSingleReferable(): ArendDefIdentifier? = childOfType()

    override fun getConstructorReference() = referenceElement?.unresolvedReference

    val referenceElement: ArendReferenceContainer?
        get() = getChild { it is ArendLongName || it is ArendIPName }
}