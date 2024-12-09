package org.arend.module

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.arend.util.getRelativePath

class AllArendFilesScope(
    private val libraryConfig: LibraryConfig,
    private val extraPath: ModulePath = ModulePath(),
    private val isTest: Boolean = false,
    private val withPrelude: Boolean = true
) : Scope {
    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()

        val srcDir = libraryConfig.sourcesDirFile
        val testDir = libraryConfig.testsDirFile

        val dir = if (extraPath.size() == 0) {
            if (isTest) {
                testDir
            } else {
                srcDir
            }
        } else {
            if (isTest) {
                LibraryConfig.findArendFileOrDirectoryByModulePath(testDir, extraPath)
            } else {
                LibraryConfig.findArendFileOrDirectoryByModulePath(srcDir, extraPath)
            }
        }
        dir?.let { addArendFiles(it, result) }

        if (withPrelude && !isTest) {
            val psiManager = PsiManager.getInstance(libraryConfig.project)
            libraryConfig.project.service<TypeCheckingService>().prelude?.let { psiManager.findFile(it.virtualFile) }?.let {
                result.add(PsiModuleReferable(listOf(it), Prelude.MODULE_PATH))
            }
        }
        return result
    }

    private fun addArendFiles(root: VirtualFile, result: MutableList<Referable>) {
        val psiManager = PsiManager.getInstance(libraryConfig.project)
        val virtualFileVisitor = object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                val psiFile = psiManager.findFile(file)
                if (psiFile is ArendFile) {
                    result.add(createPsiModuleReferable(psiFile, root))
                }
                return true
            }
        }
        VfsUtilCore.visitChildrenRecursively(root, virtualFileVisitor)
    }

    private fun createPsiModuleReferable(file: ArendFile, root: VirtualFile): PsiModuleReferable {
        val listPath = root.getRelativePath(file.virtualFile, FileUtils.EXTENSION)
        return object : PsiModuleReferable(listOf(file), ModulePath(listPath ?: emptyList())) {
            override fun textRepresentation(): String {
                val fullName = listPath?.joinToString(".") ?: ""
                return fullName
            }
        }
    }

    override fun getElements(context: Scope.ScopeContext?): Collection<Referable> = if (context == null || context == Scope.ScopeContext.STATIC) elements else emptyList()

    override fun resolveNamespace(name: String) = AllArendFilesScope(libraryConfig, ModulePath(extraPath.toList() + name), isTest, false)
}
