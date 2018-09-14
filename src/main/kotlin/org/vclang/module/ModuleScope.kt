package org.vclang.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.prelude.Prelude
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.module.util.libraryConfig
import org.vclang.module.util.sourcesDirFile
import org.vclang.psi.VcFile
import org.vclang.psi.ext.PsiModuleReferable


class ModuleScope private constructor(private val module: Module, private val rootDirs: List<VirtualFile>?) : Scope {
    constructor(module: Module) : this(module, null)

    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        val psiManager = PsiManager.getInstance(module.project)
        for (root in rootDirs ?: module.libraryConfig?.sourcesDirFile?.let { listOf(it) } ?: emptyList()) {
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
                    (psiManager.findFile(file) as? VcFile)?.let { result.add(PsiModuleReferable(listOf(it), it.modulePath)) }
                }
            }
        }
        if (rootDirs == null) {
            result.add(ModuleReferable(Prelude.MODULE_PATH))
        }
        return result
    }

    override fun resolveNamespace(name: String): Scope {
        val newRootDirs = (rootDirs ?: module.libraryConfig?.sourcesDirFile?.let { listOf(it) } ?: emptyList()).mapNotNull { root ->
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