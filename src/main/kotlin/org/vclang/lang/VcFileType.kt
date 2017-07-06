package org.vclang.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import org.vclang.ide.icons.VcIcons

import javax.swing.Icon

object VcFileType : LanguageFileType(VcLanguage) {
    override fun getName(): String = "Vclang file"

    override fun getDescription(): String = "Vclang Files"

    override fun getDefaultExtension(): String = "vc"

    override fun getIcon(): Icon? = VcIcons.FILE
}
