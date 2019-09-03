package org.arend.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.vfs.VirtualFile


class FileTypeChooserDescriptor(private val extensions: List<String>, chooseFolders: Boolean = false, chooseMultiple: Boolean = false) : FileChooserDescriptor(true, chooseFolders, false, false, false, chooseMultiple) {
    override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean): Boolean {
        if (file == null || !showHiddenFiles && FileElement.isFileHidden(file)) {
            return false
        }

        if (file.isDirectory) {
            return isChooseFolders
        }

        val name = file.name
        return extensions.any { name.endsWith(it) }
    }

    override fun isFileSelectable(file: VirtualFile?) = isFileVisible(file, true)
}