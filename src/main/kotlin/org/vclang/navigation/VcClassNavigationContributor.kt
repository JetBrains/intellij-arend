package org.vclang.navigation

import org.vclang.psi.ext.VcNamedElement
import org.vclang.psi.stubs.index.VcGotoClassIndex

class VcClassNavigationContributor : VcNavigationContributorBase<VcNamedElement>(
        VcGotoClassIndex.KEY,
        VcNamedElement::class.java
)
