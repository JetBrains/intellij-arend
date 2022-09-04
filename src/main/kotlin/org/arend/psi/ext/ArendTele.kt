package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.ext.concrete.expr.SigmaFieldKind
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.Abstract.FieldParameter


class ArendNameTele(node: ASTNode): ArendLamParam(node), Abstract.Parameter {
    val identifierOrUnknownList: List<ArendIdentifierOrUnknown>
        get() = getChildrenOfType()

    override fun getData() = this

    override fun isExplicit() = firstRelevantChild.elementType != LBRACE

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType(): ArendExpr? = getChildOfType()

    override fun isStrict() = hasChildOfType(STRICT_KW)
}

class ArendNameTeleUntyped(node: ASTNode): ArendSourceNodeImpl(node), Abstract.Parameter {
    val defIdentifier: ArendDefIdentifier
        get() = getChildOfTypeStrict()

    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList(): List<Referable?> = listOf(defIdentifier)

    override fun getType(): ArendExpr? = null

    override fun isStrict() = false
}

class ArendTypeTele(node: ASTNode): ArendSourceNodeImpl(node), Abstract.Parameter {
    val typedExpr: ArendTypedExpr?
        get() = getChildOfType()

    val lparen: PsiElement?
        get() = findChildByType(LPAREN)

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    override fun getData() = this

    override fun isExplicit() = firstRelevantChild.elementType != LBRACE

    override fun getReferableList(): List<Referable?> =
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier }?.ifEmpty { listOf(null) } ?: listOf(null)

    override fun getType() = typedExpr?.type ?: firstRelevantChild as? ArendExpr

    override fun isStrict() = hasChildOfType(STRICT_KW)
}

class ArendSigmaTypeTele(node: ASTNode): ArendSourceNodeImpl(node), Abstract.SigmaParameter {
    val typedExpr: ArendTypedExpr?
        get() = getChildOfType()

    val propertyKw: PsiElement?
        get() = findChildByType(PROPERTY_KW)

    override fun getData() = this

    override fun isExplicit() = false

    override fun getReferableList(): List<Referable?> =
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier }?.ifEmpty { listOf(null) } ?: listOf(null)

    override fun getType(): ArendExpr? = typedExpr?.type ?: firstRelevantChild as? ArendExpr

    override fun isStrict() = false

    override fun getFieldKind(): SigmaFieldKind =
        if (propertyKw != null) SigmaFieldKind.PROPERTY else SigmaFieldKind.ANY
}

class ArendFieldTele(node: ASTNode): ArendSourceNodeImpl(node), FieldParameter {
    val classifyingKw: PsiElement?
        get() = findChildByType(CLASSIFYING_KW)

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    override fun getData() = this

    override fun isExplicit() = firstRelevantChild.elementType != LBRACE

    override fun getReferableList(): List<ArendFieldDefIdentifier> = getChildrenOfType()

    override fun getType(): ArendExpr? = getChildOfType()

    override fun isStrict() = false

    override fun isClassifying() = classifyingKw != null

    override fun isCoerce() = findChildByType<PsiElement>(COERCE_KW) != null
}
