package com.jetbrains.arend.ide.navigation

import com.jetbrains.arend.ide.psi.ext.PsiReferable
import com.jetbrains.arend.ide.psi.stubs.index.ArdGotoClassIndex

class ArdClassNavigationContributor : ArdNavigationContributorBase<PsiReferable>(
        ArdGotoClassIndex.KEY,
        PsiReferable::class.java
)
