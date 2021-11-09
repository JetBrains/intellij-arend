package org.arend

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.psi.*
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.xml.util.XmlStringUtil
import org.arend.ext.module.LongName
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.naming.scope.Scope
import org.arend.parser.ParserMixin.*
import org.arend.psi.*
import org.arend.psi.doc.ArendDocCodeBlock
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.doc.ArendDocReference
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.term.abs.Abstract


private fun String.htmlEscape(): String = XmlStringUtil.escapeString(this, true)

private const val FULL_PREFIX = "\\full:"

class ArendDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, false)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, true)

    override fun getDocumentationElementForLink(psiManager: PsiManager?, link: String, context: PsiElement?): PsiElement? {
        val longName = link.removePrefix(FULL_PREFIX)
        val scope = ArendDocComment.getScope((context as? ArendDocComment)?.owner ?: context) ?: return null
        val ref = RedirectingReferable.getOriginalReferable(Scope.resolveName(scope, LongName.fromString(longName).toList()))
        return if (ref is PsiReferable && longName.length != link.length) ref.documentation else ref as? PsiElement
    }

    private fun generateDoc(element: PsiElement, originalElement: PsiElement?, withDocComments: Boolean): String? {
        val ref = element as? PsiReferable ?: (element as? ArendDocComment)?.owner ?: return null
        return buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                }
            }

            wrapTag("body") {
            wrap(DEFINITION_START, DEFINITION_END) {
                generateDefinition(ref)
            }

            wrap(CONTENT_START, CONTENT_END) {
                generateContent(ref, originalElement)
            }

            if (withDocComments) {
                val doc = element as? ArendDocComment ?: ref.documentation
                if (doc != null) {
                    append(CONTENT_START)
                    generateDocComments(ref, doc, element is ArendDocComment)
                    append(CONTENT_END)
                }
            }
        } } }
    }

    private fun StringBuilder.generateDocComments(element: PsiReferable, doc: PsiElement, full: Boolean) {
        for (docElement in doc.childrenWithLeaves) {
            val elementType = (docElement as? LeafPsiElement)?.elementType
            when {
                elementType == DOC_LBRACKET -> append("[")
                elementType == DOC_RBRACKET -> append("]")
                elementType == DOC_TEXT -> html(docElement.text)
                elementType == WHITE_SPACE -> append(" ")
                elementType == DOC_CODE -> append("<code>${docElement.text.htmlEscape()}</code>")
                elementType == DOC_PARAGRAPH_SEP -> {
                    append("<br>")
                    if (!full) {
                        append("<a href=\"psi_element://$FULL_PREFIX${element.refName}\">more...</a>")
                        return
                    }
                }
                docElement is ArendDocReference -> {
                    val longName = docElement.longName
                    val link = longName.refIdentifierList.joinToString(".") { it.id.text.htmlEscape() }
                    val isLink = longName.resolve is PsiReferable
                    if (isLink) {
                        append("<a href=\"psi_element://$link\">")
                    }

                    append("<code>")
                    val text = docElement.docReferenceText
                    if (text != null) {
                        for (child in text.childrenWithLeaves) {
                            if (child.elementType == DOC_TEXT) html(child.text)
                        }
                    } else {
                        append(link)
                    }
                    append("</code>")

                    if (isLink) {
                        append("</a>")
                    }
                }
                docElement is ArendDocCodeBlock -> {
                    append("<pre>")
                    for (child in docElement.childrenWithLeaves) {
                        when ((child as? LeafPsiElement)?.elementType) {
                            DOC_CODE_LINE -> html(child.text)
                            WHITE_SPACE -> append("\n")
                        }
                    }
                    append("</pre>")
                }
            }
        }
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
        is ArendDefModule -> "module"
        is ArendDefMeta -> "meta"
        is ArendLetClause -> "let"
        is ArendDefIdentifier -> when (element.parent) {
            is ArendLetClause -> "let"
            is ArendLevelParams, is ArendMetaLevels -> "level parameter"
            else -> "variable"
        }
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
