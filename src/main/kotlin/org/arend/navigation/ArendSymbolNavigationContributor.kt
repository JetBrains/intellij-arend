package org.arend.navigation

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.prelude.Prelude
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendNamedElementIndex
import org.arend.typechecking.TypeCheckingService

class ArendSymbolNavigationContributor : ArendNavigationContributorBase<PsiReferable>(
    ArendNamedElementIndex.KEY,
    PsiReferable::class.java
) {
    override fun getGeneratedItems(project: Project?): Map<String, List<PsiLocatedReferable>> {
        return emptyMap()
        /* TODO[server2]
        val service = project?.service<TypeCheckingService>()
        service ?: return emptyMap()
        val result = hashMapOf<String, List<PsiLocatedReferable>>()
        if (Prelude.isInitialized()) {
            Prelude.forEach { def -> service.getDefinitionPsiReferable(def)?.let { result[def.name] = listOf(it) } }
        }
        service.libraryManager.registeredLibraries?.forEach { lib ->
            (lib as? ArendRawLibrary)?.let { result.putAll(it.config.additionalNames) }
        }
        return result
        */
    }
}
