package org.arend.psi.doc

import com.intellij.lang.ASTNode
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElementImpl
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendLongName
import org.arend.psi.getChildOfType
import org.arend.psi.getChildOfTypeStrict

class ArendDocReference(node: ASTNode) : ArendCompositeElementImpl(node) {
    val docReferenceText: ArendDocReferenceText?
        get() = getChildOfType()

    val longName: ArendLongName
        get() = getChildOfTypeStrict()

    override val scope: Scope =
        ArendDocComment.getScope(ancestor<ArendDocComment>()?.owner) ?: ancestor<ArendGroup>()?.scope ?: EmptyScope.INSTANCE
}
