package com.jetbrains.arend.ide.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ArdElementTypes.INFIX
import com.jetbrains.arend.ide.psi.ArdElementTypes.POSTFIX

class ArdRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return element is ArdDefIdentifier || element is ArdFieldDefIdentifier || element is ArdLetClause
    }

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        if (context is LeafPsiElement && (context.elementType == INFIX || context.elementType == POSTFIX)) return false

        return element is ArdDefClass || element is ArdDefFunction || element is ArdDefData ||
                element is ArdClassField || element is ArdClassFieldSyn || element is ArdConstructor
    }
}