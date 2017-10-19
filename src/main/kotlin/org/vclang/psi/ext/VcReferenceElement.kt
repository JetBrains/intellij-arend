package org.vclang.psi.ext

interface VcReferenceElement : VcCompositeElement {
    val referenceNameElement: VcCompositeElement?
    val referenceName: String
}
