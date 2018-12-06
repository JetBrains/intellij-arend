package org.arend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.module.util.availableConfigs
import org.arend.module.util.libraryConfig
import org.arend.module.util.sourcesDirFile
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.util.FileUtils


class ModuleScope private constructor(private val module: Module, private val rootDirs: List<VirtualFile>?) : Scope {
    constructor(module: Module) : this(module, null)

    private fun calculateRootDirs() = rootDirs ?: module.libraryConfig?.availableConfigs?.mapNotNull { it.sourcesDirFile } ?: emptyList()

    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        val psiManager = PsiManager.getInstance(module.project)
        for (root in calculateRootDirs()) {
            for (file in root.children) {
                if (file.isDirectory) {
                    val name = file.name
                    if (FileUtils.isModuleName(name)) {
                        val dir = psiManager.findDirectory(file)
                        if (dir != null) {
                            result.add(PsiModuleReferable(listOf(dir), ModulePath(name)))
                        }
                    }
                } else if (file.name.endsWith(FileUtils.EXTENSION)) {
                    val arendFile = psiManager.findFile(file) as? ArendFile
                    arendFile?.modulePath?.let { result.add(PsiModuleReferable(listOf(arendFile), it)) }
                }
            }
        }
        if (rootDirs == null) {
            result.add(ModuleReferable(Prelude.MODULE_PATH))
        }
        return result
    }

    override fun resolveNamespace(name: String): Scope {
        val newRootDirs = (rootDirs ?: calculateRootDirs()).mapNotNull { root ->
            for (file in root.children) {
                if (file.name == name) {
                    return@mapNotNull if (file.isDirectory) file else null
                }
            }
            return@mapNotNull null
        }
        return if (newRootDirs.isEmpty()) EmptyScope.INSTANCE else ModuleScope(module, newRootDirs)
    }
}