package org.arend.search

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.fullName

class ArendFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = ArendWordScanner()

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is PsiReferable

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = when (element) {
        is ArendDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is ArendDefModule -> "module"
        is ArendClassField -> "class field"
        is ArendClassFieldSyn -> "class field synonym"
        is ArendDefInstance -> "class instance"
        is ArendDefData -> "data"
        is ArendConstructor -> "constructor"
        is ArendDefFunction -> "function"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is PsiLocatedReferable -> element.fullName
        is PsiReferable -> element.name ?: "<unnamed>"
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = when (element) {
        is PsiLocatedReferable -> if (useFullName) element.fullName else element.textRepresentation()
        is PsiReferable -> element.name ?: ""
        else -> ""
    }
}
