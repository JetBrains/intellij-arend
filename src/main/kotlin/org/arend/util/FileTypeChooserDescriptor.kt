package org.arend.util

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.vfs.VirtualFile


class FileTypeChooserDescriptor(private val extensions: List<String>, chooseFolders: Boolean = false, chooseMultiple: Boolean = false) : FileChooserDescriptor(true, chooseFolders, false, false, false, chooseMultiple) {
    fun isFileAcceptable(file: VirtualFile?, acceptHiddenFiles: Boolean, acceptFolders: Boolean): Boolean {
        if (file == null || !acceptHiddenFiles && FileElement.isFileHidden(file)) {
            return false
        }

        if (file.isDirectory) {
            return acceptFolders
        }

        val name = file.name
        return extensions.any { name.endsWith(it) }
    }

    override fun isFileVisible(file: VirtualFile?, showHiddenFiles: Boolean) = isFileAcceptable(file, showHiddenFiles, true)

    override fun isFileSelectable(file: VirtualFile?) = isFileAcceptable(file, true, isChooseFolders)
}