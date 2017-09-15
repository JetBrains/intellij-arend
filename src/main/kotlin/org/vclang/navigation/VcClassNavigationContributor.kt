package org.vclang.navigation

import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.stubs.index.VcGotoClassIndex

class VcClassNavigationContributor : VcNavigationContributorBase<PsiReferable>(
        VcGotoClassIndex.KEY,
        PsiReferable::class.java
)
