package org.arend.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.arend.documentation.ArendDocumentationProvider.TypeListItem.Companion.LIST_ELEMENT_TYPES
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
import org.arend.psi.doc.ArendDocReferenceText
import org.arend.psi.ext.*
import org.arend.psi.prevElement
import org.arend.term.abs.Abstract
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.URL
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
        var curIndex = 0
        val docElements = doc.childrenWithLeaves.toList()
        val context = mutableListOf<IElementType>()

        while (curIndex <= docElements.lastIndex) {
            curIndex = processElement(curIndex, docElements, element, full, context)
        }
    }

    private fun StringBuilder.processElement(
        index: Int,
        docElements: List<PsiElement>,
        element: PsiReferable,
        full: Boolean,
        context: MutableList<IElementType>
    ): Int {
        val docElement = docElements[index]
        val elementType = docElement.elementType
        when {
            elementType == DOC_LBRACKET -> append("[")
            elementType == DOC_RBRACKET -> append("]")
            elementType == DOC_TEXT -> {
                val nextElement = docElements.getOrNull(index + 1)
                val nextElementType = nextElement.elementType
                if (docElements.getOrNull(index + 2).elementType == DOC_NEWLINE) {
                    if (nextElementType == DOC_HEADER_1) {
                        append("<h1>${docElement.text}</h1>")
                        return index + 2
                    } else if (nextElementType == DOC_HEADER_2) {
                        append("<h2>${docElement.text}</h2>")
                        return index + 2
                    }
                }
                html(docElement.text)
            }
            elementType == WHITE_SPACE || elementType == DOC_NEWLINE || elementType == DOC_TABS -> append(" ")
            elementType == DOC_CODE -> append("<code>${docElement.text.htmlEscape()}</code>")
            elementType == DOC_LATEX_CODE -> append(getHtmlLatexCode("image${counterLatexImages++}",
                docElement.text.htmlEscape(),
                docElement.prevElement.elementType == DOC_NEWLINE_LATEX_CODE,
                element.project,
                docElement.textOffset)
            )
            elementType == DOC_UNORDERED_LIST -> {
                return appendListItems(TypeListItem.UNORDERED, index, docElements, element, full, context)
            }
            elementType == DOC_ORDERED_LIST -> {
                return appendListItems(TypeListItem.ORDERED, index, docElements, element, full, context)
            }
            elementType == DOC_BLOCKQUOTES -> {
                return appendBlockQuotes(index, docElements, element, full, context)
            }
            elementType == DOC_ITALICS_CODE -> append("<i>${docElement.text}</i>")
            elementType == DOC_BOLD_CODE -> append("<b>${docElement.text}</b>")
            elementType == DOC_LINEBREAK -> append("<br/>")
            elementType == DOC_PARAGRAPH_SEP -> {
                append("<br>")
                if (!full) {
                    append("<a href=\"psi_element://$FULL_PREFIX${element.refName}\">more...</a>")
                    return docElements.lastIndex + 1
                }
            }
            docElement is ArendDocReference -> {
                val url = docElement.longName.text
                if (isValidUrl(url)) {
                    append("<a href=\"$url\">${docElement.docReferenceText?.let { getLinkText(it).joinToString() }}</a>")
                    return index + 1
                }
                val longName = docElement.longName
                val link = longName.refIdentifierList.joinToString(".") { it.id.text.htmlEscape() }
                val isLink = longName.resolve is PsiReferable
                if (isLink) {
                    append("<a href=\"psi_element://$link\">")
                }

                append("<code>")
                val text = docElement.docReferenceText
                if (text != null) {
                    getLinkText(text).forEach { html(it) }
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
                    when (child.elementType) {
                        DOC_CODE_LINE -> html(child.text)
                        WHITE_SPACE -> append("\n")
                    }
                }
                append("</pre>")
            }
        }
        return index + 1
    }

    private fun isValidUrl(urlString: String): Boolean {
        return try {
            URL(urlString)
            true
        } catch (ex: Exception) {
            false
        }
    }

    private fun getLinkText(text: ArendDocReferenceText): Sequence<String> {
        return text.childrenWithLeaves.filter { it.elementType == DOC_TEXT }.map { it.text }
    }

    enum class TypeListItem(val elementType: IElementType) {
        ORDERED(DOC_ORDERED_LIST),
        UNORDERED(DOC_UNORDERED_LIST);

        companion object {
            val LIST_ELEMENT_TYPES = entries.associateBy { it.elementType }
            val LIST_UNORDERED_ITEM_SYMBOLS = listOf("* ", "- ", "+ ")
            val LIST_ORDERED_REGEX = "[0-9]+.".toRegex()
        }
    }

    private fun StringBuilder.appendListItems(
        typeListItem: TypeListItem,
        index: Int,
        docElements: List<PsiElement>,
        element: PsiReferable,
        full: Boolean,
        context: MutableList<IElementType>
    ): Int {
        context.add(docElements[index].elementType!!)
        if (typeListItem == TypeListItem.UNORDERED) {
            append("<ul>")
        } else {
            append("<ol>")
        }
        append("<li>")

        val newIndex = processBlock(index + 1, docElements, element, full, context, "</li><li>")

        append("</li>")
        if (typeListItem == TypeListItem.UNORDERED) {
            append("</ul>")
        } else {
            append("</ol>")
        }
        return newIndex
    }

    private fun StringBuilder.appendBlockQuotes(index: Int, docElements: List<PsiElement>, element: PsiReferable, full: Boolean, context: MutableList<IElementType>): Int {
        context.add(docElements[index].elementType!!)
        append("<blockquote><p>")

        val newIndex = processBlock(index + 1, docElements, element, full, context, " ")

        append("</p></blockquote>")
        return newIndex
    }

    private fun StringBuilder.processBlock(
        index: Int,
        docElements: List<PsiElement>,
        element: PsiReferable,
        full: Boolean,
        context: MutableList<IElementType>,
        itemHtml: String
    ): Int {
        var curIndex = index
        while (curIndex <= docElements.lastIndex) {
            val curElementType = docElements[curIndex].elementType
            if (curElementType == DOC_PARAGRAPH_SEP) {
                break
            } else if (curElementType == DOC_NEWLINE) {
                val resultContext = checkContext(curIndex, docElements, context)
                if (resultContext.first) {
                    curIndex += resultContext.second
                    append(itemHtml)
                } else if (isNestedList(curIndex, docElements, context)) {
                    append("</li>")
                    val listElementType = docElements[curIndex + 2].elementType
                    curIndex = appendListItems(
                        if (listElementType == DOC_ORDERED_LIST) TypeListItem.ORDERED else TypeListItem.UNORDERED,
                        curIndex + 2,
                        docElements,
                        element,
                        full,
                        context
                    )
                    continue
                } else {
                    break
                }
            } else {
                curIndex = processElement(curIndex, docElements, element, full, context)
                continue
            }
            curIndex++
        }
        context.removeLast()
        return curIndex
    }

    private fun checkContext(elementIndex: Int, docElements: List<PsiElement>, context: List<IElementType>): Pair<Boolean, Int> {
        var contextIndex = 0
        var shiftDocElements = 1
        while (contextIndex <= context.lastIndex) {
            val contextElement = context[contextIndex]
            val element = docElements.getOrNull(elementIndex + shiftDocElements)
            val elementType = element?.elementType

            if (elementType == DOC_TABS) {
                var numberTabs = (element!!.text.length + DOC_TABS_SIZE - 1) / DOC_TABS_SIZE
                while (numberTabs > 0 && contextIndex < context.lastIndex) {
                    if (!LIST_ELEMENT_TYPES.contains(context[contextIndex])) {
                        return Pair(false, -1)
                    }
                    contextIndex++
                    numberTabs--
                }
                shiftDocElements++
                if (numberTabs == 0) {
                    continue
                }
            }
            if (elementType != contextElement) {
                return Pair(false, -1)
            }
            contextIndex++
            shiftDocElements++
        }
        return Pair(true, shiftDocElements - 1)
    }

    private fun isNestedList(elementIndex: Int, docElements: List<PsiElement>, context: List<IElementType>): Boolean {
        var firstNotEqualContextIndex = -1
        for (contextElement in context.withIndex()) {
            val elementType = docElements.getOrNull(elementIndex + contextElement.index + 1)?.elementType
            if (elementType != contextElement.value) {
                firstNotEqualContextIndex = contextElement.index
                break
            }
        }

        val whiteSpaceItem = docElements.getOrNull(elementIndex + 1)
        if (whiteSpaceItem.elementType != DOC_TABS) {
            return false
        }
        val numberTabs = (whiteSpaceItem!!.text.length + DOC_TABS_SIZE - 1) / DOC_TABS_SIZE
        if (numberTabs != context.lastIndex - firstNotEqualContextIndex + 1) {
            return false
        }
        for (index in firstNotEqualContextIndex..context.lastIndex) {
            if (!LIST_ELEMENT_TYPES.contains(context[index])) {
                return false
            }
        }

        val listItemType = docElements.getOrNull(elementIndex + 2).elementType
        return LIST_ELEMENT_TYPES.contains(listItemType)
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
