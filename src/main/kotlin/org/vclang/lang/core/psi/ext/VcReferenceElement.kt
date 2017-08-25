package org.vclang.lang.core.psi.ext

interface VcReferenceElement : VcCompositeElement {
    val referenceNameElement: VcCompositeElement?
    val referenceName: String?
}
