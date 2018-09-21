package org.arend.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.INFIX
import org.arend.psi.ArendElementTypes.POSTFIX

class ArendRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return element is ArendDefIdentifier || element is ArendFieldDefIdentifier || element is ArendLetClause
    }

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        if (context is LeafPsiElement && (context.elementType == INFIX || context.elementType == POSTFIX)) return false

        return element is ArendDefClass || element is ArendDefFunction || element is ArendDefData ||
                element is ArendClassField || element is ArendClassFieldSyn || element is ArendConstructor || element is ArendDefModule
    }
}