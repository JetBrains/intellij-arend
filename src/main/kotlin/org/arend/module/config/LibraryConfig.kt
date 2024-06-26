package org.arend.module.config

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.library.classLoader.ClassLoaderDelegate
import org.arend.module.ArendRawLibrary
import org.arend.module.IntellijClassLoaderDelegate
import org.arend.module.ModuleLocation
import org.arend.naming.reference.Referable
import org.arend.prelude.Prelude.MODULE_PATH
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.util.getRelativeFile
import org.arend.util.getRelativePath
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.Range
import org.arend.util.Version
import org.arend.util.mapFirstNotNull


abstract class LibraryConfig(val project: Project) {
    open val sourcesDir: String
        get() = ""
    open val binariesDir: String?
        get() = null
    open val testsDir: String
        get() = ""
    open val extensionsDir: String?
        get() = null
    open val extensionMainClass: String?
        get() = null
    open val modules: List<ModulePath>?
        get() = null
    open val dependencies: List<LibraryDependency>
        get() = emptyList()
    open val version: Version?
        get() = null
    open val langVersion: Range<Version>
        get() = Range.unbound()

    abstract val name: String

    abstract val root: VirtualFile?

    open val localFSRoot: VirtualFile?
        get() = root?.let { if (it.isInLocalFileSystem) it else JarFileSystem.getInstance().getVirtualFileForJar(it) }

    private val additionalModules = HashMap<ModulePath, ArendFile>()
    val additionalNames = HashMap<String, ArrayList<PsiLocatedReferable>>()

    private fun findDir(dir: String) = root?.findFileByRelativePath(FileUtil.toSystemIndependentName(dir).removeSuffix("/"))

    open val sourcesDirFile: VirtualFile?
        get() = sourcesDir.let { if (it.isEmpty()) root else findDir(it) }

    open val testsDirFile: VirtualFile?
        get() = testsDir.let { if (it.isEmpty()) null else findDir(it) }

    val binariesDirFile: VirtualFile?
        get() = binariesDir?.let { findDir(it) }

    val binariesDirList: List<String>?
        get() = binariesDir?.let { FileUtil.toSystemIndependentName(it).removeSuffix("/").split('/') }

    open val isExternal: Boolean
        get() = false

    // Extensions

    val extensionDirFile: VirtualFile?
        get() = extensionsDir?.let(::findDir)

    val extensionMainClassFile: VirtualFile?
        get() {
            val className = extensionMainClass ?: return null
            return extensionDirFile?.getRelativeFile(className.split('.'), ".class")
        }

    val classLoaderDelegate: ClassLoaderDelegate?
        get() = extensionDirFile?.let { IntellijClassLoaderDelegate(it) }

    // Modules

    fun findModules(inTests: Boolean): List<ModulePath> {
        val modules = modules
        if (modules != null) {
            return modules
        }

        val dir = (if (inTests) testsDirFile else sourcesDirFile) ?: return emptyList()
        val result = ArrayList<ModulePath>()
        VfsUtil.iterateChildrenRecursively(dir, null) { file ->
            if (file.name.endsWith(FileUtils.EXTENSION)) {
                dir.getRelativePath(file, FileUtils.EXTENSION)?.let { result.add(ModulePath(it)) }
            }
            return@iterateChildrenRecursively true
        }
        return result
    }

    fun containsModule(modulePath: ModulePath) =
        modules?.any { it == modulePath } ?: (findArendFile(modulePath, withAdditional = false, withTests = false) != null)

    val additionalModulesSet: Set<ModulePath>
        get() = additionalModules.keys

    fun addAdditionalModule(modulePath: ModulePath, file: ArendFile) {
        additionalModules[modulePath] = file
    }

    fun clearAdditionalModules() {
        val maps = project.service<TypeCheckingService>().getTCRefMaps(Referable.RefKind.EXPR)
        for (file in additionalModules.values) {
            maps.remove(file.moduleLocation)
        }
        additionalModules.clear()
        additionalNames.clear()
    }

    private fun findParentDirectory(modulePath: ModulePath, inTests: Boolean): VirtualFile? {
        var dir = (if (inTests) testsDirFile else sourcesDirFile) ?: return null
        val list = modulePath.toList()
        var i = 0
        while (i < list.size - 1) {
            dir = dir.findChild(list[i++]) ?: return null
        }
        return dir
    }

    fun findArendDirectory(modulePath: ModulePath): PsiDirectory? {
        var dir = sourcesDirFile ?: return null
        for (name in modulePath.toList()) {
            dir = dir.findChild(name) ?: return null
        }
        return PsiManager.getInstance(project).findDirectory(dir)
    }

    fun findArendFile(modulePath: ModulePath, inTests: Boolean): ArendFile? =
        findParentDirectory(modulePath, inTests)?.findChild(modulePath.lastName + EXTENSION)?.let {
            PsiManager.getInstance(project).findFile(it) as? ArendFile
        }

    fun findArendFile(modulePath: ModulePath, withAdditional: Boolean, withTests: Boolean): ArendFile? =
        if (modulePath.size() == 0) {
            null
        } else {
            (if (withAdditional) additionalModules[modulePath] else null) ?:
                findArendFile(modulePath, false) ?: if (withTests) findArendFile(modulePath, true) else null
        }

    fun getArendDirectoryOrFile(modulePath: ModulePath, isTest: Boolean): PsiFileSystemItem? {
        val psiManager = PsiManager.getInstance(project)
        return if (modulePath.size() == 0) {
            if (isTest) {
                psiManager.findDirectory(testsDirFile!!)
            } else {
                psiManager.findDirectory(sourcesDirFile!!)
            }
        } else {
            getArendSubdirectoryOrFile(modulePath, isTest)
        }
    }

    fun getArendSubdirectoryOrFile(modulePath: ModulePath, isTest: Boolean): PsiFileSystemItem? {
        val psiManager = PsiManager.getInstance(project)
        if (modulePath == MODULE_PATH) {
            return project.service<TypeCheckingService>().prelude
        }
        return if (isTest) {
            (findArendFileOrDirectoryByModulePath(testsDirFile, modulePath) ?:
                findArendFileOrDirectoryByModulePath(sourcesDirFile, modulePath))?.let {
                    psiManager.findDirectory(it) ?: psiManager.findFile(it)
            } ?: findArendFile(modulePath, withAdditional = false, withTests = true)
        } else {
            (findArendFileOrDirectoryByModulePath(sourcesDirFile, modulePath) ?:
                findArendFileOrDirectoryByModulePath(testsDirFile, modulePath))?.let {
                psiManager.findDirectory(it) ?: psiManager.findFile(it)
            } ?: findArendFile(modulePath, withAdditional = false, withTests = true)
        }
    }

    fun findArendFileOrDirectory(modulePath: ModulePath, withAdditional: Boolean, withTests: Boolean): PsiFileSystemItem? {
        if (modulePath.size() == 0) {
            return findArendDirectory(modulePath)
        }
        if (withAdditional) {
            additionalModules[modulePath]?.let {
                return it
            }
        }

        val psiManager = PsiManager.getInstance(project)

        val srcDir = findParentDirectory(modulePath, false)
        srcDir?.findChild(modulePath.lastName + EXTENSION)?.let {
            val file = psiManager.findFile(it)
            if (file is ArendFile) {
                return file
            }
        }

        val testDir = if (withTests) findParentDirectory(modulePath, true) else null
        testDir?.findChild(modulePath.lastName + EXTENSION)?.let {
            val file = psiManager.findFile(it)
            if (file is ArendFile) {
                return file
            }
        }

        return (findArendFileOrDirectoryByModulePath(sourcesDirFile, modulePath) ?:
                findArendFileOrDirectoryByModulePath(testsDirFile, modulePath))?.let {
            psiManager.findDirectory(it)
        }
    }

    fun getFileModulePath(file: ArendFile): ModuleLocation? {
        file.generatedModuleLocation?.let {
            return it
        }

        val vFile = file.originalFile.viewProvider.virtualFile
        val sourcesPath = sourcesDirFile?.getRelativePath(vFile, FileUtils.EXTENSION)
        val path: List<String>
        val locationKind = if (sourcesPath != null) {
            path = sourcesPath
            ModuleLocation.LocationKind.SOURCE
        } else {
            path = testsDirFile?.getRelativePath(vFile, FileUtils.EXTENSION) ?: return null
            ModuleLocation.LocationKind.TEST
        }
        return ModuleLocation(name, isExternal, locationKind, ModulePath(path))
    }

    fun getFileLocationKind(file: ArendFile): ModuleLocation.LocationKind? = getFileModulePath(file)?.locationKind

    // Dependencies

    val availableConfigs: List<LibraryConfig>
        get() {
            val deps = dependencies
            if (deps.isEmpty()) {
                return listOf(this)
            }

            val libraryManager = project.service<TypeCheckingService>().libraryManager
            return listOf(this) + deps.mapNotNull { dep -> (libraryManager.getRegisteredLibrary(dep.name) as? ArendRawLibrary)?.config }
        }

    inline fun <T> forAvailableConfigs(f: (LibraryConfig) -> T?): T? {
        val t = f(this)
        if (t != null) {
            return t
        }

        val deps = dependencies
        if (deps.isEmpty()) {
            return null
        }

        val libraryManager = project.service<TypeCheckingService>().libraryManager
        return deps.mapFirstNotNull { dep -> (libraryManager.getRegisteredLibrary(dep.name) as? ArendRawLibrary)?.config?.let { f(it) } }
    }

    companion object {
        fun findArendFileOrDirectoryByModulePath(root: VirtualFile?, modulePath: ModulePath): VirtualFile? {
            val path = modulePath.toList()
            var curElement = root
            for (index in path.indices) {
                if (index == path.indices.last - 1 && path[index + 1] == EXTENSION.drop(1)) {
                    curElement = curElement?.findChild(path[index] + EXTENSION)
                    break
                } else if (index == path.indices.last) {
                    curElement = curElement?.findChild(path[index]) ?: curElement?.findChild(path[index] + EXTENSION)
                } else {
                    curElement = curElement?.findChild(path[index])
                }
            }
            return curElement
        }
    }
}
