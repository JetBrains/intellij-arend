package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference

interface ArendReferenceContainer: ArendCompositeElement {
    val unresolvedReference: UnresolvedReference?
    val resolvedInScope: Referable?
    val resolve: PsiElement?
}