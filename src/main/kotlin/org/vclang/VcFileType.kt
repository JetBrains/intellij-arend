package org.vclang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.jetbrains.jetpad.vclang.util.FileUtils

import javax.swing.Icon

object VcFileType : LanguageFileType(VcLanguage.INSTANCE) {
    override fun getName(): String = "Vclang"

    override fun getDescription(): String = "Vclang files"

    override fun getDefaultExtension(): String = FileUtils.EXTENSION.drop(1)

    override fun getIcon(): Icon? = VcIcons.VCLANG_FILE
}
