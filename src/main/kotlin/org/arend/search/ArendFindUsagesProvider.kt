package org.arend.search

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.arend.psi.ext.*

class ArendFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = ArendWordScanner()

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is PsiReferable || element is ArendAliasIdentifier

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = when (element) {
        is ArendDefClass -> "class"
        is ArendDefModule -> "module"
        is ArendClassFieldBase<*> -> "class field"
        is ArendDefInstance -> "class instance"
        is ArendDefData -> "data"
        is ArendConstructor -> "constructor"
        is ArendDefFunction -> "function"
        is ArendAliasIdentifier -> getType(element.parent.parent)
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is PsiLocatedReferable -> element.fullName
        is PsiReferable -> element.name ?: "<unnamed>"
        is ArendAliasIdentifier -> (if (element.parent is ArendAlias && element.parent.parent is PsiLocatedReferable) (element.parent.parent as? PsiLocatedReferable)?.fullName else null) ?: ""
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = when (element) {
        is PsiLocatedReferable -> if (useFullName) element.fullName else element.textRepresentation()
        is PsiReferable -> element.name ?: ""
        is ArendAliasIdentifier -> getNodeText(element.parent.parent, useFullName)
        else -> ""
    }
}
