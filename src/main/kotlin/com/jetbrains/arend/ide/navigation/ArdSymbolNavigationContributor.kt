package com.jetbrains.arend.ide.navigation

import com.jetbrains.arend.ide.psi.ext.PsiReferable
import com.jetbrains.arend.ide.psi.stubs.index.ArdNamedElementIndex

class ArdSymbolNavigationContributor : ArdNavigationContributorBase<PsiReferable>(
        ArdNamedElementIndex.KEY,
        PsiReferable::class.java
)
