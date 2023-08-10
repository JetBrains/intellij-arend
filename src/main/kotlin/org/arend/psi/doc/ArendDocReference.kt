package org.arend.psi.doc

import com.intellij.lang.ASTNode
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElementImpl
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendLongName
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict

class ArendDocReference(node: ASTNode) : ArendCompositeElementImpl(node) {
    val docReferenceText: ArendDocReferenceText?
        get() = childOfType()

    val longName: ArendLongName
        get() = childOfTypeStrict()

    override val scope: Scope =
        ArendDocComment.getScope(ancestor<ArendDocComment>()?.owner) ?: ancestor<ArendGroup>()?.scope ?: EmptyScope.INSTANCE
}
