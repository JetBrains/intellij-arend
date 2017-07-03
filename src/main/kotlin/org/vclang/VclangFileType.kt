package org.vclang

import com.intellij.openapi.fileTypes.LanguageFileType

import javax.swing.Icon

object VclangFileType : LanguageFileType(VclangLanguage) {
    override fun getName(): String = "vclang file"

    override fun getDescription(): String = "vclang language file"

    override fun getDefaultExtension(): String = "vc"

    override fun getIcon(): Icon = VclangIcons.FILE
}
