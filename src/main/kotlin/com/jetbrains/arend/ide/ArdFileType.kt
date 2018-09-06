package com.jetbrains.arend.ide

import com.intellij.openapi.fileTypes.LanguageFileType
import com.jetbrains.jetpad.vclang.util.FileUtils

import javax.swing.Icon

object ArdFileType : LanguageFileType(com.jetbrains.arend.ide.ArdLanguage.INSTANCE) {
    override fun getName(): String = "Arend"

    override fun getDescription(): String = "Arend files"

    override fun getDefaultExtension(): String = FileUtils.EXTENSION.drop(1)

    override fun getIcon(): Icon? = ArdIcons.AREND_FILE
}
