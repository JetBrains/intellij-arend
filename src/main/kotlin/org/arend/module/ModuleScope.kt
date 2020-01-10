package org.arend.module

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.util.FileUtils


class ModuleScope private constructor(private val libraryConfig: LibraryConfig, private val rootDirs: List<VirtualFile>?) : Scope {
    constructor(libraryConfig: LibraryConfig) : this(libraryConfig, null)

    private fun calculateRootDirs() = rootDirs ?: libraryConfig.availableConfigs.mapNotNull { it.sourcesDirFile }

    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        val psiManager = PsiManager.getInstance(libraryConfig.project)
        val names = ArrayList<String>()
        for (root in calculateRootDirs()) {
            for (file in root.children) {
                if (file.isDirectory) {
                    val name = file.name
                    if (FileUtils.isModuleName(name)) {
                        val dir = psiManager.findDirectory(file)
                        if (dir != null) {
                            names.add(name)
                            result.add(PsiModuleReferable(listOf(dir), ModulePath(ArrayList(names))))
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

    override fun resolveNamespace(name: String, onlyInternal: Boolean): Scope {
        val newRootDirs = (calculateRootDirs()).mapNotNull { root ->
            for (file in root.children) {
                if (file.name == name) {
                    return@mapNotNull if (file.isDirectory) file else null
                }
            }
            return@mapNotNull null
        }
        return if (newRootDirs.isEmpty()) EmptyScope.INSTANCE else ModuleScope(libraryConfig, newRootDirs)
    }
}