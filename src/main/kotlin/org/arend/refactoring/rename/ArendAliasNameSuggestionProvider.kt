package org.arend.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.arend.psi.ext.ArendAlias

class ArendAliasNameSuggestionProvider: NameSuggestionProvider {
    override fun getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement?, result: MutableSet<String>): SuggestedNameInfo? {
        if (nameSuggestionContext is ArendAlias) {
            val identifier = nameSuggestionContext.aliasIdentifier
            if (identifier != null) {
                result.clear()
                result.add(identifier.id.text)
                return SuggestedNameInfo.NULL_INFO
            }
        }
        return null
    }
}