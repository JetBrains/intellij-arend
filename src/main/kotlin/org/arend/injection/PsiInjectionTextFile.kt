package org.arend.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl


class PsiInjectionTextFile(provider: FileViewProvider) : PsiFileImpl(InjectionTextFileElementType, InjectionTextFileElementType, provider) {
    var injectionRanges: List<List<TextRange>> = emptyList()

    val hasInjection: Boolean
        get() = injectionRanges.isNotEmpty()

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitFile(this)
    }

    override fun getFileType() = InjectionTextFileType
}