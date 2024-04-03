package org.arend.module

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.typechecking.TypeCheckingService
import java.io.File

class AllArendFilesScope(private val libraryConfig: LibraryConfig, private val extraPath: String? = null, private val withPrelude: Boolean = true) : Scope {
    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()

        val srcDir = getDirFile(libraryConfig.sourcesDir)
        srcDir?.let { addArendFiles(it, extraPath, result) }

        val testDir = getDirFile(libraryConfig.testsDir)
        testDir?.let { addArendFiles(it, extraPath, result) }

        if (withPrelude) {
            val psiManager = PsiManager.getInstance(libraryConfig.project)
            libraryConfig.project.service<TypeCheckingService>().prelude?.let { psiManager.findFile(it.virtualFile) }?.let {
                result.add(PsiModuleReferable(listOf(it), Prelude.MODULE_PATH))
            }
        }

        return result
    }

    private fun getDirFile(dir: String): VirtualFile? {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
            File((libraryConfig.root?.path ?: "") + File.separator + dir + (extraPath ?: ""))
        )
    }

    private fun addArendFiles(virtualDir: VirtualFile, extraPath: String?, result: MutableList<Referable>) {
        val psiManager = PsiManager.getInstance(libraryConfig.project)
        val virtualFileVisitor = object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                val psiFile = psiManager.findFile(file)
                if (psiFile is ArendFile) {
                    result.add(object : Referable {
                        override fun textRepresentation(): String {
                            return psiFile.fullName.removePrefix("${extraPath?.replace(File.separator, ".")?.removePrefix(".")}.")
                        }

                        override fun getUnderlyingReferable(): Referable {
                            return psiFile
                        }
                    })
                }
                return true
            }
        }
        VfsUtilCore.visitChildrenRecursively(virtualDir, virtualFileVisitor)
    }

    override fun getElements(kind: Referable.RefKind?): Collection<Referable> = if (kind == null || kind == Referable.RefKind.EXPR) elements else emptyList()

    override fun resolveNamespace(name: String, onlyInternal: Boolean) = AllArendFilesScope(libraryConfig, extraPath?.let { "$it${File.separator}$name" } ?: "${File.separator}$name")
}
