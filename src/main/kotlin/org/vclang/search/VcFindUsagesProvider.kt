package org.vclang.search

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.parser.fullName
import org.vclang.psi.*
import org.vclang.psi.ext.VcNamedElement

class VcFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = VcWordScanner()

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is VcNamedElement

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = when (element) {
        is VcDefClass -> "class"
        is VcClassField -> "class field"
        is VcDefClassView -> "class view"
        is VcDefInstance -> "class view instance"
        is VcClassImplement -> "implementation"
        is VcDefData -> "data"
        is VcConstructor -> "constructor"
        is VcDefFunction -> "function"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is Abstract.Definition -> element.fullName
        is VcNamedElement -> element.name ?: "<unnamed>"
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = when (element) {
        is Abstract.Definition -> if (useFullName) element.fullName else element.name!!
        is VcNamedElement -> element.name!!
        else -> ""
    }
}
