package com.jetbrains.arend.ide

import com.intellij.openapi.fileTypes.LanguageFileType
import com.jetbrains.jetpad.vclang.util.FileUtils
import javax.swing.Icon

object ArdlFileType : LanguageFileType(com.jetbrains.arend.ide.ArdlLanguage.INSTANCE) {
    override fun getName(): String = "LibHeader"

    override fun getDefaultExtension(): String = FileUtils.LIBRARY_EXTENSION.drop(1)

    override fun getDescription(): String = "Library header"

    override fun getIcon(): Icon? = ArdIcons.AREND_LIB_FILE
}