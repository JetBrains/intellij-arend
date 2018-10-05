package org.arend.module.util

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.util.FileUtils

open class ArendModuleConfigurationUpdater constructor () : ModuleBuilder.ModuleConfigurationUpdater() {
    private var sourceDir = ArendModuleBuilder.DEFAULT_SOURCE_DIR
    private var outputDir = ArendModuleBuilder.DEFAULT_OUTPUT_DIR

    constructor(sourceDir: String, outputDir: String): this() {
        this.sourceDir = sourceDir
        this.outputDir = outputDir
    }

    protected open fun sourceDir(moduleRoot: VirtualFile, project: Project): String = sourceDir

    protected open fun outputDir(moduleRoot: VirtualFile, project: Project): String = outputDir

    override fun update(module: Module, rootModel: ModifiableRootModel) {
        rootModel.inheritSdk()
        val contentEntry = rootModel.contentEntries.singleOrNull()
        if (contentEntry != null) {
            val projectRoot = contentEntry.file ?: return
            val srcDir = sourceDir(projectRoot, rootModel.project)
            val outDir = outputDir(projectRoot, rootModel.project)

                if (projectRoot.fileSystem.findFileByPath(ArendModuleBuilder.toAbsolute(projectRoot.path, srcDir)) == null) {
                    VfsUtil.createDirectories(ArendModuleBuilder.toAbsolute(projectRoot.path, srcDir))
                }

            /*
            val relSrcDir = ArendModuleBuilder.toRelative(projectRoot.path, srcDir)
            if (relSrcDir != null) {
                if (projectRoot.findChild(relSrcDir) == null)
                    projectRoot.createChildDirectory(null, relSrcDir)
            }*/
            contentEntry.addSourceFolder(ArendModuleBuilder.toAbsolute(projectRoot.path, srcDir), false)

            if (projectRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) == null) {
                val configFile = projectRoot.createChildData(projectRoot, FileUtils.LIBRARY_CONFIG_FILE)
                configFile.setBinaryContent(("sourcesDir: ${srcDir}\n"+"outputDir: ${outDir}").toByteArray())
            }

            contentEntry.addExcludeFolder(ArendModuleBuilder.toAbsolute(projectRoot.path, outDir))
        }
    }
}