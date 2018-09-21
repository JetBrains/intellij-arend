package org.arend.psi.ext

interface ArendReferenceElement : ArendCompositeElement {
    val referenceNameElement: ArendCompositeElement?
    val referenceName: String
}
