package org.vclang.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.module.util.roots
import org.vclang.psi.VcFile
import org.vclang.psi.ext.PsiModuleReferable


class ModuleScope private constructor(private val module: Module, private val rootDir: VirtualFile?) : Scope {
    constructor(module: Module) : this(module, null)

    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        val psiManager = PsiManager.getInstance(module.project)
        for (root in if (rootDir != null) arrayOf(rootDir) else module.roots) {
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
        return result
    }

    override fun resolveNamespace(name: String, resolveModuleNames: Boolean): Scope {
        for (root in if (rootDir != null) arrayOf(rootDir) else module.roots) {
            for (file in root.children) {
                if (file.name == name) {
                    return if (file.isDirectory) ModuleScope(module, file) else EmptyScope.INSTANCE
                }
            }
        }
        return EmptyScope.INSTANCE
    }
}