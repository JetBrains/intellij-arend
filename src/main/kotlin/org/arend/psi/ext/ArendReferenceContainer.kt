package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.naming.reference.UnresolvedReference

interface ArendReferenceContainer : ArendCompositeElement {
    val referenceNameElement: ArendCompositeElement?
    val referenceName: String
    val longName: List<String>
    val unresolvedReference: UnresolvedReference?
    val resolve: PsiElement?
}