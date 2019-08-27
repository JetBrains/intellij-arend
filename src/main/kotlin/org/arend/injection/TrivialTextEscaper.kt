package org.arend.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost


class TrivialTextEscaper(host: PsiLanguageInjectionHost) : LiteralTextEscaper<PsiLanguageInjectionHost>(host) {
    override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
        outChars.append(myHost.text, rangeInsideHost.startOffset, rangeInsideHost.endOffset)
        return true
    }

    override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange) =
        rangeInsideHost.startOffset + offsetInDecoded

    override fun isOneLine() = false
}