package org.arend.psi.parser.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.reference.UnresolvedReference
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.impl.ArendLamParamImpl
import org.arend.psi.parser.api.ArendPattern
import org.arend.term.Fixity
import org.arend.term.concrete.Concrete

class ArendPatternImpl(node: ASTNode) : ArendLamParamImpl(node), ArendPattern {

    override fun getSequence(): List<ArendPattern> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, ArendPattern::class.java)
    }

    override fun getType(): ArendExpr? {
        return PsiTreeUtil.getChildOfType(this, ArendExpr::class.java)
    }

    override fun getAsPatterns(): List<ArendAsPattern> {
        return listOfNotNull(PsiTreeUtil.getChildOfType(this, ArendAsPattern::class.java))
    }

    override fun getData(): ArendPatternImpl = this

    override fun isUnnamed(): Boolean {
        return getUnderscore() != null
    }

    override fun isExplicit(): Boolean {
        return !hasChildOfType(ArendElementTypes.LBRACE)
    }

    override fun isTuplePattern(): Boolean = hasChildOfType(ArendElementTypes.LPAREN) && hasChildOfType(ArendElementTypes.RPAREN)

    override fun getInteger(): Int? {
        val numberChild = findChildByType<PsiElement>(ArendElementTypes.NUMBER) ?: findChildByType(ArendElementTypes.NEGATIVE_NUMBER) ?: return null
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

    override fun getFixity(): Fixity? {
        if (singleReferable == null) {
            return null
        }
        val ip = findChildByType<ArendIPName>(ArendElementTypes.IP_NAME)
        return ip?.infix?.let { Fixity.INFIX } ?: ip?.postfix?.let { Fixity.POSTFIX } ?: Fixity.NONFIX
    }

    override fun getSingleReferable(): UnresolvedReference? {
        val longName = findChildByType<ArendLongName>(ArendElementTypes.LONG_NAME) ?: findChildByType<ArendIPName>(ArendElementTypes.IP_NAME)
        return longName?.unresolvedReference
    }

    override fun getUnderscore(): PsiElement? {
        return findChildByType(ArendElementTypes.UNDERSCORE)
    }

    override fun getReferenceElement(): ArendReferenceContainer? {
        return findChildByType(ArendElementTypes.LONG_NAME) ?: findChildByType(ArendElementTypes.IP_NAME)
    }
}