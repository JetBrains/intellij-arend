package org.vclang.navigation

import org.vclang.psi.ext.VcNamedElement
import org.vclang.psi.stubs.index.VcNamedElementIndex

class VcSymbolNavigationContributor : VcNavigationContributorBase<VcNamedElement>(
        VcNamedElementIndex.KEY,
        VcNamedElement::class.java
)
