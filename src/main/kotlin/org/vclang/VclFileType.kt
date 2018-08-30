package org.vclang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.jetbrains.jetpad.vclang.util.FileUtils
import javax.swing.Icon

object VclFileType : LanguageFileType(VclLanguage.INSTANCE) {
    override fun getName(): String = "LibHeader"

    override fun getDefaultExtension(): String = FileUtils.LIBRARY_EXTENSION.drop(1)

    override fun getDescription(): String = "Library header"

    override fun getIcon(): Icon? = VcIcons.VCLANG_FILE
}