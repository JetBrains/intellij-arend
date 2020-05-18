package org.arend.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import org.arend.psi.ArendAliasIdentifier
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendFile
import org.arend.psi.ArendLetClause
import org.arend.psi.ext.PsiLocatedReferable

class ArendRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?) =
        element is ArendDefIdentifier || element is ArendLetClause

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?) =
            element is PsiLocatedReferable && element !is ArendFile
                    || element is ArendAliasIdentifier
}