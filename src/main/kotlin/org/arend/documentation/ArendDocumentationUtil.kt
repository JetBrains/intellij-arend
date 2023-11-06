package org.arend.documentation

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.xml.util.XmlStringUtil
import org.arend.ArendLanguage
import org.jsoup.nodes.Element

const val FULL_PREFIX = "\\full:"
const val AREND_SECTION_START = 0
const val HIGHLIGHTER_CLASS = "highlighter-rouge"
const val AREND_DOCUMENTATION_BASE_PATH = "https://arend-lang.github.io/documentation/language-reference/"
const val DEFINITION_CHAPTER = "definitions/"
const val EXPRESSION_CHAPTER = "expressions/"


internal val REGEX_SPAN = "<span class=\"(o|k|n|g|kt|u)\">([^\"<]+)</span>".toRegex()
internal val REGEX_SPAN_HIGHLIGHT = "<span class=\"inl-highlight\">([^\"]+)</span>".toRegex()
internal val REGEX_CODE = "<code class=\"language-plaintext highlighter-rouge\">([^\"]+)</code>".toRegex()
internal val REGEX_HREF = "<a href=\"([^\"]+)\">([^\"]+)</a>".toRegex()

internal fun String.htmlEscape(): String = XmlStringUtil.escapeString(this, true)

internal fun String.wrapTag(tag: String) = "<$tag>$this</$tag>"

internal fun StringBuilder.html(text: String) = append(text.htmlEscape())

internal inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
    this.append(prefix)
    body()
    this.append(postfix)
}

internal inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
    wrap("<$tag>", "</$tag>", body)
}

fun StringBuilder.appendNewLineHtml() = append("<br>")

internal fun StringBuilder.processElement(project: Project, element: Element, chapter: String?, folder: String?) {
    if (element.className() == HIGHLIGHTER_CLASS) {
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            this,
            project,
            ArendLanguage.INSTANCE,
            element.text(),
            DocumentationSettings.getHighlightingSaturation(false)
        )
        appendNewLineHtml()
    } else {
        appendLine(changeTags(element.toString(), chapter, folder, isAside(element.tagName())))
    }
    appendNewLineHtml()
}

private fun isAside(tag: String) = tag == "aside"

internal fun changeTags(line: String, chapter: String?, folder: String?, isAside: Boolean): String {
    return line.replace(REGEX_SPAN) {
        it.groupValues.getOrNull(2)?.wrapTag("b") ?: ""
    }.replace(REGEX_SPAN_HIGHLIGHT) {
        it.groupValues.getOrNull(1)?.wrapTag("b") ?: ""
    }.replace(REGEX_CODE){
        it.groupValues.getOrNull(1)?.wrapTag("b") ?: ""
    }.replace(REGEX_HREF) {
        "<a href=\"${AREND_DOCUMENTATION_BASE_PATH + chapter + (if (isAside) folder else "") + it.groupValues.getOrNull(1)}\">${it.groupValues.getOrNull(2)}</a>"
    }
}
