package org.arend.navigation

import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendGotoClassIndex

class ArendClassNavigationContributor : ArendNavigationContributorBase<PsiReferable>(
        ArendGotoClassIndex.KEY,
        PsiReferable::class.java
)
