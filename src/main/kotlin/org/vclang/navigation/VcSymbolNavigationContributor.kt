package org.vclang.navigation

import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.stubs.index.VcNamedElementIndex

class VcSymbolNavigationContributor : VcNavigationContributorBase<PsiReferable>(
        VcNamedElementIndex.KEY,
        PsiReferable::class.java
)
