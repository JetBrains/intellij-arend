package org.arend.psi.ext

import com.intellij.openapi.util.TextRange

interface ArendReferenceElement : ArendCompositeElement {
    val referenceNameElement: ArendCompositeElement?
    val referenceName: String
    val rangeInElement: TextRange
    val longName: List<String>
}
