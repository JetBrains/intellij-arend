package org.arend.navigation

import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendNamedElementIndex

class ArendSymbolNavigationContributor : ArendNavigationContributorBase<PsiReferable>(
        ArendNamedElementIndex.KEY,
        PsiReferable::class.java
)
