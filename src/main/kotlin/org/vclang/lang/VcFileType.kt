package org.vclang.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import org.vclang.ide.icons.VcIcons

import javax.swing.Icon

object VcFileType : LanguageFileType(VcLanguage) {
    override fun getName(): String = "vclang file"

    override fun getDescription(): String = "vclang language file"

    override fun getDefaultExtension(): String = "vc"

    override fun getIcon(): Icon = VcIcons.FILE
}
