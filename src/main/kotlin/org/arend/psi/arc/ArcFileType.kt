package org.arend.psi.arc

import com.intellij.openapi.fileTypes.FileType
import org.arend.ArendIcons
import org.arend.util.FileUtils

open class ArcFileType : FileType {
    override fun getName(): String = "Arc"

    override fun getDescription(): String = "Arc files"

    override fun getDefaultExtension(): String = FileUtils.SERIALIZED_EXTENSION.drop(1)

    override fun getIcon() = ArendIcons.AREND_FILE

    override fun isBinary(): Boolean = true

    companion object {
        val INSTANCE = ArcFileType()
    }
}