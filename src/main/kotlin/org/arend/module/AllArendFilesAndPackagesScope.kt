package org.arend.module

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.TypecheckRunConfigurationProducer.Companion.TEST_PREFIX
import org.arend.util.FileUtils
import org.arend.util.getRelativePath

class AllArendFilesAndPackagesScope(
    val libraryConfig: LibraryConfig,
    private val extraPath: ModulePath = ModulePath(),
    private val withPrelude: Boolean = true,
    private val withArendExtension: Boolean = true
) : Scope {
    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()

        val srcDir = libraryConfig.sourcesDirFile
        val testDir = libraryConfig.testsDirFile

        if (extraPath.size() == 0) {
            srcDir?.let { addArendFiles(it, it, result) }
            testDir?.let { addArendFiles(it, it.parent, result) }
        } else if (extraPath.firstName == TEST_PREFIX) {
            val dir = LibraryConfig.findArendFileOrDirectoryByModulePath(testDir, ModulePath(extraPath.toList().subList(1, extraPath.size())))
            dir?.let { addArendFiles(it, it, result) }
        } else {
            val dir = LibraryConfig.findArendFileOrDirectoryByModulePath(srcDir, extraPath)
            dir?.let { addArendFiles(it, it, result) }
        }

        if (withPrelude) {
            val psiManager = PsiManager.getInstance(libraryConfig.project)
            libraryConfig.project.service<TypeCheckingService>().prelude?.let { psiManager.findFile(it.virtualFile) }?.let {
                result.add(PsiModuleReferable(listOf(it), Prelude.MODULE_PATH))
            }
        }
        return result
    }

    private fun addArendFiles(root: VirtualFile, startFile: VirtualFile, result: MutableList<Referable>) {
        val psiManager = PsiManager.getInstance(libraryConfig.project)
        val virtualFileVisitor = object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                val psiFile = psiManager.findFile(file)
                if (psiFile is ArendFile) {
                    result.add(createPsiModuleReferable(psiFile, root, startFile))
                }
                val psiDirectory = psiManager.findDirectory(file)
                if (psiDirectory != null) {
                    result.add(createPsiModuleReferable(psiDirectory, root, startFile))
                }
                return true
            }
        }
        VfsUtilCore.visitChildrenRecursively(root, virtualFileVisitor)
    }

    private fun createPsiModuleReferable(psiItem: PsiFileSystemItem, root: VirtualFile, startFile: VirtualFile): PsiModuleReferable {
        val listPath = startFile.getRelativePath(psiItem.virtualFile, if (withArendExtension) FileUtils.EXTENSION else "")
            ?.apply {
                if (root != startFile) {
                    if (size > 1) {
                        set(0, TEST_PREFIX)
                    }
                }
            }
        return object : PsiModuleReferable(listOf(psiItem), ModulePath(listPath ?: emptyList())) {
            override fun textRepresentation(): String {
                val fullName = listPath?.joinToString(".") ?: ""
                return fullName
            }
        }
    }

    override fun getElements(kind: Referable.RefKind?): Collection<Referable> = if (kind == null || kind == Referable.RefKind.EXPR) elements else emptyList()

    override fun resolveNamespace(name: String, onlyInternal: Boolean) = AllArendFilesAndPackagesScope(libraryConfig, ModulePath(extraPath.toList() + name), false, withArendExtension)
}
