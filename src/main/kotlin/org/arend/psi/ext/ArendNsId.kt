package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.psi.firstRelevantChild
import org.arend.term.NameRenaming


class ArendNsId(node: ASTNode) : ArendCompositeElementImpl(node), NameRenaming {
    val refIdentifier: ArendRefIdentifier
        get() = childOfTypeStrict()

    val defIdentifier: ArendDefIdentifier?
        get() = childOfType()

    val prec: ArendPrec?
        get() = childOfType()

    val asKw: PsiElement?
        get() = findChildByType(ArendElementTypes.AS_KW)

    override fun getScopeContext() = ArendStatCmd.getScopeContext(firstRelevantChild)

    override fun getOldReference() = refIdentifier.referent

    override fun getName() = defIdentifier?.referenceName

    override fun getPrecedence() = ReferableBase.calcPrecedence(prec)
}