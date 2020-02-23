package org.arend.serialized

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.arend.util.FileUtils
import javax.swing.Icon

object ArendSerializedFileType : FileType {
    override fun getName() = "Arend serialized"

    override fun getDescription() = "Arend serialized files"

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    override fun isBinary() = true

    override fun isReadOnly() = false

    override fun getDefaultExtension() = FileUtils.SERIALIZED_EXTENSION.drop(1)

    override fun getIcon(): Icon? = AllIcons.FileTypes.JavaClass
}
