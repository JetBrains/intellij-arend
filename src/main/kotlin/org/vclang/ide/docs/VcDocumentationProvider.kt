package org.vclang.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.ext.adapters.DefinitionAdapter

class VcDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return if (element is DefinitionAdapter<*>) {
            buildString {
                getType(element)?.let { append("<b>$it</b> ") }
                element.getNameIdentifier()?.text?.let { append(it) }
                element.getContainingFile().originalFile.let {
                    append(" <i>defined in</i> ")
                    append((it as? VcFile)?.relativeModulePath ?: it.name)
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
