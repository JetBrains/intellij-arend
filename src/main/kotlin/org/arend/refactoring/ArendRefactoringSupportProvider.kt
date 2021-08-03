package org.arend.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendFile
import org.arend.psi.ArendLetClause
import org.arend.psi.ArendRefIdentifier
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.changeSignature.ArendChangeSignatureHandler

class ArendRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?) =
        element is ArendDefIdentifier || element is ArendRefIdentifier || element is ArendLetClause

    /* this method is never invoked on an element of type AliasIdentifier since rename refactorings of GlobalReferables with aliases are implemented via ArendGlobalReferableRenameHandler which does not use this method */
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?) =
            element is PsiLocatedReferable && element !is ArendFile

    override fun getChangeSignatureHandler() = ArendChangeSignatureHandler()
}