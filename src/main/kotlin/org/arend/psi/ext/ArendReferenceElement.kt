package org.arend.psi.ext

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference

interface ArendReferenceElement : ArendReferenceContainer {
    val rangeInElement: TextRange

    override val resolve: PsiElement?
        get() = reference?.resolve()

    override val resolvedInScope: Referable?
        get() = scope.resolveName(referenceName)

    override val unresolvedReference: UnresolvedReference?
        get() = null
}
