package org.arend.serialized

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ArendIcons
import org.arend.util.FileUtils

object ArendSerializedFileType : FileType {
    override fun getName() = "Arend serialized"

    override fun getDescription() = "Arend serialized files"

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    override fun isBinary() = true

    override fun isReadOnly() = false

    override fun getDefaultExtension() = FileUtils.SERIALIZED_EXTENSION.drop(1)

    override fun getIcon() = ArendIcons.AREND_SERIALIZED_FILE
}
