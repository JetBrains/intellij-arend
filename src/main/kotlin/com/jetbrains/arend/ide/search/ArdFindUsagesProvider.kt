package com.jetbrains.arend.ide.search

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable
import com.jetbrains.arend.ide.psi.ext.PsiReferable
import com.jetbrains.arend.ide.psi.ext.fullName

class ArdFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = ArdWordScanner()

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is PsiReferable

    override fun getHelpId(element: PsiElement): String = HelpID.FIND_OTHER_USAGES

    override fun getType(element: PsiElement): String = when (element) {
        is ArdDefClass -> if (element.fatArrow == null) "class" else "class synonym"
        is ArdClassField -> "class field"
        is ArdClassFieldSyn -> "class field synonym"
        is ArdDefInstance -> "class instance"
        is ArdDefData -> "data"
        is ArdConstructor -> "constructor"
        is ArdDefFunction -> "function"
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
