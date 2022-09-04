package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildOfType
import org.arend.psi.hasChildOfType

class ArendClassStat(node: ASTNode) : ArendCompositeElementImpl(node) {
    val classField: ArendClassField?
        get() = getChildOfType()

    val classImplement: ArendClassImplement?
        get() = getChildOfType()

    val overriddenField: ArendOverriddenField?
        get() = getChildOfType()

    val coClause: ArendCoClause?
        get() = getChildOfType()

    val definition: ArendDefinition<*>?
        get() = getChildOfType()

    val group: ArendGroup?
        get() = getChildOfType()

    val isDefault: Boolean
        get() = hasChildOfType(ArendElementTypes.DEFAULT_KW)

    val propertyKw: PsiElement?
        get() = findChildByType(ArendElementTypes.PROPERTY_KW)
}