package org.vclang.lang.core.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.vclang.lang.VcFileType
import org.vclang.lang.VcLanguage


class VcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VcLanguage) {
    override fun getFileType(): FileType = VcFileType
}
