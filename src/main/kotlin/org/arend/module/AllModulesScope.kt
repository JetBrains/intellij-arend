package org.arend.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.psi.ext.PsiModuleReferable
import org.arend.util.allModules

class AllModulesScope(private val project: Project) : Scope {
    override fun getElements(): Collection<Referable> {
        val psiManager = PsiManager.getInstance(project)
        val result = ArrayList<Referable>()
        project.allModules.forEach {
            val moduleFile = ModuleRootManager.getInstance(it).contentRoots.getOrNull(0) ?: return@forEach
            val psiModuleFile = psiManager.findDirectory(moduleFile) ?: return@forEach
            result.add(PsiModuleReferable(listOf(psiModuleFile), ModulePath(listOf(it.name))))
        }
        return result
    }

    override fun getElements(kind: Referable.RefKind?): Collection<Referable> = if (kind == null || kind == Referable.RefKind.EXPR) elements else emptyList()
}
