package org.arend.injection

import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.OwnBufferLeafPsiElement


class PsiInjectionText(text: CharSequence) : OwnBufferLeafPsiElement(InjectionTextFileElementType.INJECTION_TEXT, text), PsiLanguageInjectionHost {
    override fun isValidHost() = (parent as? PsiInjectionTextFile)?.hasInjection == true

    override fun updateText(text: String) = this

    override fun createLiteralTextEscaper() = TrivialTextEscaper(this)

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitElement(this)
    }
}