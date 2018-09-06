package com.jetbrains.arend.ide.psi.ext

interface ArdReferenceElement : ArdCompositeElement {
    val referenceNameElement: ArdCompositeElement?
    val referenceName: String
}
