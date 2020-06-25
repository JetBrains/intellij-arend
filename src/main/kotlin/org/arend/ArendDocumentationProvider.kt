package org.arend

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil
import org.arend.naming.reference.FieldReferable
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.term.abs.Abstract


private fun String.htmlEscape(): String = XmlStringUtil.escapeString(this)

class ArendDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, false)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, true)

    private fun generateDoc(element: PsiElement, originalElement: PsiElement?, withDocComments: Boolean) =
        if (element !is PsiReferable) {
            null
        } else buildString { wrapTag("html") { wrapTag("body") {
            wrap(DEFINITION_START, DEFINITION_END) {
                generateDefinition(element)
            }

            wrap(CONTENT_START, CONTENT_END) {
                generateContent(element, originalElement)
            }

            if (withDocComments) {
                generateDocComments(element)
            }
        } } }

    private fun StringBuilder.generateDocComments(element: PsiReferable) {
        val doc = getDocumentation(element) ?: return
        append(CONTENT_START)
        html(doc.text)
        append(CONTENT_END)
    }

    private fun StringBuilder.html(text: String) = append(text.htmlEscape())

    private fun StringBuilder.generateDefinition(element: PsiReferable) {
        wrapTag("b") {
            html(element.textRepresentation())
        }
        (element as? ReferableAdapter<*>)?.getAlias()?.aliasIdentifier?.id?.text?.let {
            html(" $it")
        }

        for (parameter in (element as? Abstract.ParametersHolder)?.parameters ?: emptyList()) {
            if (parameter is PsiElement) {
                html(" ${parameter.text}")
            }
        }

        element.psiElementType?.let { html(" : ${it.text}") }
    }

    private fun StringBuilder.generatePrecedence(prec: ArendPrec) {
        append(", ")
        append(prec.firstChild.text.drop(1))
        prec.number?.let { priority ->
            append(" ")
            append(priority.text)
        }
    }

    private fun StringBuilder.generateContent(element: PsiElement, originalElement: PsiElement?) {
        wrapTag("em") {
            getType(element)?.let { append(it) }
        }
        getSuperType(element)?.let { append(it) }

        (element as? ReferableAdapter<*>)?.getPrec()?.let { generatePrecedence(it) }

        (element as? ReferableAdapter<*>)?.getAlias()?.let { alias ->
            alias.prec?.let {
                generatePrecedence(it)
                html(" ${alias.aliasIdentifier?.id?.text ?: ""}")
            }
        }

        if (element !is PsiFile) {
            getSourceFileName(element, originalElement)?.let {
                val fileName = it.htmlEscape()
                append(", defined in ")
                wrapTag("b") {
                    append(fileName)
                }
                // createHyperlink(this, fileName, fileName, false)
            }
        }
    }

    private fun getType(element: PsiElement): String? = when (element) {
        is ArendDefClass -> if (element.isRecord) "record" else "class"
        is FieldReferable -> "field"
        is ArendDefInstance -> "instance"
        is ArendClassImplement -> "implementation"
        is ArendDefData -> "data"
        is ArendConstructor -> "constructor"
        is ArendDefFunction, is ArendCoClauseDef -> "function"
        is ArendDefModule -> if (element.moduleKw != null) "module" else "meta"
        is ArendLetClause -> "let"
        is ArendDefIdentifier -> if (element.parent is ArendLetClause) "let" else "variable"
        is PsiFile -> "file"
        else -> null
    }

    private fun getSuperType(element: PsiElement): String? = when (element) {
        is FieldReferable ->
            (element.locatedReferableParent as? ArendDefClass)?.let {
                " of ${if (it.isRecord) "record" else "class"} <b>${it.name ?: return@let null}</b>"
            }
        is ArendConstructor -> (element.locatedReferableParent as? ArendDefData)?.name?.let { " of data <b> $it </b>" }
        else -> null
    }

    private fun getSourceFileName(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is PsiLocatedReferable) {
            val file = element.containingFile.originalFile
            if (file != originalElement?.containingFile?.originalFile) {
                return (file as? ArendFile)?.fullName ?: file.name
            }
        }

        return null
    }

    private inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
        this.append(prefix)
        body()
        this.append(postfix)
    }

    private inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
        wrap("<$tag>", "</$tag>", body)
    }
}
