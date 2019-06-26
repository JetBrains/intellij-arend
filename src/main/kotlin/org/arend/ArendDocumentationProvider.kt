package org.arend

import com.google.common.html.HtmlEscapers
import com.intellij.codeInsight.documentation.DocumentationManagerUtil.createHyperlink
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.arend.term.abs.Abstract
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.ReferableAdapter


private fun String.htmlEscape(): String = HtmlEscapers.htmlEscaper().escape(this)

class ArendDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) =
        if (element !is PsiReferable) {
            null
        } else {
            buildString {
                wrap(DEFINITION_START, DEFINITION_END) {
                    generateDefinition(element)
                }

                val docComments = extractDocComments(element)
                if (docComments.isNotEmpty()) {
                    wrap(CONTENT_START, CONTENT_END) {
                        append(docComments)
                    }
                }

                wrap(CONTENT_START, CONTENT_END) {
                    generateContent(element, originalElement)
                }
            }
        }

    private fun getElementJustBeforeReferable(element: PsiReferable): PsiElement? {
        val parent = element.parent
        return if (parent is ArendClassStat || parent is ArendStatement) parent.prevSibling else null
    }

    private fun extractDocComments(element: PsiReferable): String =
            buildString {
                var prevElement = getElementJustBeforeReferable(element)
                while (prevElement is PsiWhiteSpace || prevElement is PsiComment && (prevElement.tokenType in arrayOf(ArendElementTypes.LINE_COMMENT, ArendElementTypes.BLOCK_COMMENT, ArendElementTypes.BLOCK_COMMENT_END))) {
                    prevElement = prevElement.prevSibling
                }
                if (prevElement is PsiComment && prevElement.tokenType == ArendElementTypes.LINE_DOC_TEXT) {
                    append(prevElement.text)
                } else {
                    while (prevElement is PsiComment && prevElement.tokenType != ArendElementTypes.BLOCK_DOC_COMMENT_START) {
                        append(prevElement.text)
                        prevElement = prevElement.prevSibling
                    }
                }
            }

    private fun StringBuilder.generateDefinition(element: PsiReferable) {
        wrapTag("b") {
            append(element.textRepresentation().htmlEscape())
        }

        append(getParametersList(element).htmlEscape())
        append(getResultType(element).htmlEscape())
    }

    private fun getParametersList(element: PsiReferable) =
        buildString {
            val parameters: List<Abstract.Parameter> =
                (element as? Abstract.ParametersHolder)?.parameters ?: emptyList()

            for (parameter in parameters) {
                if (parameter is PsiElement) {
                    append(" ${parameter.text}")
                }
            }
        }

    private fun getResultType(element: PsiReferable) =
        buildString {
            val resultType = element.psiElementType
            resultType?.let { append(" : ${it.text}") }
        }

    private fun StringBuilder.generateContent(element: PsiElement, originalElement: PsiElement?) {
        wrapTag("em") {
            getType(element)?.let { append(it.htmlEscape()) }
        }

        (element as? ReferableAdapter<*>)?.getPrec()?.let {
            append(", ")
            append(it.firstChild.text.drop(1))
            it.number?.let { priority ->
                append(" ")
                append(priority.text)
            }
        }

        getSourceFileName(element, originalElement)
            ?.run(String::htmlEscape)
            ?.let { fileName ->
                StringBuilder().also {
                    createHyperlink(it, fileName, fileName, false)
                    append(", defined in $it")
                }
            }
    }

    private fun getType(element: PsiElement): String? = when (element) {
        is ArendDefClass -> "class"
        is ArendClassField, is ArendFieldDefIdentifier -> "field"
        is ArendDefInstance -> "instance"
        is ArendClassImplement -> "implementation"
        is ArendDefData -> "data"
        is ArendConstructor -> "data constructor"
        is ArendDefFunction -> "function"
        is ArendLetClause -> "let"
        is ArendDefIdentifier -> if (element.parent is ArendLetClause) "let" else "variable"
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
