package org.arend.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import org.arend.ext.module.LongName
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.naming.scope.Scope
import org.arend.parser.ParserMixin.*
import org.arend.psi.ArendFile
import org.arend.psi.ArendKeyword
import org.arend.psi.ArendKeyword.*
import org.arend.psi.ArendKeyword.Companion.isArendKeyword
import org.arend.psi.ArendKeyword.Companion.toArendKeyword
import org.arend.psi.childrenWithLeaves
import org.arend.psi.doc.ArendDocCodeBlock
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.doc.ArendDocReference
import org.arend.psi.ext.*
import org.arend.psi.prevElement
import org.arend.term.abs.Abstract
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.UnknownHostException


class ArendDocumentationProvider : AbstractDocumentationProvider() {

    private data class ArendKeywordHtmlSection(val id: String, val index: Int)

    private inner class ArendKeywordHtml(val chapter: String, val folder: String?) {
        var isUnknownHostException = false
        private fun initParagraphs(doc: Document?) = doc?.select("section")?.first()?.children() ?: emptyList()

        private fun initSections(paragraphs: List<Element>) = paragraphs.mapIndexedNotNull { index: Int, element: Element? ->
            if (element?.id().isNullOrEmpty()) {
                null
            } else {
                ArendKeywordHtmlSection(element?.id()!!, index)
            }
        }

        var paragraphs = initParagraphs(
            try {
                Jsoup.connect(AREND_DOCUMENTATION_BASE_PATH + chapter + (folder ?: "")).get()
            } catch (e: UnknownHostException) {
                isUnknownHostException = true
                null
            } catch (e: Throwable) {
                null
            }
        )
        var sections = initSections(paragraphs)

        fun updateConnection() {
            try {
                Jsoup.connect(AREND_DOCUMENTATION_BASE_PATH + chapter + (folder ?: "")).get()
            } catch (e: Throwable) {
                null
            }?.let {
                isUnknownHostException = false
                paragraphs = initParagraphs(it)
                sections = initSections(paragraphs)
            }

        }
    }

    private val functionHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "functions")
    private val moduleHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "modules")
    private val dataHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "data")
    private val typesHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "types")
    private val classesHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "classes")
    private val recordsHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "records")
    private val metasHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "metas")
    private val parametersHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "parameters")
    private val definitionsHtml = ArendKeywordHtml(DEFINITION_CHAPTER, null)
    private val coercionHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "coercion")
    private val levelHtml = ArendKeywordHtml(DEFINITION_CHAPTER, "level")
    private val universesHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "universes")
    private val classExtHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "class-ext")
    private val piHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "pi")
    private val sigmaHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "sigma")
    private val letHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "let")
    private val caseHtml = ArendKeywordHtml(EXPRESSION_CHAPTER, "case")

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, false)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, true)

    override fun getDocumentationElementForLink(psiManager: PsiManager?, link: String, context: PsiElement?): PsiElement? {
        val longName = link.removePrefix(FULL_PREFIX)
        val scope = ArendDocComment.getScope((context as? ArendDocComment)?.owner ?: context) ?: return null
        val ref = RedirectingReferable.getOriginalReferable(Scope.resolveName(scope, LongName.fromString(longName).toList()))
        return if (ref is PsiReferable && longName.length != link.length) ref.documentation else ref as? PsiElement
    }

    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?): PsiElement? {
        if (contextElement?.isArendKeyword() == true) {
            return contextElement
        }
        return null
    }

    private fun getStartAndFinishSection(html: ArendKeywordHtml?, arendKeyword: ArendKeyword?): List<Element> {
        val sectionName = arendKeyword?.section?.sectionName ?: return emptyList()
        val paragraphs = html?.paragraphs ?: return emptyList()
        val sections = html.sections

        val indexOfLemmaSection = sections.indexOf(sections.find { it.id == sectionName })
        val sectionStart = sections[indexOfLemmaSection].index + 1
        val sectionFinish = if (indexOfLemmaSection == sections.lastIndex) {
            paragraphs.size
        } else {
            sections[indexOfLemmaSection + 1].index
        }
        return paragraphs.subList(sectionStart, sectionFinish)
    }

    private fun getArendKeywordHtml(arendKeyword: ArendKeyword?) =
        when (arendKeyword) {
            OPEN, HIDING, AS, USING, IMPORT, MODULE, WHERE -> moduleHtml
            TRUNCATED, DATA, CONS -> dataHtml
            FUNC, LEMMA, AXIOM, SFUNC, EVAL, PEVAL, WITH, ELIM, COWITH -> functionHtml
            TYPE -> typesHtml
            CLASS, CLASSIFYING, NO_CLASSIFYING, INSTANCE -> classesHtml
            RECORD, FIELD, PROPERTY, OVERRIDE, DEFAULT, EXTENDS, THIS -> recordsHtml
            META -> metasHtml
            STRICT -> parametersHtml
            ALIAS, INFIX, INFIX_LEFT, INFIX_RIGHT, FIX, FIX_LEFT, FIX_RIGHT -> definitionsHtml
            USE, COERCE -> coercionHtml
            LEVEL -> levelHtml
            LEVELS, PLEVELS, HLEVELS, LP, LH, SUC, MAX, OO, PROP, SET, UNIVERSE, TRUNCATED_UNIVERSE -> universesHtml
            NEW -> classExtHtml
            PI, LAM -> piHtml
            SIGMA -> sigmaHtml
            LET, LETS, HAVE, HAVES, IN -> letHtml
            CASE, SCASE, RETURN -> caseHtml
            BOX, PRIVATE, PROTECTED, null -> null
        }

    private fun StringBuilder.addLink(arendKeywordHtml: ArendKeywordHtml?, arendKeyword: ArendKeyword?) {
        append("See the documentation: <a href=\"${AREND_DOCUMENTATION_BASE_PATH + (arendKeywordHtml?.chapter ?: "") + (arendKeywordHtml?.folder ?: "") 
                + (arendKeyword?.section?.sectionName?.let { "#$it" } ?: "")}\">${arendKeyword?.type?.debugName}</a>")
    }

    private fun StringBuilder.getDescriptionForKeyword(psiElement: PsiElement) {
        val arendKeyword = psiElement.toArendKeyword()
        val arendKeywordHtml = getArendKeywordHtml(arendKeyword)
        val paragraphs = arendKeywordHtml?.paragraphs
        val sections = arendKeywordHtml?.sections

        val sectionElements = if (arendKeywordHtml?.isUnknownHostException == true) {
            emptyList()
        } else {
            when (arendKeyword) {
                DATA, TYPE, CLASS, RECORD, META, FIELD, USE, COERCE, PI, SIGMA, LAM, LET, IN, CASE, RETURN, PROP, SET, UNIVERSE, TRUNCATED_UNIVERSE ->
                    paragraphs?.subList(AREND_SECTION_START, sections?.firstOrNull()?.index ?: paragraphs.size)
                FUNC -> paragraphs?.subList(AREND_SECTION_START, sections?.firstOrNull()?.index?.minus(1) ?: paragraphs.size)
                LEVEL, NEW -> paragraphs?.subList(AREND_SECTION_START, paragraphs.size)

                else -> getStartAndFinishSection(arendKeywordHtml, arendKeyword)
            } ?: emptyList()
        }

        for (element in sectionElements) {
            processElement(psiElement.project, element, arendKeywordHtml?.chapter, arendKeywordHtml?.folder)
        }
        if (arendKeywordHtml?.isUnknownHostException == true) {
            append("There is no internet connection to get the documentation")
            arendKeywordHtml.updateConnection()
        }
        when (arendKeyword) {
            BOX, PRIVATE, PROTECTED, null -> return
            else -> {
                wrapTag("hr") {
                    addLink(arendKeywordHtml, arendKeyword)
                }
            }
        }
    }

    private fun generateDocForKeywords(element: PsiElement): String {
        return buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                }
            }
            wrapTag("body") {
                wrap(DEFINITION_START, DEFINITION_END) {
                    wrapTag("b") {
                        html(element.text)
                    }
                }

                wrap(CONTENT_START, CONTENT_END) {
                    getDescriptionForKeyword(element)
                }
            }
        } }
    }

    private fun generateDoc(element: PsiElement, originalElement: PsiElement?, withDocComments: Boolean): String? {
        File("latex-images").deleteRecursively()

        val ref = element as? PsiReferable ?: (element as? ArendDocComment)?.owner
        ?: return if (element.isArendKeyword()) generateDocForKeywords(element) else null
        return buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                }
                wrapTag("style") {
                    append(".center {text-align: center;}")
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
                elementType == DOC_LATEX_CODE -> append(getHtmlLatexCode("image${counterLatexImages++}",
                    docElement.text.htmlEscape(),
                    docElement.prevElement.elementType == DOC_NEWLINE_LATEX_CODE,
                    element.project,
                    docElement.textOffset)
                )
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

    private fun StringBuilder.generateDefinition(element: PsiReferable) {
        wrapTag("b") {
            html(element.textRepresentation())
        }
        (element as? ReferableBase<*>)?.alias?.aliasIdentifier?.id?.text?.let {
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

        (element as? ReferableBase<*>)?.prec?.let { generatePrecedence(it) }

        (element as? ReferableBase<*>)?.alias?.let { alias ->
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
        is ArendDefIdentifier -> if (element.parent is ArendLetClause) "let" else "variable"
        is ArendLevelIdentifier -> "level"
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
}
