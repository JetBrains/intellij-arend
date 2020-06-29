package org.arend.psi.doc

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendLongName
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElementImpl
import org.arend.psi.ext.impl.ArendGroup

class ArendDocReference(node: ASTNode) : ArendCompositeElementImpl(node) {
    val docReferenceText: ArendDocReferenceText? =
        PsiTreeUtil.getChildOfType(this, ArendDocReferenceText::class.java)

    val longName: ArendLongName =
        notNullChild<ArendLongName>(PsiTreeUtil.getChildOfType(this, ArendLongName::class.java))

    override val scope: Scope =
        ArendDocComment.getScope(ancestor<ArendDocComment>()?.owner) ?: ancestor<ArendGroup>()?.scope ?: EmptyScope.INSTANCE
}
