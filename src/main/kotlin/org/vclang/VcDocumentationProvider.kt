package org.vclang

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable

class VcDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return if (element is PsiGlobalReferable) {
            buildString {
                getType(element)?.let { append("<b>$it</b> ") }
                append(element.textRepresentation())
                element.getContainingFile().originalFile.let {
                    append(" <i>defined in</i> ")
                    append((it as? VcFile)?.fullName ?: it.name)
                }
            }
        } else {
            null
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
            generateDoc(element, originalElement)

    private fun getType(element: PsiElement): String? = when (element) {
        is VcDefClass -> "class"
        is VcClassField -> "class field"
        is VcDefClassView -> "class view"
        is VcDefInstance -> "class view instance"
        is VcClassImplement -> "implementation"
        is VcDefData -> "data"
        is VcConstructor -> "constructor"
        is VcDefFunction -> "function"
        else -> null
    }
}
