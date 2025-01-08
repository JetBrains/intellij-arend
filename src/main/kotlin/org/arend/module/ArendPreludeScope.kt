package org.arend.module

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude.MODULE_PATH
import org.arend.psi.ext.PsiModuleReferable
import org.arend.server.ArendServerService

class ArendPreludeScope(private val project: Project) : Scope {
    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        project.service<ArendServerService>().prelude?.let {
            result.add(PsiModuleReferable(listOf(it), MODULE_PATH))
        }
        return result
    }

    override fun getElements(context: Scope.ScopeContext?): Collection<Referable> = if (context == null || context == Scope.ScopeContext.STATIC) elements else emptyList()
}
