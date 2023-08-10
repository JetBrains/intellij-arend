package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfType
import org.arend.psi.hasChildOfType

class ArendClassStat(node: ASTNode) : ArendCompositeElementImpl(node) {
    val classField: ArendClassField?
        get() = childOfType()

    val classImplement: ArendClassImplement?
        get() = childOfType()

    val overriddenField: ArendOverriddenField?
        get() = childOfType()

    val coClause: ArendCoClause?
        get() = childOfType()

    val definition: ArendDefinition<*>?
        get() = childOfType()

    val group: ArendGroup?
        get() = childOfType()

    val isDefault: Boolean
        get() = hasChildOfType(ArendElementTypes.DEFAULT_KW)

    val propertyKw: PsiElement?
        get() = findChildByType(ArendElementTypes.PROPERTY_KW)
}