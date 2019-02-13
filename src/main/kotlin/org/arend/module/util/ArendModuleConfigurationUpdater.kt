package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.config.BINARIES
import org.arend.module.config.DEFAULT_BINARIES_DIR
import org.arend.module.config.DEFAULT_SOURCES_DIR
import org.arend.module.config.SOURCES
import org.arend.util.FileUtils

open class ArendModuleConfigurationUpdater(private val sourceDir: String, private val binariesDir: String) : ModuleBuilder.ModuleConfigurationUpdater() {
    constructor(): this(DEFAULT_SOURCES_DIR, DEFAULT_BINARIES_DIR)

    protected open fun sourceDir(moduleRoot: VirtualFile, project: Project): String = sourceDir

    protected open fun binariesDir(moduleRoot: VirtualFile, project: Project): String = binariesDir

    override fun update(module: Module, rootModel: ModifiableRootModel) {
        rootModel.inheritSdk()
        val contentEntry = rootModel.contentEntries.singleOrNull() ?: return
        val projectRoot = contentEntry.file ?: return
        val srcDir = sourceDir(projectRoot, rootModel.project)
        val srcAbsoluteDir = toAbsolute(projectRoot.path, srcDir)
        val outDir = binariesDir(projectRoot, rootModel.project)

        if (projectRoot.fileSystem.findFileByPath(srcAbsoluteDir) == null) {
            VfsUtil.createDirectories(srcAbsoluteDir)
        }

        contentEntry.addSourceFolder(VfsUtil.pathToUrl(srcAbsoluteDir), false)
        contentEntry.addExcludeFolder(VfsUtil.pathToUrl(toAbsolute(projectRoot.path, outDir)))

        if (projectRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) == null) {
            projectRoot.createChildData(projectRoot, FileUtils.LIBRARY_CONFIG_FILE).setBinaryContent(
                ("$SOURCES: ${toRelative(projectRoot.path, srcDir)}\n" +
                 "$BINARIES: ${toRelative(projectRoot.path, outDir)}").toByteArray())
        }
    }

    private companion object {
        private fun toAbsolute(root: String, path: String): String {
            val iPath = FileUtil.toSystemIndependentName(path)
            return if (FileUtil.isAbsolute(path)) iPath else "$root/$iPath"
        }

        private fun toRelative(root: String, path: String): String {
            val iPath = FileUtil.toSystemIndependentName(path)
            return if (iPath.startsWith(root)) iPath.substring(root.length + (if (root.endsWith('/')) 0 else 1)) else iPath
        }
    }
}