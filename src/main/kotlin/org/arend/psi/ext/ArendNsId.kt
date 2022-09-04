package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.naming.reference.Referable
import org.arend.psi.ArendElementTypes
import org.arend.psi.getChildOfType
import org.arend.psi.getChildOfTypeStrict
import org.arend.term.NameRenaming


class ArendNsId(node: ASTNode) : ArendCompositeElementImpl(node), NameRenaming {
    val refIdentifier: ArendRefIdentifier
        get() = getChildOfTypeStrict()

    val defIdentifier: ArendDefIdentifier?
        get() = getChildOfType()

    val prec: ArendPrec?
        get() = getChildOfType()

    val asKw: PsiElement?
        get() = findChildByType(ArendElementTypes.AS_KW)

    override fun getOldReference(): Referable = refIdentifier.referent

    override fun getName() = defIdentifier?.referenceName

    override fun getPrecedence() = ReferableBase.calcPrecedence(prec)
}