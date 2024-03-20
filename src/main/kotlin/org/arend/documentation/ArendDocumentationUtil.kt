package org.arend.documentation

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.xml.util.XmlStringUtil
import org.arend.ArendLanguage
import org.jsoup.nodes.Element

internal val LOG: Logger = Logger.getInstance("#org.arend.documentation")

const val FULL_PREFIX = "\\full:"
const val ACTION_PREFIX = "\\action:"
const val AREND_SECTION_START = 0
const val AREND_DOCUMENTATION_BASE_PATH = "https://arend-lang.github.io/documentation/language-reference/"
const val DEFINITION_CHAPTER = "definitions/"
const val EXPRESSION_CHAPTER = "expressions/"

const val AREND_CSS = "Arend.css"
const val AREND_JS = "highlight-hover.js"
const val AREND_DIR_HTML = "arend-html-files/"
const val AREND_BASE_FILE = "Base.ard"

internal val REGEX_SPAN = "<span class=\"(o|k|n|g|kt|u)\">([^\"<]+)</span>".toRegex()
internal val REGEX_SPAN_HIGHLIGHT = "<span class=\"inl-highlight\">([^\"]+)</span>".toRegex()
internal val REGEX_CODE = "<code class=\"language-plaintext highlighter-rouge\">([^\"]+)</code>".toRegex()
internal val REGEX_HREF = "<a href=\"([^\"]+)\">([^\"]+)</a>".toRegex()
internal val REGEX_AREND_LIB_VERSION = "\\* \\[(.+)]".toRegex()
internal val REGEX_AREND_DOC_NEW_LINE = "\n \t*(- )?".toRegex()

const val AREND_DOC_COMMENT_TABS_SIZE = 2

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

fun StringBuilder.appendNewLineHtml(): StringBuilder = append("<br>")

internal fun StringBuilder.processElement(project: Project, element: Element, chapter: String?, folder: String?) {
    if (element.className() == "highlighter-rouge") {
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

private fun changeTags(line: String, chapter: String?, folder: String?, isAside: Boolean): String {
    return line.replace(REGEX_SPAN) {
        it.groupValues.getOrNull(2)?.wrapTag("b") ?: ""
    }.replace(REGEX_SPAN_HIGHLIGHT) {
        it.groupValues.getOrNull(1)?.wrapTag("b") ?: ""
    }.replace(REGEX_CODE) {
        it.groupValues.getOrNull(1)?.wrapTag("b") ?: ""
    }.replace(REGEX_HREF) {
        "<a href=\"${
            AREND_DOCUMENTATION_BASE_PATH + chapter + (if (isAside) folder else "") + it.groupValues.getOrNull(
                1
            )
        }\">${it.groupValues.getOrNull(2)}</a>"
    }
}

internal fun getHtmlRgbFormat(rgb: Int) = String.format("#%06x", rgb and 0xFFFFFF)
