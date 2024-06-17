package org.arend

import com.intellij.openapi.fileTypes.LanguageFileType
import org.arend.util.FileUtils

open class ArendFileType : LanguageFileType(ArendLanguage.INSTANCE) {
    override fun getName(): String = "Arend"

    override fun getDescription(): String = "Arend files"

    override fun getDefaultExtension(): String = FileUtils.EXTENSION.drop(1)

    override fun getIcon() = ArendIcons.AREND_FILE
}

object ArendFileTypeInstance : ArendFileType()