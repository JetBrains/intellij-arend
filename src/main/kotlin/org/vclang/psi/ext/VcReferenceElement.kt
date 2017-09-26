package org.vclang.psi.ext

// TODO[abstract]: Do we need this?
interface VcReferenceElement : VcCompositeElement {
    val referenceNameElement: VcCompositeElement?
    val referenceName: String?
}
