package org.arend.navigation

import com.intellij.ide.navigationToolbar.DefaultNavBarExtension
import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import org.arend.ArendLanguage
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendDefinition
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.psi.parentOfType
import javax.swing.Icon

class ArendNavBarExtension : StructureAwareNavBarModelExtension() {
    override val language: Language
        get() = ArendLanguage.INSTANCE

    override fun getPresentableText(element: Any?): String? {
        val ancestor = (element as? PsiElement?)?.ancestor<ArendDefinition<*>>()
        return if (ancestor != null) {
            ancestor.getName()
        } else if (element is ArendFile) {
            element.fullName
        } else if (element is ArendRefIdentifier) {
            getPresentableText(element.resolve)
        } else {
            DefaultNavBarExtension().getPresentableText(element)
        }
    }

    override fun adjustElement(psiElement: PsiElement): PsiElement? {
        return psiElement.ancestor<ArendDefinition<*>>()
            ?: if (psiElement.containingFile is ArendFile) {
                psiElement.containingFile
            } else {
                DefaultNavBarExtension().adjustElement(psiElement)
            }
    }

    override fun getParent(psiElement: PsiElement?): PsiElement? {
        val ancestor = psiElement?.ancestor<ArendDefinition<*>>()
        return if (ancestor != null && ancestor.getRefLongName().size() > 1) {
            ancestor.parentOfType<ArendDefinition<*>>()
        } else if (psiElement?.containingFile is ArendFile) {
            psiElement.containingFile
        } else {
            DefaultNavBarExtension().getParent(psiElement)
        }
    }

    override fun getIcon(element: Any?): Icon? {
        val ancestor = (element as? PsiElement?)?.ancestor<ArendDefinition<*>>()
        return if (ancestor != null) {
            ancestor.getIcon(0)
        } else {
            DefaultNavBarExtension().getIcon(element)
        }
    }
}
