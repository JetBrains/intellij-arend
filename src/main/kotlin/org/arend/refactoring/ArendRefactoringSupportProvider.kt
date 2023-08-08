package org.arend.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.ArendChangeSignatureHandler
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler

class ArendRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?) =
        element is ArendDefIdentifier && !ArendGlobalReferableRenameHandler.Util.isDefIdentifierFromNsId(element) ||
                element is ArendRefIdentifier || element is ArendLetClause

    /* this method is never invoked on an element of type AliasIdentifier since rename refactorings of GlobalReferables with aliases are implemented via ArendGlobalReferableRenameHandler which does not use this method */
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?) =
            element is PsiLocatedReferable && element !is ArendFile

    override fun getChangeSignatureHandler() = ArendChangeSignatureHandler()
}