package org.vclang.ide.navigation.goto

import org.vclang.lang.core.psi.ext.VcNamedElement
import org.vclang.lang.core.stubs.index.VcNamedElementIndex

class VcSymbolNavigationContributor : VcNavigationContributorBase<VcNamedElement>(
        VcNamedElementIndex.KEY,
        VcNamedElement::class.java
)
