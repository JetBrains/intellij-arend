package org.arend.module

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude.MODULE_PATH
import org.arend.psi.ext.PsiModuleReferable
import org.arend.typechecking.TypeCheckingService

class ArendPreludeScope(private val project: Project) : Scope {
    override fun getElements(): Collection<Referable> {
        val psiManager = PsiManager.getInstance(project)
        val result = ArrayList<Referable>()
        project.service<TypeCheckingService>().prelude?.let { psiManager.findFile(it.virtualFile) }?.let {
            result.add(PsiModuleReferable(listOf(it), MODULE_PATH))
        }
        return result
    }

    override fun getElements(context: Scope.ScopeContext?): Collection<Referable> = if (context == null || context == Scope.ScopeContext.STATIC) elements else emptyList()
}
