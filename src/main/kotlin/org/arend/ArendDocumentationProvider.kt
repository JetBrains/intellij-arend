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

class ArendDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, false)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, true)

    override fun getDocumentationElementForLink(psiManager: PsiManager?, link: String?, context: PsiElement?): PsiElement? {
        val scope = ArendDocComment.getScope(context) ?: return null
        return RedirectingReferable.getOriginalReferable(Scope.Utils.resolveName(scope, LongName.fromString(link).toList())) as? PsiElement
    }

    private fun generateDoc(element: PsiElement, originalElement: PsiElement?, withDocComments: Boolean) =
        if (element !is PsiReferable) {
            null
        } else buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                }
            }

            wrapTag("body") {
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

/*
    private fun StringBuilder.generateDocComments(element: PsiReferable, originalElement: PsiElement?) {
        val parent = element.parent
        var curElement = if (parent is ArendClassStat || parent is ArendStatement) parent.prevSibling else null
        while (curElement is PsiWhiteSpace || curElement is PsiComment && (curElement.tokenType in arrayOf(ArendElementTypes.LINE_COMMENT, ArendElementTypes.BLOCK_COMMENT, ArendElementTypes.BLOCK_COMMENT_END))) {
            curElement = curElement.prevSibling
        }

        // <a href="psi_element://Main"><code>Main</code></a>
        append(CONTENT_START)
        // append("<p>")//"<p class=\"normal_text\">")
        if (curElement is PsiComment && curElement.tokenType == ArendElementTypes.LINE_DOC_TEXT) {
            html(curElement.text)
        } else {
            while (curElement is PsiComment && curElement.tokenType != ArendElementTypes.BLOCK_DOC_COMMENT_START) {
                curElement = curElement.prevSibling
            }

            while (curElement is PsiComment && curElement.tokenType != ArendElementTypes.BLOCK_COMMENT_END) {
                if (curElement.tokenType == ArendElementTypes.BLOCK_DOC_TEXT) {
                    html(curElement.text.substringBefore("\n\n"))
                    append(" ")
                }
                /*
                if (curElement.tokenType == ArendElementTypes.DOC_CODE_MLINE_BOUND) {
                    if (!isInsideMLineCode) {
                        append("<pre>")//"<p class=\"normal_text\">")
                    } else {
                        append("</pre>")
                    }
                    isInsideMLineCode = !isInsideMLineCode
                }
                curElement = if (curElement.tokenType == ArendElementTypes.DOC_LINK_START) {
                    val link = curElement.nextSibling as? PsiComment ?: break

                    if (link.tokenType == ArendElementTypes.BLOCK_DOC_TEXT) {
                        val refName = link.text
                        val target = originalElement?.parentOfType<ArendSourceNode>()?.scope?.resolveName(refName) as? PsiElement

                        if (target != null) {
                            DocumentationManagerUtil.createHyperlink(this, target, refName, refName, true)
                        }
                        // append("<a href=\"psi_element://${refName}\"><code>${refName}</code></a>")
                        val endBrace = link.nextSibling as? PsiComment ?: break
                        if (endBrace.tokenType != ArendElementTypes.DOC_LINK_END) break
                        endBrace.nextSibling
                    } else {
                        if (link.tokenType != ArendElementTypes.DOC_LINK_END) break
                        link.nextSibling
                    }
                } else {
                    curElement.nextSibling
                }*/
            }
        }
*/

    private fun StringBuilder.generateDocComments(element: PsiReferable) {
        val doc = getDocumentation(element) ?: return
        append(CONTENT_START)
        for (docElement in doc.children) {
            val elementType = (docElement as? LeafPsiElement)?.elementType
            when {
                elementType == DOC_LBRACKET -> append("[")
                elementType == DOC_RBRACKET -> append("]")
                elementType == DOC_TEXT -> html(docElement.text)
                elementType == WHITE_SPACE -> append(" ")
                elementType == DOC_CODE -> append("<code>${docElement.text}</code>")
                docElement is ArendDocReference -> {
                    val longName = docElement.longName
                    val link = longName.refIdentifierList.joinToString(".") { it.id.text }
                    val isLink = longName.resolve is PsiReferable
                    if (isLink) {
                        append("<a href=\"psi_element://$link\">")
                    }

                    append("<code>")
                    val text = docElement.docReferenceText
                    if (text != null) {
                        for (child in text.childrenWithLeaves) {
                            if (child.elementType == DOC_TEXT) append(child.text)
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
                            DOC_CODE_LINE -> append(child.text)
                            WHITE_SPACE -> append("\n")
                        }
                    }
                    append("</pre>")
                }
            }
        }
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
