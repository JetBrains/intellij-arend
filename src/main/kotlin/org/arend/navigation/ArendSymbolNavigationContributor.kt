package org.arend.navigation

import org.arend.core.definition.Definition
import org.arend.prelude.Prelude
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendNamedElementIndex

class ArendSymbolNavigationContributor : ArendNavigationContributorBase<PsiReferable>(
    ArendNamedElementIndex.KEY,
    PsiReferable::class.java
) {
    override fun getPreludeDefinitions(): List<Definition> =
        if (Prelude.isInitialized()) mutableListOf<Definition>().apply {
            Prelude.forEach { def -> add(def) }
        } else emptyList()
}
