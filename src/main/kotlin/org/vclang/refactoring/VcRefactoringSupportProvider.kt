package org.vclang.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.vclang.psi.*
import org.vclang.psi.VcElementTypes.INFIX
import org.vclang.psi.VcElementTypes.POSTFIX

class VcRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return element is VcDefIdentifier || element is VcFieldDefIdentifier || element is VcLetClause
    }

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        if (context is LeafPsiElement && (context.elementType == INFIX || context.elementType == POSTFIX)) return false

        return element is VcDefClass || element is VcDefFunction || element is VcDefData ||
                element is VcClassField || element is VcClassFieldSyn || element is VcConstructor
    }
}