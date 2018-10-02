package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.util.FileUtils

open class ArendModuleConfigurationUpdater : ModuleBuilder.ModuleConfigurationUpdater() {
    protected open fun sourceDir(moduleRoot: VirtualFile, project: Project): String = "src"

    protected open fun outputDir(moduleRoot: VirtualFile, project: Project): String = FileUtil.join(moduleRoot.url, ".output")

    override fun update(module: Module, rootModel: ModifiableRootModel) {
        rootModel.inheritSdk()
        val contentEntry = rootModel.contentEntries.singleOrNull()
        if (contentEntry != null) {
            val projectRoot = contentEntry.file ?: return
            val srcDir = sourceDir(projectRoot, rootModel.project)
            val outDir = outputDir(projectRoot, rootModel.project)

            if (projectRoot.findChild(srcDir) == null) {
                projectRoot.createChildDirectory(null, srcDir)
            }

            if (projectRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) == null) {
                val configFile = projectRoot.createChildData(projectRoot, FileUtils.LIBRARY_CONFIG_FILE)
                configFile.setBinaryContent("sourcesDir: ${srcDir}".toByteArray())
            }
            contentEntry.addSourceFolder(FileUtil.join(projectRoot.url, srcDir), false)
            contentEntry.addExcludeFolder(outDir)
        }
    }
}