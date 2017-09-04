package org.vclang

import com.intellij.openapi.fileTypes.LanguageFileType

import javax.swing.Icon

object VcFileType : LanguageFileType(VcLanguage) {
    val defaultCacheExtension = "vcc"

    override fun getName(): String = "Vclang file"

    override fun getDescription(): String = "Vclang Files"

    override fun getDefaultExtension(): String = "vc"

    override fun getIcon(): Icon? = VcIcons.VCLANG_FILE
}
