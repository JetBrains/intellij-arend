package org.arend.navigation

import com.intellij.openapi.project.Project
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendGotoClassIndex

class ArendClassNavigationContributor : ArendNavigationContributorBase<PsiReferable>(
    ArendGotoClassIndex.KEY,
    PsiReferable::class.java
) {
    override fun getGeneratedItems(project: Project?): Map<String, List<PsiLocatedReferable>> = emptyMap()
}
