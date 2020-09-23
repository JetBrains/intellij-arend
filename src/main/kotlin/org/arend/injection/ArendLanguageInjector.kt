package org.arend.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import org.arend.ArendLanguage


class ArendLanguageInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val rangesList = ((context as? PsiInjectionText)?.parent as? PsiInjectionTextFile)?.injectionRanges ?: return
        for (ranges in rangesList) {
            registrar.startInjecting(ArendLanguage.INSTANCE)
            for (range in ranges) {
                registrar.addPlace(null, null, context, range)
            }
            registrar.doneInjecting()
        }
    }

    override fun elementsToInjectIn() = listOf(PsiInjectionText::class.java)
}