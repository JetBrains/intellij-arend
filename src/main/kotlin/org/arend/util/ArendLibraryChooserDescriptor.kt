package org.arend.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile


class ArendLibraryChooserDescriptor(chooseYamlConfig: Boolean, chooseZipLibrary: Boolean, chooseLibraryDirectory: Boolean, chooseDirectories: Boolean = false, chooseMultiple: Boolean = false) : FileChooserDescriptor(true, chooseDirectories, true, chooseZipLibrary, false, chooseMultiple) {
    private val fileFilter = Condition<VirtualFile> { file ->
        if (file.isDirectory) {
            chooseLibraryDirectory && file.findChild(FileUtils.LIBRARY_CONFIG_FILE) != null
        } else {
            val name = file.name
            if (chooseYamlConfig && name == FileUtils.LIBRARY_CONFIG_FILE) file.parent?.name?.let { FileUtils.isLibraryName(it) } == true
            else chooseZipLibrary && name.removeSuffixOrNull(FileUtils.ZIP_EXTENSION)?.let { FileUtils.isLibraryName(it) } == true
        }
    }

    init {
        withFileFilter(fileFilter)
    }

    override fun isFileSelectable(file: VirtualFile?) =
        file != null && !(file.`is`(VFileProperty.SYMLINK) && file.canonicalPath == null) && (isChooseFolders && file.isDirectory || fileFilter.value(file))
}