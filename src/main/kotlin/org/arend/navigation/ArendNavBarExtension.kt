package org.arend.navigation

import com.intellij.ide.navigationToolbar.DefaultNavBarExtension
import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.arend.ArendLanguage
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendDefinition
import org.arend.psi.parentOfType
import javax.swing.Icon

class ArendNavBarExtension : StructureAwareNavBarModelExtension() {
    override val language: Language
        get() = ArendLanguage.INSTANCE

    override fun getPresentableText(element: Any?): String? {
        val ancestor = (element as? PsiElement?)?.ancestor<ArendDefinition<*>>()
        if (ancestor != null) {
            return ancestor.getName()
        }
        return DefaultNavBarExtension().getPresentableText(element)
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement? {
        val ancestor = psiElement.ancestor<ArendDefinition<*>>()
        if (ancestor != null) {
            return ancestor
        }
        return DefaultNavBarExtension().adjustElement(psiElement)
    }

    override fun getParent(psiElement: PsiElement?): PsiElement? {
        val ancestor = psiElement?.ancestor<ArendDefinition<*>>()
        if (ancestor != null && ancestor.getRefLongName().size() > 1) {
            return ancestor.parentOfType<ArendDefinition<*>>()
        }
        return DefaultNavBarExtension().getParent(psiElement)
    }

    override fun getIcon(element: Any?): Icon? {
        val ancestor = (element as? PsiElement?)?.ancestor<ArendDefinition<*>>()
        if (ancestor != null) {
            return ancestor.getIcon(0)
        }
        return DefaultNavBarExtension().getIcon(element)
    }
}
