package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.ext.concrete.definition.ClassFieldKind
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.Abstract.FieldParameter


class ArendNameTele(node: ASTNode): ArendLamParam(node), Abstract.Parameter {
    val propertyKw: PsiElement?
        get() = findChildByType(PROPERTY_KW)

    val identifierOrUnknownList: List<ArendIdentifierOrUnknown>
        get() = getChildrenOfType()

    override fun getData() = this

    override fun isExplicit() = firstRelevantChild.elementType != LBRACE

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType(): ArendExpr? = childOfType()

    override fun isStrict() = hasChildOfType(STRICT_KW)

    override fun isProperty() = propertyKw != null
}

class ArendNameTeleUntyped(node: ASTNode): ArendSourceNodeImpl(node), Abstract.Parameter {
    val defIdentifier: ArendDefIdentifier
        get() = childOfTypeStrict()

    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList(): List<Referable?> = listOf(defIdentifier)

    override fun getType(): ArendExpr? = null

    override fun isStrict() = false

    override fun isProperty() = false
}

class ArendTypeTele(node: ASTNode): ArendSourceNodeImpl(node), Abstract.Parameter {
    val typedExpr: ArendTypedExpr?
        get() = childOfType()

    val lparen: PsiElement?
        get() = findChildByType(LPAREN)

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    val propertyKw: PsiElement?
        get() = findChildByType(PROPERTY_KW)

    override fun getData() = this

    override fun isExplicit() = firstRelevantChild.elementType != LBRACE

    override fun getReferableList(): List<Referable?> =
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier }?.ifEmpty { listOf(null) } ?: listOf(null)

    override fun getType() = typedExpr?.type ?: firstRelevantChild as? ArendExpr

    override fun isStrict() = hasChildOfType(STRICT_KW)

    override fun isProperty() = propertyKw != null
}

class ArendFieldTele(node: ASTNode): ArendSourceNodeImpl(node), FieldParameter {
    val classifyingKw: PsiElement?
        get() = findChildByType(CLASSIFYING_KW)

    val lbrace: PsiElement?
        get() = findChildByType(LBRACE)

    override fun getData() = this

    override fun isExplicit() = firstRelevantChild.elementType != LBRACE

    override fun getReferableList(): List<ArendFieldDefIdentifier> = getChildrenOfType()

    override fun getType(): ArendExpr? = childOfType()

    override fun isStrict() = false

    override fun getClassFieldKind() = when {
        hasChildOfType(PROPERTY_KW) -> ClassFieldKind.PROPERTY
        hasChildOfType(FIELD_KW) -> ClassFieldKind.FIELD
        else -> ClassFieldKind.ANY
    }

    override fun isClassifying() = classifyingKw != null

    override fun isCoerce() = findChildByType<PsiElement>(COERCE_KW) != null
}
