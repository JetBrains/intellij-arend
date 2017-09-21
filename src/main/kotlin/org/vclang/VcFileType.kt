package org.vclang

import com.intellij.openapi.fileTypes.LanguageFileType

import javax.swing.Icon

object VcFileType : LanguageFileType(VcLanguage) {
    val defaultCacheExtension = "vcc"

    override fun getName(): String = "Vclang"

    override fun getDescription(): String = "Vclang files"

    override fun getDefaultExtension(): String = "vc"

    override fun getIcon(): Icon? = VcIcons.VCLANG_FILE
}
