package org.vclang.search

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.fullName

class VcFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = VcWordScanner()

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is PsiReferable

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = when (element) {
        is VcDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is VcClassField -> "class field"
        is VcClassFieldSyn -> "class field synonym"
        is VcDefInstance -> "class instance"
        is VcDefData -> "data"
        is VcConstructor -> "constructor"
        is VcDefFunction -> "function"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is PsiGlobalReferable -> element.fullName
        is PsiReferable -> element.name ?: "<unnamed>"
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = when (element) {
        is PsiGlobalReferable -> if (useFullName) element.fullName else element.textRepresentation()
        is PsiReferable -> element.name ?: ""
        else -> ""
    }
}
