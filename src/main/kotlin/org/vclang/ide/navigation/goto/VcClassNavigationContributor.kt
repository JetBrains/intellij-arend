package org.vclang.ide.navigation.goto

import org.vclang.lang.core.psi.ext.VcNamedElement
import org.vclang.lang.core.stubs.index.VcGotoClassIndex

class VcClassNavigationContributor : VcNavigationContributorBase<VcNamedElement>(
        VcGotoClassIndex.KEY,
        VcNamedElement::class.java
)
